package com.pagnchanak.systemtools.tools.systemizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagnchanak.systemtools.apps.AppIcon
import com.pagnchanak.systemtools.apps.InstalledApp
import com.pagnchanak.systemtools.ui.components.EmptyState
import com.pagnchanak.systemtools.ui.components.GroupGap
import com.pagnchanak.systemtools.ui.components.GroupedListItem
import com.pagnchanak.systemtools.ui.components.LoadingState
import com.pagnchanak.systemtools.ui.components.SearchField
import com.pagnchanak.systemtools.ui.components.ToolScaffold

@Composable
fun SystemizerScreen(onBack: () -> Unit, viewModel: SystemizerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReboot by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message = message.text,
                actionLabel = if (message.offerReboot) "Reboot" else null,
                duration = if (message.offerReboot) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.reboot()
        }
    }

    ToolScaffold(
        title = "App Systemizer",
        subtitle = "Changes apply after a reboot",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (state.hasPendingChanges) {
                IconButton(onClick = { confirmReboot = true }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reboot")
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@ToolScaffold
        }
        val rows = state.visibleRows
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item {
                SearchField(state.query, viewModel::setQuery, "Search apps")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(AppFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                            leadingIcon = if (state.filter == filter) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (state.filter == AppFilter.Managed) Icons.Filled.Apps else Icons.Filled.SearchOff,
                        title = if (state.filter == AppFilter.Managed) "Nothing changed yet" else "No apps found",
                        message = if (state.filter == AppFilter.Managed) "Apps you systemize or de-systemize show up here." else "Try another search or filter.",
                    )
                }
            }
            itemsIndexed(rows, key = { _, row -> row.packageName }) { index, row ->
                val status = state.status(row)
                GroupedListItem(
                    index = index,
                    count = rows.size,
                    headline = { Text(row.label) },
                    supporting = {
                        Text(
                            if (state.filter == AppFilter.Managed) status.label else row.packageName,
                            color = if (status.pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leading = { RowIcon(row) },
                    onClick = { selected = row.packageName },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    state.rows.firstOrNull { it.packageName == selected }?.let { row ->
        AppSheet(
            row = row,
            status = state.status(row),
            busy = state.busy,
            onDismiss = { selected = null },
            onSystemize = { privileged -> viewModel.systemize(row, privileged) },
            onDesystemize = { keepUserApp -> viewModel.desystemize(row, keepUserApp) },
            onRevert = { viewModel.revert(row) },
            onKeep = { viewModel.keep(row) },
        )
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            icon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
            title = { Text("Reboot now?") },
            text = { Text("Pending systemize changes are applied on boot.") },
            confirmButton = {
                TextButton(onClick = { confirmReboot = false; viewModel.reboot() }) { Text("Reboot") }
            },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Later") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppSheet(
    row: AppRow,
    status: AppStatus,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSystemize: (privileged: Boolean) -> Unit,
    onDesystemize: (keepUserApp: Boolean) -> Unit,
    onRevert: () -> Unit,
    onKeep: () -> Unit,
) {
    val app = row.app
    var privileged by rememberSaveable { mutableStateOf(true) }
    var confirmDesystemize by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(row, size = 56.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(row.label, style = MaterialTheme.typography.headlineSmall)
                    Text(row.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            val details = listOfNotNull(
                "Status" to status.label,
                app?.let { "Version" to "${it.versionName ?: "?"} (${it.versionCode})" },
                app?.let { "Code path" to it.sourceDir.substringBeforeLast('/') },
            )
            details.forEachIndexed { index, (label, value) ->
                GroupedListItem(
                    index = index,
                    count = details.size,
                    headline = { Text(value) },
                    supporting = { Text(label) },
                    modifier = Modifier.padding(bottom = GroupGap),
                )
            }
            Spacer(Modifier.height(20.dp))

            if (busy) {
                LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            when (status) {
                AppStatus.User -> {
                    Text("Install as", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        ToggleButton(
                            checked = privileged,
                            onCheckedChange = { privileged = true },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text("priv-app") }
                        ToggleButton(
                            checked = !privileged,
                            onCheckedChange = { privileged = false },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) { Text("app") }
                    }
                    Text(
                        if (privileged) "Privileged system app. Privileged permissions it requests are allowlisted automatically."
                        else "Regular system app, without privileged permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(onClick = { onSystemize(privileged) }, modifier = Modifier.fillMaxWidth()) { Text("Systemize") }
                }
                AppStatus.System -> {
                    Text(
                        "Hides the system copy of this app. You can keep it as a normal user app or remove it completely.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Button(onClick = { confirmDesystemize = true }, modifier = Modifier.fillMaxWidth()) { Text("De-systemize") }
                }
                AppStatus.SystemizePending, AppStatus.DesystemizePending, AppStatus.RemovePending ->
                    FilledTonalButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("Cancel pending change") }
                AppStatus.Systemized, AppStatus.Desystemized ->
                    FilledTonalButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("Revert") }
                AppStatus.Removed ->
                    FilledTonalButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("Restore system app") }
                AppStatus.RevertPending ->
                    FilledTonalButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text("Keep current state") }
            }
        }
    }

    if (confirmDesystemize && app != null) {
        DesystemizeDialog(
            app = app,
            onDismiss = { confirmDesystemize = false },
            onConfirm = { keepUserApp -> confirmDesystemize = false; onDesystemize(keepUserApp) },
        )
    }
}

@Composable
private fun DesystemizeDialog(app: InstalledApp, onDismiss: () -> Unit, onConfirm: (keepUserApp: Boolean) -> Unit) {
    var keepUserApp by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("De-systemize ${app.label}?") },
        text = {
            Column(Modifier.selectableGroup()) {
                ChoiceRow(
                    selected = keepUserApp,
                    title = "Keep as user app",
                    description = if (app.isUpdatedSystem) "Its installed update stays, with all app data." else "A user copy is installed first, so the app and its data stay.",
                    onClick = { keepUserApp = true },
                )
                ChoiceRow(
                    selected = !keepUserApp,
                    title = "Remove completely",
                    description = "The app and all its data are deleted after the reboot.",
                    onClick = { keepUserApp = false },
                )
                Text(
                    "Core system apps can break the system without them. Either way you can restore it here, or by removing the System Tools module in your root manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keepUserApp) }) { Text(if (keepUserApp) "De-systemize" else "Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** App icon, or a generic one for packages that aren't installed (removed apps). */
@Composable
private fun RowIcon(row: AppRow, size: Dp = 40.dp) {
    if (row.app != null) {
        AppIcon(row.app, size = size)
    } else {
        Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size))
    }
}
