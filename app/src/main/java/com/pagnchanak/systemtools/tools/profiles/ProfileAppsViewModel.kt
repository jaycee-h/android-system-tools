package com.pagnchanak.systemtools.tools.profiles

import android.app.Application
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pagnchanak.systemtools.apps.AppRepository
import com.pagnchanak.systemtools.apps.InstalledApp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileFilter(val label: String) { All("All"), Enabled("In profile"), Disabled("Not in profile") }

data class ProfileAppsState(
    val loading: Boolean = true,
    val profiles: List<UserProfile> = emptyList(),
    val selectedProfileId: Int? = null,
    val apps: List<InstalledApp> = emptyList(),
    val installedInProfile: Set<String> = emptySet(),
    val filter: ProfileFilter = ProfileFilter.All,
    val showSystem: Boolean = false,
    val query: String = "",
    /** Packages with an install/uninstall in flight. */
    val busy: Set<String> = emptySet(),
) {
    val selectedProfile: UserProfile? get() = profiles.firstOrNull { it.id == selectedProfileId }

    val visibleApps: List<InstalledApp>
        get() = apps.filter { app ->
            val inProfile = app.packageName in installedInProfile
            (showSystem || !app.isSystem) &&
                when (filter) {
                    ProfileFilter.All -> true
                    ProfileFilter.Enabled -> inProfile
                    ProfileFilter.Disabled -> !inProfile
                } &&
                (query.isBlank() || app.label.contains(query, true) || app.packageName.contains(query, true))
        }
}

class ProfileAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ProfileAppsState())
    val state: StateFlow<ProfileAppsState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            // UserHandle.hashCode() is the user id.
            val profiles = Profiles.list(Process.myUserHandle().hashCode())
            val selected = (profiles.firstOrNull { it.isWorkProfile } ?: profiles.firstOrNull())?.id
            val apps = AppRepository.load(getApplication())
            val installed = selected?.let { Profiles.installedPackages(it) }.orEmpty()
            _state.update {
                it.copy(loading = false, profiles = profiles, selectedProfileId = selected, apps = apps, installedInProfile = installed)
            }
        }
    }

    fun selectProfile(id: Int) {
        _state.update { it.copy(selectedProfileId = id) }
        viewModelScope.launch {
            val installed = Profiles.installedPackages(id)
            _state.update { if (it.selectedProfileId == id) it.copy(installedInProfile = installed) else it }
        }
    }

    fun setFilter(filter: ProfileFilter) = _state.update { it.copy(filter = filter) }

    fun setShowSystem(show: Boolean) = _state.update { it.copy(showSystem = show) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setEnabled(app: InstalledApp, enabled: Boolean) {
        val profile = _state.value.selectedProfile ?: return
        if (app.packageName in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + app.packageName) }
        viewModelScope.launch {
            val result = if (enabled) Profiles.install(profile.id, app.packageName) else Profiles.uninstall(profile.id, app.packageName)
            val installed = Profiles.installedPackages(profile.id)
            _state.update {
                it.copy(
                    busy = it.busy - app.packageName,
                    installedInProfile = if (it.selectedProfileId == profile.id) installed else it.installedInProfile,
                )
            }
            _messages.send(
                when {
                    !result.isSuccess -> "Failed: ${result.output.ifBlank { "exit ${result.code}" }}"
                    enabled -> "${app.label} added to ${profile.name}"
                    else -> "${app.label} removed from ${profile.name}"
                },
            )
        }
    }
}
