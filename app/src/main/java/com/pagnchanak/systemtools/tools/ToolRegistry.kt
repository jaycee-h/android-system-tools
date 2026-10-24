package com.pagnchanak.systemtools.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import com.pagnchanak.systemtools.tools.shell.ShellScreen

/** Every tool shown on the home screen, in display order. */
object ToolRegistry {
    val tools: List<Tool> = listOf(
        Tool(
            id = "shell",
            title = "Root Shell",
            description = "Run commands as root and view the output",
            icon = Icons.Filled.Terminal,
            content = { ShellScreen() },
        ),
    )

    fun find(id: String): Tool? = tools.firstOrNull { it.id == id }
}
