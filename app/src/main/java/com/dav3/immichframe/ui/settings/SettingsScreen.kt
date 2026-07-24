package com.dav3.immichframe.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dav3.immichframe.domain.model.FillMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val s by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Slideshow interval
            Text("Slideshow Interval: ${s.intervalSeconds}s", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = s.intervalSeconds.toFloat(),
                onValueChange = { viewModel.updateInterval(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22
            )

            HorizontalDivider()

            // Fill mode
            Text("Image Fit", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.fillMode == FillMode.CONTAIN,
                    onClick = { viewModel.updateFillMode(FillMode.CONTAIN) },
                    label = { Text("Contain") }
                )
                FilterChip(
                    selected = s.fillMode == FillMode.COVER,
                    onClick = { viewModel.updateFillMode(FillMode.COVER) },
                    label = { Text("Cover") }
                )
            }

            HorizontalDivider()

            // Toggles
            ListItem(
                headlineContent = { Text("Ken Burns Effect") },
                trailingContent = { Switch(checked = s.kenBurns, onCheckedChange = { viewModel.toggleKenBurns() }) }
            )
            ListItem(
                headlineContent = { Text("Show Clock") },
                trailingContent = { Switch(checked = s.showClock, onCheckedChange = { viewModel.toggleClock() }) }
            )
            ListItem(
                headlineContent = { Text("Keep Screen On") },
                trailingContent = { Switch(checked = s.keepScreenOn, onCheckedChange = { viewModel.toggleKeepScreenOn() }) }
            )
        }
    }
}
