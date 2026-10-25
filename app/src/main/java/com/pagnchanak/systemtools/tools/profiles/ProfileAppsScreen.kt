package com.pagnchanak.systemtools.tools.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileAppsScreen(onBack: () -> Unit, viewModel: ProfileAppsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRemove by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    ToolScaffold(
        title = "Work Profile Apps",
        subtitle = state.selectedProfile?.let { "${it.name} · user ${it.id}" },
        onBack = onBack,
        snackbarHostState = snackbar,
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
            if (state.profiles.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.WorkOff, "No profiles found", "Set up a work profile (or another user/profile) first, then come back.")
                }
                return@LazyColumn
            }
            if (state.profiles.size > 1) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        state.profiles.forEachIndexed { index, profile ->
                            ToggleButton(
                                checked = profile.id == state.selectedProfileId,
                                onCheckedChange = { viewModel.selectProfile(profile.id) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    state.profiles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    if (profile.isWorkProfile) Icons.Filled.Work else Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                )
                                Text(profile.name, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item { SearchField(state.query, viewModel::setQuery, "Search apps") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(ProfileFilter.entries) { filter ->
                        val selected = state.filter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.showSystem,
                            onClick = { viewModel.setShowSystem(!state.showSystem) },
                            label = { Text("System apps") },
                            leadingIcon = if (state.showSystem) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (apps.isEmpty()) {
                item { EmptyState(Icons.Filled.SearchOff, "No apps found", "Try another search or filter.") }
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val inProfile = app.packageName in state.installedInProfile
                val busy = app.packageName in state.busy
                GroupedListItem(
                    index = index,
                    count = apps.size,
                    headline = { Text(app.label) },
                    supporting = {
                        Text(
                            if (!app.installedForMe) "Only installed in another profile" else app.packageName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leading = { AppIcon(app) },
                    trailing = {
                        if (busy) {
                            LoadingIndicator(Modifier.size(36.dp))
                        } else {
                            Switch(
                                checked = inProfile,
                                onCheckedChange = { enable ->
                                    if (enable) viewModel.setEnabled(app, true) else confirmRemove = app
                                },
                            )
                        }
                    },
                    onClick = {
                        if (!busy) {
                            if (inProfile) confirmRemove = app else viewModel.setEnabled(app, true)
                        }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    confirmRemove?.let { app ->
        val profileName = state.selectedProfile?.name ?: "the profile"
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${app.label} from $profileName?") },
            text = { Text("Its data in $profileName is deleted. The app stays installed everywhere else.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; viewModel.setEnabled(app, false) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}
