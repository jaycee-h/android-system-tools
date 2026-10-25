package com.pagnchanak.systemtools.tools.systemizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagnchanak.systemtools.apps.AppRepository
import com.pagnchanak.systemtools.apps.InstalledApp
import com.pagnchanak.systemtools.root.CommandResult
import com.pagnchanak.systemtools.root.RootShell
import com.pagnchanak.systemtools.tools.systemizer.Systemizer.Kind
import com.pagnchanak.systemtools.tools.systemizer.Systemizer.Module
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
    Desystemized("De-systemized"),
    RevertPending("Reverts after reboot", pending = true),
}

enum class AppFilter(val label: String) { User("User"), System("System"), Managed("Changed") }

data class SystemizerState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val modules: Map<String, Module> = emptyMap(),
    val filter: AppFilter = AppFilter.User,
    val query: String = "",
    val busy: Boolean = false,
) {
    fun status(app: InstalledApp): AppStatus {
        val module = modules[app.packageName] ?: return if (app.isSystem) AppStatus.System else AppStatus.User
        return when {
            module.pendingRemoval -> AppStatus.RevertPending
            module.kind == Kind.Systemize -> if (app.isSystem) AppStatus.Systemized else AppStatus.SystemizePending
            else -> if (app.isSystem) AppStatus.DesystemizePending else AppStatus.Desystemized
        }
    }

    val visibleApps: List<InstalledApp>
        get() = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.User -> !app.isSystem && app.packageName !in modules
                AppFilter.System -> app.isSystem && app.packageName !in modules
                AppFilter.Managed -> app.packageName in modules
            }
            matchesFilter && (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }

    val hasPendingChanges: Boolean get() = apps.any { it.packageName in modules && status(it).pending }
}

data class UiMessage(val text: String, val offerReboot: Boolean = false)

class SystemizerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(SystemizerState())
    val state: StateFlow<SystemizerState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val apps = AppRepository.load(getApplication()).filter { it.installedForMe }
        val modules = Systemizer.modules(apps.map { it.packageName })
        _state.update { it.copy(loading = false, apps = apps, modules = modules) }
    }

    fun setFilter(filter: AppFilter) = _state.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun systemize(app: InstalledApp, privileged: Boolean) = act("${app.label} will be systemized after a reboot") {
        Systemizer.systemize(app, privileged, getApplication<Application>().packageManager)
    }

    fun desystemize(app: InstalledApp) = act("${app.label} will be de-systemized after a reboot") {
        Systemizer.desystemize(app)
    }

    fun revert(app: InstalledApp) {
        val state = _state.value
        val module = state.modules[app.packageName] ?: return
        val applied = state.status(app) in setOf(AppStatus.Systemized, AppStatus.Desystemized)
        val message = if (applied) "${app.label} will be restored after a reboot" else "Pending change cancelled"
        act(message, offerReboot = applied) { Systemizer.revert(module, applied) }
    }

    fun cancelRevert(app: InstalledApp) {
        val module = _state.value.modules[app.packageName] ?: return
        act("Kept ${app.label} as is", offerReboot = false) { Systemizer.cancelRevert(module) }
    }

    fun reboot() = viewModelScope.launch { RootShell.reboot() }

    private fun act(success: String, offerReboot: Boolean = true, block: suspend () -> CommandResult) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = block()
            refresh().join()
            _state.update { it.copy(busy = false) }
            _messages.send(
                if (result.isSuccess) UiMessage(success, offerReboot)
                else UiMessage("Failed: ${result.output.ifBlank { "exit ${result.code}" }}"),
            )
        }
    }
}
