package com.dav3.immichframe.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dav3.immichframe.R
import com.dav3.immichframe.ui.onboarding.TourHost
import com.dav3.immichframe.ui.onboarding.TourScreen
import com.dav3.immichframe.ui.onboarding.rememberTourState
import com.dav3.immichframe.ui.onboarding.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSuccess: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.connectionState) {
        if (state.connectionState == ConnectionState.SUCCESS) {
            kotlinx.coroutines.delay(800)
            onSuccess()
        }
    }

    var showKey by remember { mutableStateOf(false) }

    val tourState = rememberTourState()
    val completedSteps by viewModel.onboardingSteps.collectAsState()

    TourHost(
        screen = TourScreen.SETUP,
        completedSteps = completedSteps,
        onStepCompleted = viewModel::markStepCompleted,
        onSkipped = { },
        tourState = tourState,
    ) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var protocolExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = protocolExpanded,
                        onExpandedChange = { protocolExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = if (state.useHttps) "https://" else "http://",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.protocol)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(protocolExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(120.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = protocolExpanded,
                            onDismissRequest = { protocolExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("https://") },
                                onClick = {
                                    viewModel.updateProtocol(true)
                                    protocolExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("http://") },
                                onClick = {
                                    viewModel.updateProtocol(false)
                                    protocolExpanded = false
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = state.domain,
                        onValueChange = viewModel::updateDomain,
                        label = { Text(stringResource(R.string.domain)) },
                        placeholder = { Text(stringResource(R.string.domain_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .weight(1f)
                            .tourTarget("setup_server", tourState),
                    )
                }
                // Show resulting URL
                if (state.domain.isNotBlank()) {
                    Text(
                        state.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) stringResource(R.string.hide_key) else stringResource(R.string.show_key),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .tourTarget("setup_apikey", tourState),
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = viewModel::testConnection,
                    enabled = state.connectionState != ConnectionState.CONNECTING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tourTarget("setup_connect", tourState),
                ) {
                    if (state.connectionState == ConnectionState.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.test_connection))
                }

                AnimatedVisibility(state.connectionState == ConnectionState.SUCCESS) {
                    Text(
                        stringResource(R.string.connected_as, state.connectedEmail ?: ""),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                AnimatedVisibility(state.connectionState == ConnectionState.ERROR) {
                    Text(
                        state.errorMessage ?: stringResource(R.string.connection_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
