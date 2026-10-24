package com.pagnchanak.systemtools.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single feature of the app. To add a new tool, implement its screen and
 * register it in [ToolRegistry].
 */
data class Tool(
    /** Stable id, used as the navigation route segment. */
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val requiresRoot: Boolean = true,
    val content: @Composable () -> Unit,
)
