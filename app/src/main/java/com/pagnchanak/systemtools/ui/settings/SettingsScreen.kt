package com.pagnchanak.systemtools.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pagnchanak.systemtools.BuildConfig
import com.pagnchanak.systemtools.ui.components.GroupGap
import com.pagnchanak.systemtools.ui.components.GroupedListItem
import com.pagnchanak.systemtools.ui.components.SectionHeader
import com.pagnchanak.systemtools.ui.components.ToolScaffold
import com.pagnchanak.systemtools.update.UpdatePanel
import com.pagnchanak.systemtools.update.UpdatePrefs
import com.pagnchanak.systemtools.update.Updater

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = remember { UpdatePrefs(LocalContext.current) }
    var channel by remember { mutableStateOf(prefs.channel) }
    var autoCheck by remember { mutableStateOf(prefs.autoCheck) }
    val channels = listOf(Updater.CHANNEL_STABLE to "Stable", Updater.CHANNEL_DEV to "Dev")

    ToolScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GroupGap),
        ) {
            item { SectionHeader("Updates") }
            item {
                GroupedListItem(
                    index = 0, count = 3,
                    headline = { Text("Channel") },
                    supporting = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Stable is tested; Dev gets every change first")
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                channels.forEachIndexed { i, (id, label) ->
                                    SegmentedButton(
                                        selected = channel == id,
                                        onClick = { channel = id; prefs.channel = id },
                                        shape = SegmentedButtonDefaults.itemShape(i, channels.size),
                                    ) { Text(label) }
                                }
                            }
                        }
                    },
                )
            }
            item {
                GroupedListItem(
                    index = 1, count = 3,
                    headline = { Text("Check automatically") },
                    supporting = { Text("Look for updates when the app opens") },
                    trailing = {
                        Switch(checked = autoCheck, onCheckedChange = { autoCheck = it; prefs.autoCheck = it })
                    },
                    onClick = { autoCheck = !autoCheck; prefs.autoCheck = autoCheck },
                )
            }
            item {
                GroupedListItem(
                    index = 2, count = 3,
                    headline = { Text("Check now") },
                    supporting = { UpdatePanel(channel, Modifier.padding(top = 4.dp)) },
                )
            }
            item { SectionHeader("About") }
            item {
                GroupedListItem(
                    index = 0, count = 1,
                    headline = { Text("System Tools ${BuildConfig.VERSION_NAME}") },
                    supporting = { Text("Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.CHANNEL} channel") },
                )
            }
        }
    }
}
