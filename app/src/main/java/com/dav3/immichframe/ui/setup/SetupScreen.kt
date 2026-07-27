package com.dav3.immichframe.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    var showPassword by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Handle OAuth callback deep link
    LaunchedEffect(Unit) {
        val intent = (context as? android.app.Activity)?.intent
        val data = intent?.data
        if (data != null && data.scheme == "com.dav3.immichframe" && data.host == "oauth-callback") {
            viewModel.handleOAuthCallback(data.toString())
            // Clear the intent so it doesn't re-trigger on config change
            (context as? android.app.Activity)?.intent = Intent()
        }
    }

    // Launch browser when OAuth starts
    LaunchedEffect(state.pendingOAuth) {
        val oauth = state.pendingOAuth ?: return@LaunchedEffect
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, Uri.parse(oauth.authUrl))
    }

    val tourState = rememberTourState()
    val completedSteps by viewModel.onboardingSteps.collectAsState()

    if (showHelpDialog) {
        ApiKeyHelpDialog(onDismiss = { showHelpDialog = false })
    }

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
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
                Spacer(Modifier.height(16.dp))
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

                when (state.step) {
                    SetupStep.DOMAIN -> DomainStep(
                        state = state,
                        viewModel = viewModel,
                        tourState = tourState,
                    )
                    SetupStep.AUTH -> AuthStep(
                        state = state,
                        viewModel = viewModel,
                        showKey = showKey,
                        showPassword = showPassword,
                        onToggleShowKey = { showKey = !showKey },
                        onToggleShowPassword = { showPassword = !showPassword },
                        onShowHelp = { showHelpDialog = true },
                        onBack = viewModel::backToDomainStep,
                        onOpenOAuth = {
                            val intent = CustomTabsIntent.Builder().build()
                            intent.launchUrl(context, Uri.parse(it))
                        },
                        tourState = tourState,
                    )
                }

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = viewModel::resetOnboarding) {
                    Text(stringResource(R.string.show_tour))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
    tourState: com.dav3.immichframe.ui.onboarding.TourState,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    modifier = Modifier.menuAnchor().width(120.dp),
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
        if (state.domain.isNotBlank()) {
            Text(
                state.serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::validateServer,
            enabled = state.connectionState != ConnectionState.CONNECTING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.connectionState == ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.validate_server))
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

@Composable
private fun AuthStep(
    state: SetupUiState,
    viewModel: SetupViewModel,
    showKey: Boolean,
    showPassword: Boolean,
    onToggleShowKey: () -> Unit,
    onToggleShowPassword: () -> Unit,
    onShowHelp: () -> Unit,
    onBack: () -> Unit,
    onOpenOAuth: (String) -> Unit,
    tourState: com.dav3.immichframe.ui.onboarding.TourState,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Server info banner
        state.serverVersionDisplay?.let { version ->
            Text(
                stringResource(R.string.server_version_label, version),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            state.serverUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(24.dp))

        when (state.authMode) {
            AuthMode.MANUAL_KEY -> ManualKeySection(
                state = state,
                viewModel = viewModel,
                showKey = showKey,
                onToggleShowKey = onToggleShowKey,
                onShowHelp = onShowHelp,
                onSwitchToGenerate = { viewModel.setAuthMode(AuthMode.GENERATE) },
                tourState = tourState,
            )
            AuthMode.GENERATE -> GenerateKeySection(
                state = state,
                viewModel = viewModel,
                showPassword = showPassword,
                onToggleShowPassword = onToggleShowPassword,
                onShowHelp = onShowHelp,
                onOpenOAuth = onOpenOAuth,
                onSwitchToManual = { viewModel.setAuthMode(AuthMode.MANUAL_KEY) },
                tourState = tourState,
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
        AnimatedVisibility(state.connectionState == ConnectionState.SUCCESS) {
            Text(
                stringResource(R.string.connected_as, state.connectedEmail ?: ""),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun GenerateKeySection(
    state: SetupUiState,
    viewModel: SetupViewModel,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    onShowHelp: () -> Unit,
    onOpenOAuth: (String) -> Unit,
    onSwitchToManual: () -> Unit,
    tourState: com.dav3.immichframe.ui.onboarding.TourState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tourTarget("setup_apikey", tourState),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.generate_key_title),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onShowHelp) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = stringResource(R.string.api_key_help),
                )
            }
        }
        Text(
            stringResource(R.string.generate_key_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.generate_key_account_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::updateEmail,
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleShowPassword) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) stringResource(R.string.hide) else stringResource(R.string.reveal),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = viewModel::generateKey,
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
            Text(stringResource(R.string.login_and_generate))
        }

        if (state.showOAuthButton) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::startOAuth,
                enabled = state.connectionState != ConnectionState.CONNECTING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sign_in_with_oauth))
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSwitchToManual) {
            Text(stringResource(R.string.enter_manually))
        }
    }
}

@Composable
private fun ManualKeySection(
    state: SetupUiState,
    viewModel: SetupViewModel,
    showKey: Boolean,
    onToggleShowKey: () -> Unit,
    onShowHelp: () -> Unit,
    onSwitchToGenerate: () -> Unit,
    tourState: com.dav3.immichframe.ui.onboarding.TourState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tourTarget("setup_apikey", tourState),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.api_key),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = onShowHelp) {
                Icon(
                    Icons.Default.HelpOutline,
                    contentDescription = stringResource(R.string.api_key_help),
                )
            }
        }
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::updateApiKey,
            label = { Text(stringResource(R.string.api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleShowKey) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKey) stringResource(R.string.hide_key) else stringResource(R.string.show_key),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.generate_key_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TextButton(
            onClick = onSwitchToGenerate,
            modifier = Modifier
                .tourTarget("setup_generate_key", tourState),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.generate_key))
        }
    }
}

@Composable
private fun ApiKeyHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_key_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.api_key_help_what),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.api_key_help_why),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.api_key_help_password),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}
