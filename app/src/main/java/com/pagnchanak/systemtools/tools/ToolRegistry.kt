package com.pagnchanak.systemtools.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Work
import com.pagnchanak.systemtools.tools.profiles.ProfileAppsScreen
import com.pagnchanak.systemtools.tools.shell.ShellScreen
import com.pagnchanak.systemtools.tools.systemizer.SystemizerScreen

/** Every tool shown on the home screen, in display order. */
object ToolRegistry {
    val tools: List<Tool> = listOf(
        Tool(
            id = "systemizer",
            title = "App Systemizer",
            description = "Turn user apps into system apps and back",
            icon = Icons.Filled.InstallMobile,
            content = { SystemizerScreen(onBack = it) },
        ),
        Tool(
            id = "profiles",
            title = "Work Profile Apps",
            description = "Add installed apps to your work profile",
            icon = Icons.Filled.Work,
            content = { ProfileAppsScreen(onBack = it) },
        ),
        Tool(
            id = "shell",
            title = "Root Shell",
            description = "Run commands as root and view the output",
            icon = Icons.Filled.Terminal,
            content = { ShellScreen(onBack = it) },
        ),
    )

    fun find(id: String): Tool? = tools.firstOrNull { it.id == id }
}
