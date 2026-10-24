package com.pagnchanak.systemtools.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pagnchanak.systemtools.root.RootShell
import com.pagnchanak.systemtools.root.RootStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _rootStatus = MutableStateFlow<RootStatus>(RootStatus.Checking)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _rootStatus.value = RootStatus.Checking
        viewModelScope.launch { _rootStatus.value = RootShell.requestRoot() }
    }
}
