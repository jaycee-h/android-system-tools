package com.pagnchanak.systemtools.tools.systemizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
        val apps = state.visibleApps
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
            if (apps.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (state.filter == AppFilter.Managed) Icons.Filled.Apps else Icons.Filled.SearchOff,
                        title = if (state.filter == AppFilter.Managed) "Nothing changed yet" else "No apps found",
                        message = if (state.filter == AppFilter.Managed) "Apps you systemize or de-systemize show up here." else "Try another search or filter.",
                    )
                }
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val status = state.status(app)
                GroupedListItem(
                    index = index,
                    count = apps.size,
                    headline = { Text(app.label) },
                    supporting = {
                        Text(
                            if (state.filter == AppFilter.Managed) status.label else app.packageName,
                            color = if (status.pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leading = { AppIcon(app) },
                    onClick = { selected = app.packageName },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    state.apps.firstOrNull { it.packageName == selected }?.let { app ->
        AppSheet(
            app = app,
            status = state.status(app),
            busy = state.busy,
            onDismiss = { selected = null },
            onSystemize = { privileged -> viewModel.systemize(app, privileged) },
            onDesystemize = { viewModel.desystemize(app) },
            onRevert = { viewModel.revert(app) },
            onCancelRevert = { viewModel.cancelRevert(app) },
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
    app: InstalledApp,
    status: AppStatus,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSystemize: (privileged: Boolean) -> Unit,
    onDesystemize: () -> Unit,
    onRevert: () -> Unit,
    onCancelRevert: () -> Unit,
) {
    var privileged by rememberSaveable { mutableStateOf(true) }
    var confirmDesystemize by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, size = 56.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall)
                    Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            val details = listOf(
                "Status" to status.label,
                "Version" to "${app.versionName ?: "?"} (${app.versionCode})",
                "Code path" to app.sourceDir.substringBeforeLast('/'),
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
                        "Hides the system copy so this becomes a normal user app." +
                            if (app.isUpdatedSystem) " Its installed update is kept." else " A user copy is installed first so the app and its data stay.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Button(onClick = { confirmDesystemize = true }, modifier = Modifier.fillMaxWidth()) { Text("De-systemize") }
                }
                AppStatus.SystemizePending, AppStatus.DesystemizePending ->
                    FilledTonalButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("Cancel pending change") }
                AppStatus.Systemized, AppStatus.Desystemized ->
                    FilledTonalButton(onClick = onRevert, modifier = Modifier.fillMaxWidth()) { Text("Revert") }
                AppStatus.RevertPending ->
                    FilledTonalButton(onClick = onCancelRevert, modifier = Modifier.fillMaxWidth()) { Text("Keep current state") }
            }
        }
    }

    if (confirmDesystemize) {
        AlertDialog(
            onDismissRequest = { confirmDesystemize = false },
            title = { Text("De-systemize ${app.label}?") },
            text = {
                Text("Core system apps can break the system if they lose system status. You can revert from this screen, or by removing the module in your root manager.")
            },
            confirmButton = {
                TextButton(onClick = { confirmDesystemize = false; onDesystemize() }) { Text("De-systemize") }
            },
            dismissButton = { TextButton(onClick = { confirmDesystemize = false }) { Text("Cancel") } },
        )
    }
}
