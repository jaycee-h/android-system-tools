package com.pagnchanak.systemtools.tools.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pagnchanak.systemtools.root.RootShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShellEntry(val command: String, val output: List<String>, val code: Int)

data class ShellState(
    val history: List<ShellEntry> = emptyList(),
    val running: Boolean = false,
)

class ShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    fun run(command: String) {
        if (command.isBlank() || _state.value.running) return
        _state.update { it.copy(running = true) }
        viewModelScope.launch {
            val result = RootShell.run(command)
            val entry = ShellEntry(command, result.out + result.err, result.code)
            _state.update { it.copy(history = it.history + entry, running = false) }
        }
    }

    fun clear() = _state.update { it.copy(history = emptyList()) }
}
