package com.pagnchanak.systemtools.tools.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pagnchanak.systemtools.ui.components.ToolScaffold

@Composable
fun ShellScreen(onBack: () -> Unit, viewModel: ShellViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.history.size) {
        if (state.history.isNotEmpty()) listState.animateScrollToItem(state.history.lastIndex)
    }

    fun submit() {
        viewModel.run(input)
        input = ""
    }

    ToolScaffold(title = "Root Shell", onBack = onBack) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
        if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(12.dp)) {
                items(state.history) { entry -> ShellEntryView(entry) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            IconButton(onClick = viewModel::clear) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear")
            }
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Command") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = CircleShape,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(onClick = ::submit, enabled = input.isNotBlank() && !state.running) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run")
            }
        }
    }
    }
}

@Composable
private fun ShellEntryView(entry: ShellEntry) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(Modifier.padding(bottom = 12.dp)) {
        Text("# ${entry.command}", style = mono, color = MaterialTheme.colorScheme.primary)
        if (entry.output.isNotEmpty()) {
            Text(entry.output.joinToString("\n"), style = mono)
        }
        if (entry.code != 0) {
            Text("exit ${entry.code}", style = mono, color = MaterialTheme.colorScheme.error)
        }
    }
}
