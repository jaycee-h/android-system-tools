package com.pagnchanak.systemtools.tools.systemizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagnchanak.systemtools.apps.AppRepository
import com.pagnchanak.systemtools.apps.InstalledApp
import com.pagnchanak.systemtools.root.CommandResult
import com.pagnchanak.systemtools.root.RootShell
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppStatus(val label: String, val pending: Boolean = false) {
    User("User app"),
    System("System app"),
    SystemizePending("Systemizes after reboot", pending = true),
    Systemized("Systemized"),
    DesystemizePending("De-systemizes after reboot", pending = true),
    Desystemized("De-systemized, kept as user app"),
    RemovePending("Removed after reboot", pending = true),
    Removed("Removed"),
    RevertPending("Reverts after reboot", pending = true),
}

enum class AppFilter(val label: String) { User("User"), System("System"), Managed("Changed") }

/** A list row: an installed app, or a package that is only known from the module (e.g. removed). */
data class AppRow(val packageName: String, val label: String, val app: InstalledApp?)

data class SystemizerState(
    val loading: Boolean = true,
    val rows: List<AppRow> = emptyList(),
    val module: ModuleState = ModuleState(),
    val filter: AppFilter = AppFilter.User,
    val query: String = "",
    val busy: Boolean = false,
) {
    fun status(row: AppRow): AppStatus {
        val app = row.app
        val isSystem = app?.isSystem == true
        return when (val entry = module.desired[row.packageName]) {
            is Entry.Systemize -> if (isSystem) AppStatus.Systemized else AppStatus.SystemizePending
            is Entry.Desystemize -> when {
                isSystem -> if (entry.keepUserApp) AppStatus.DesystemizePending else AppStatus.RemovePending
                entry.keepUserApp -> AppStatus.Desystemized
                app == null -> AppStatus.Removed
                else -> AppStatus.RemovePending // hidden, user copy is uninstalled shortly after boot
            }
            null -> when (module.mounted[row.packageName]) {
                is Entry.Systemize -> if (isSystem) AppStatus.RevertPending else AppStatus.User
                is Entry.Desystemize -> if (!isSystem) AppStatus.RevertPending else AppStatus.System
                null -> if (isSystem) AppStatus.System else AppStatus.User
            }
        }
    }

    val visibleRows: List<AppRow>
        get() = rows.filter { row ->
            val status = status(row)
            val matchesFilter = when (filter) {
                AppFilter.User -> status == AppStatus.User
                AppFilter.System -> status == AppStatus.System
                AppFilter.Managed -> status != AppStatus.User && status != AppStatus.System
            }
            matchesFilter && (query.isBlank() || row.label.contains(query, true) || row.packageName.contains(query, true))
        }

    val hasPendingChanges: Boolean get() = rows.any { status(it).pending }
}

data class UiMessage(val text: String, val offerReboot: Boolean = false)

class SystemizerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SystemizerState())
    val state: StateFlow<SystemizerState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var apps: Map<String, InstalledApp> = emptyMap()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        apps = AppRepository.load(getApplication()).filter { it.installedForMe }.associateBy { it.packageName }
        val module = Systemizer.state()
        // Packages the module knows about but that aren't installed (removed apps) still get a row.
        val orphans = (module.desired.keys + module.mounted.keys - apps.keys)
            .map { AppRow(it, it, null) }
        val rows = (apps.values.map { AppRow(it.packageName, it.label, it) } + orphans)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        _state.update { it.copy(loading = false, rows = rows, module = module) }
    }

    fun setFilter(filter: AppFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun systemize(row: AppRow, privileged: Boolean) = change(row, "${row.label} will be systemized after a reboot") { desired ->
        desired + (row.packageName to Entry.Systemize(row.packageName, privileged))
    }

    fun desystemize(row: AppRow, keepUserApp: Boolean) {
        val app = row.app ?: return
        val message = if (keepUserApp) "${row.label} will be de-systemized after a reboot" else "${row.label} will be removed after a reboot"
        change(row, message) { desired ->
            val codePath = Systemizer.systemCodePath(app.packageName)
                ?: throw ChangeFailed("Could not find the system copy of ${app.packageName}")
            if (keepUserApp && !app.isUpdatedSystem) {
                val install = Systemizer.installUserCopy(codePath)
                if (!install.isSuccess) throw ChangeFailed("Couldn't install a user copy first, so nothing was changed.\n${install.output}")
            }
            desired + (app.packageName to Entry.Desystemize(app.packageName, keepUserApp, codePath))
        }
    }

    /** Undoes a change: cancels it if still pending, otherwise reverts it on the next reboot. */
    fun revert(row: AppRow) = change(row, "${row.label} will be restored after a reboot") { desired ->
        desired - row.packageName
    }

    /** Cancels a pending revert, keeping the currently applied change. */
    fun keep(row: AppRow) {
        val entry = _state.value.module.mounted[row.packageName] ?: return
        change(row, "Kept ${row.label} as is") { desired -> desired + (row.packageName to entry) }
    }

    fun reboot() = viewModelScope.launch { RootShell.reboot() }

    private class ChangeFailed(message: String) : Exception(message)

    /** Applies a new desired state for [row], built by [transform] from the current one. */
    private fun change(row: AppRow, success: String, transform: suspend (Map<String, Entry>) -> Map<String, Entry>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            // Re-read the module so the change builds on what's really on disk.
            val module = Systemizer.state()
            val result = try {
                val desired = transform(module.desired)
                Systemizer.apply(module, desired, apps, getApplication<Application>().packageManager)
            } catch (e: ChangeFailed) {
                CommandResult(1, emptyList(), listOf(e.message.orEmpty()))
            }
            refresh().join()
            val pending = _state.value.let { state ->
                state.rows.firstOrNull { it.packageName == row.packageName }?.let { state.status(it).pending } ?: false
            }
            _state.update { it.copy(busy = false) }
            _messages.send(
                when {
                    !result.isSuccess -> UiMessage("Failed: ${result.output.ifBlank { "exit ${result.code}" }}")
                    // The change brought the app back to what's already applied.
                    !pending -> UiMessage("Pending change for ${row.label} cancelled")
                    else -> UiMessage(success, offerReboot = true)
                },
            )
        }
    }
}
