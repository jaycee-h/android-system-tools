package com.pagnchanak.systemtools.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pagnchanak.systemtools.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: UpdateInfo) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

class UpdateController(private val context: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    fun check(channel: String, onResult: (UpdateState) -> Unit = {}) {
        state = UpdateState.Checking
        scope.launch {
            state = try {
                val info = Updater.latest(channel)
                if (info.isNewer) UpdateState.Available(info) else UpdateState.UpToDate(info)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Couldn't check for updates")
            }
            onResult(state)
        }
    }

    fun install(info: UpdateInfo) {
        if (!Updater.canInstall(context)) {
            state = UpdateState.NeedsPermission(info)
            return
        }
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val apk = Updater.download(context, info) { p -> state = UpdateState.Downloading(info, p) }
                state = UpdateState.Installing(info)
                Updater.install(context, apk)
            } catch (e: Exception) {
                state = UpdateState.Failed(e.message ?: "Update failed", info)
            }
        }
    }

    fun grantPermission() = Updater.openInstallPermissionSettings(context)
    fun reset() { state = UpdateState.Idle }
}

@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateController(context.applicationContext, scope) }
}

private var checkedThisLaunch = false

/** Checks once per app launch (if auto-check is on) and offers the update in a dialog. */
@Composable
fun StartupUpdatePrompt() {
    val context = LocalContext.current
    val prefs = remember { UpdatePrefs(context) }
    val ctl = rememberUpdateController()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (checkedThisLaunch || !prefs.autoCheck) return@LaunchedEffect
        checkedThisLaunch = true
        ctl.check(prefs.channel) { st ->
            if (st is UpdateState.Available && st.info.versionCode != prefs.skippedVersion) visible = true
        }
    }

    if (!visible) return
    fun close() { visible = false; ctl.reset() }

    val st = ctl.state
    AlertDialog(
        onDismissRequest = { if (st !is UpdateState.Downloading && st !is UpdateState.Installing) close() },
        title = { Text(if (st is UpdateState.Failed) "Update" else "Update available") },
        text = { UpdateBody(st) },
        confirmButton = {
            when (st) {
                is UpdateState.Available -> Button(onClick = { ctl.install(st.info) }) { Text("Update now") }
                is UpdateState.NeedsPermission -> Button(onClick = { ctl.grantPermission() }) { Text("Allow") }
                is UpdateState.Failed -> st.info?.let { info ->
                    Button(onClick = { ctl.install(info) }) { Text("Retry") }
                } ?: TextButton(onClick = ::close) { Text("Close") }
                else -> {}
            }
        },
        dismissButton = {
            when (st) {
                is UpdateState.Available -> Row {
                    TextButton(onClick = { prefs.skippedVersion = st.info.versionCode; close() }) { Text("Skip") }
                    TextButton(onClick = ::close) { Text("Later") }
                }
                is UpdateState.NeedsPermission -> Row {
                    TextButton(onClick = { ctl.install(st.info) }) { Text("I've allowed it") }
                    TextButton(onClick = ::close) { Text("Cancel") }
                }
                is UpdateState.Failed -> TextButton(onClick = ::close) { Text("Close") }
                else -> {}
            }
        },
    )
}

@Composable
fun UpdateBody(st: UpdateState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (st) {
            UpdateState.Idle, UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is UpdateState.UpToDate -> Text(
                "Version ${BuildConfig.VERSION_NAME} is the latest on the ${st.latest.channel} channel."
            )
            is UpdateState.Available -> {
                Text("${BuildConfig.VERSION_NAME} → ${st.info.versionName}", style = MaterialTheme.typography.titleSmall)
                if (st.info.apkSize > 0) Text(
                    "%.1f MB · via %s".format(st.info.apkSize / 1_048_576f, st.info.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (st.info.notes.isNotBlank()) Text(
                    st.info.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            }
            is UpdateState.Downloading -> {
                Text("Downloading ${st.info.versionName}… ${(st.progress * 100).toInt()}%")
                LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.NeedsPermission -> Text(
                "Android needs your permission for System Tools to install updates. " +
                    "Tap Allow, turn on \"Allow from this source\", then come back."
            )
            is UpdateState.Installing -> Text("Opening the installer… Confirm the update when Android asks.")
            is UpdateState.Failed -> Text(st.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Inline check/install controls for the Settings screen. */
@Composable
fun UpdatePanel(channel: String, modifier: Modifier = Modifier) {
    val ctl = rememberUpdateController()
    val st = ctl.state
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st !is UpdateState.Idle) UpdateBody(st)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (st) {
                is UpdateState.Available -> Button(onClick = { ctl.install(st.info) }) { Text("Download & install") }
                is UpdateState.NeedsPermission -> {
                    Button(onClick = { ctl.grantPermission() }) { Text("Allow installs") }
                    TextButton(onClick = { ctl.install(st.info) }) { Text("Continue") }
                }
                is UpdateState.Failed -> FilledTonalButton(onClick = {
                    st.info?.let { ctl.install(it) } ?: ctl.check(channel)
                }) { Text("Retry") }
                is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> {}
                else -> FilledTonalButton(onClick = { ctl.check(channel) }) { Text("Check for updates") }
            }
        }
    }
}
