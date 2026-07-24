package com.dav3.immichframe.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dav3.immichframe.domain.model.FillMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeAlbums: () -> Unit,
    onReset: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val s = state.settings

    var editingUrl by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    var urlDraft by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var keyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Slideshow ---
            Text("Slideshow", style = MaterialTheme.typography.titleSmall)

            Text("Interval: ${s.intervalSeconds}s")
            Slider(
                value = s.intervalSeconds.toFloat(),
                onValueChange = { viewModel.updateInterval(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
            )

            HorizontalDivider()

            Text("Image Fit", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.fillMode == FillMode.CONTAIN, "Contain") { viewModel.updateFillMode(FillMode.CONTAIN) }
                FilterChip(s.fillMode == FillMode.COVER, "Cover") { viewModel.updateFillMode(FillMode.COVER) }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Fullscreen") },
                supportingContent = { Text("Hide system bars") },
                trailingContent = { Switch(checked = s.fullscreen, onCheckedChange = { viewModel.toggleFullscreen() }) },
            )
            ListItem(
                headlineContent = { Text("Show Clock") },
                trailingContent = { Switch(checked = s.showClock, onCheckedChange = { viewModel.toggleClock() }) },
            )
            ListItem(
                headlineContent = { Text("Keep Screen On") },
                trailingContent = { Switch(checked = s.keepScreenOn, onCheckedChange = { viewModel.toggleKeepScreenOn() }) },
            )

            HorizontalDivider()

            // --- Albums ---
            Text("Albums", style = MaterialTheme.typography.titleSmall)
            Button(onClick = onChangeAlbums, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Text("Change Album Selection")
            }

            HorizontalDivider()

            // --- Connection ---
            Text("Connection", style = MaterialTheme.typography.titleSmall)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!editingUrl) {
                        Text("Server", style = MaterialTheme.typography.labelSmall)
                        Text(state.serverUrl.ifBlank { "Not set" }, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        OutlinedTextField(
                            value = urlDraft,
                            onValueChange = { urlDraft = it },
                            label = { Text("Server URL") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (editingUrl) {
                            TextButton(onClick = {
                                urlDraft = state.serverUrl
                                editingUrl = false
                            }) { Text("Cancel") }
                            Button(onClick = {
                                viewModel.updateServerUrl(urlDraft)
                                editingUrl = false
                            }) { Text("Save") }
                        } else {
                            TextButton(onClick = { editingUrl = true }) { Text("Edit") }
                        }
                    }

                    HorizontalDivider()

                    if (!editingKey) {
                        Text("API Key", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (state.apiKey.isBlank()) "Not set" else "•".repeat(20),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (editingKey) {
                            TextButton(onClick = {
                                keyDraft = state.apiKey
                                editingKey = false
                            }) { Text("Cancel") }
                            Button(onClick = {
                                viewModel.updateApiKey(keyDraft)
                                editingKey = false
                            }) { Text("Save") }
                        } else {
                            TextButton(onClick = { editingKey = true }) { Text("Edit") }
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- Danger zone ---
            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset All Settings", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Everything?") },
            text = { Text("This clears server URL, API key, selected albums, and all slideshow settings. You'll be sent back to the setup screen.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetAll()
                    onReset()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
