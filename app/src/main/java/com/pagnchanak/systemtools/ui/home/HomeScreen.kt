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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTool: (Tool) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val hasRoot = rootStatus is RootStatus.Granted

    Scaffold(
        topBar = { TopAppBar(title = { Text("System Tools") }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item { RootStatusCard(rootStatus, onRefresh = viewModel::refresh) }
            items(ToolRegistry.tools, key = { it.id }) { tool ->
                ToolCard(
                    tool = tool,
                    enabled = hasRoot || !tool.requiresRoot,
                    onClick = { onOpenTool(tool) },
                )
            }
        }
    }
}

@Composable
private fun RootStatusCard(status: RootStatus, onRefresh: () -> Unit) {
    val colors = when (status) {
        is RootStatus.Granted -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        RootStatus.Denied -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        RootStatus.Checking -> CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            when (status) {
                RootStatus.Checking -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                RootStatus.Denied -> Icon(Icons.Filled.Block, contentDescription = null)
                is RootStatus.Granted -> Icon(Icons.Filled.VerifiedUser, contentDescription = null)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (status) {
                        RootStatus.Checking -> "Checking root access…"
                        RootStatus.Denied -> "Root not available"
                        is RootStatus.Granted -> "Root granted"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = when (status) {
                    RootStatus.Denied -> "Grant root in your root manager, then refresh"
                    is RootStatus.Granted -> status.provider
                    RootStatus.Checking -> null
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (status != RootStatus.Checking) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(tool.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(tool.title, style = MaterialTheme.typography.titleMedium)
                Text(tool.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
