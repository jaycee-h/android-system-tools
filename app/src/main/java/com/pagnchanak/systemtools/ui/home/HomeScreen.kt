package com.pagnchanak.systemtools.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagnchanak.systemtools.root.RootStatus
import com.pagnchanak.systemtools.tools.Tool
import com.pagnchanak.systemtools.tools.ToolRegistry
import com.pagnchanak.systemtools.ui.components.GroupGap
import com.pagnchanak.systemtools.ui.components.GroupedListItem
import com.pagnchanak.systemtools.ui.components.SectionHeader
import com.pagnchanak.systemtools.ui.components.ShapedIcon
import com.pagnchanak.systemtools.ui.components.ToolScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val toolShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Arch,
)

@Composable
fun HomeScreen(
    onOpenTool: (Tool) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val hasRoot = rootStatus is RootStatus.Granted
    val tools = ToolRegistry.tools

    ToolScaffold(
        title = "System Tools",
        onBack = null,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings and updates")
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item { RootStatusCard(rootStatus, onRefresh = viewModel::refresh) }
            item { SectionHeader("Tools") }
            itemsIndexed(tools, key = { _, tool -> tool.id }) { index, tool ->
                val enabled = hasRoot || !tool.requiresRoot
                GroupedListItem(
                    index = index,
                    count = tools.size,
                    headline = { Text(tool.title) },
                    supporting = { Text(tool.description) },
                    leading = { ToolIcon(tool, index, enabled) },
                    onClick = { onOpenTool(tool) },
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolIcon(tool: Tool, index: Int, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        !enabled -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        index % 3 == 0 -> colors.primaryContainer to colors.onPrimaryContainer
        index % 3 == 1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    ShapedIcon(
        tool.icon,
        shape = toolShapes[index % toolShapes.size].toShape(),
        containerColor = container,
        contentColor = content,
        size = 44.dp,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RootStatusCard(status: RootStatus, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        is RootStatus.Granted -> colors.primaryContainer to colors.onPrimaryContainer
        RootStatus.Denied -> colors.errorContainer to colors.onErrorContainer
        RootStatus.Checking -> colors.surfaceContainerHigh to colors.onSurface
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            when (status) {
                RootStatus.Checking -> LoadingIndicator(Modifier.size(56.dp))
                RootStatus.Denied -> ShapedIcon(
                    Icons.Filled.Block,
                    shape = MaterialShapes.Ghostish.toShape(),
                    containerColor = colors.error,
                    contentColor = colors.onError,
                    size = 56.dp,
                )
                is RootStatus.Granted -> ShapedIcon(
                    Icons.Filled.VerifiedUser,
                    shape = MaterialShapes.SoftBurst.toShape(),
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    size = 56.dp,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        RootStatus.Checking -> "Checking root…"
                        RootStatus.Denied -> "No root access"
                        is RootStatus.Granted -> "Root granted"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                val detail = when (status) {
                    RootStatus.Denied -> "Grant root in your root manager, then refresh"
                    is RootStatus.Granted -> status.provider
                    RootStatus.Checking -> null
                }
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (status != RootStatus.Checking) {
                FilledTonalIconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }
}
