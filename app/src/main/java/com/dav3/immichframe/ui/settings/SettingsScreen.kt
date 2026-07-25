package com.dav3.immichframe.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.PhotoAnimation
import com.dav3.immichframe.domain.system.needsBootPermission
import com.dav3.immichframe.domain.system.openBootPermissionSettings
import java.text.SimpleDateFormat
import java.util.Date

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
    val context = LocalContext.current
    val installedFromPlayStore = remember {
        try {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            installer == "com.android.vending"
        } catch (_: Exception) {
            false
        }
    }

    var editingUrl by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showBootPermissionDialog by remember { mutableStateOf(false) }

    var urlDraft by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var keyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // ============================= PLAYBACK =============================
            SectionHeader(stringResource(R.string.section_playback))

            Text("${stringResource(R.string.interval)}: ${s.intervalSeconds}s")
            Slider(
                value = s.intervalSeconds.toFloat(),
                onValueChange = { viewModel.updateInterval(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
            )

            SwitchItem(
                title = stringResource(R.string.shuffle),
                subtitle = stringResource(R.string.shuffle_desc),
                checked = s.shuffle,
                onToggle = { viewModel.toggleShuffle() },
            )
            SwitchItem(
                title = stringResource(R.string.skip_videos),
                subtitle = stringResource(R.string.skip_videos_desc),
                checked = s.skipVideos,
                onToggle = { viewModel.toggleSkipVideos() },
            )
            SwitchItem(
                title = stringResource(R.string.muted),
                subtitle = stringResource(R.string.muted_desc),
                checked = s.muted,
                onToggle = { viewModel.toggleMuted() },
            )

            // Photo Animations
            SwitchItem(
                title = stringResource(R.string.photo_animations),
                subtitle = stringResource(R.string.photo_animations_desc),
                checked = s.photoAnimations,
                onToggle = { viewModel.togglePhotoAnimations() },
            )
            if (s.photoAnimations) {
                Text(
                    stringResource(R.string.photo_animations_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PhotoAnimation.entries.forEach { anim ->
                    val checked = when (anim) {
                        PhotoAnimation.ZOOM_IN -> s.animZoomIn
                        PhotoAnimation.ZOOM_OUT -> s.animZoomOut
                        PhotoAnimation.PAN_LEFT -> s.animPanLeft
                        PhotoAnimation.PAN_RIGHT -> s.animPanRight
                        PhotoAnimation.PAN_UP -> s.animPanUp
                        PhotoAnimation.PAN_DOWN -> s.animPanDown
                    }
                    SwitchItem(
                        title = anim.displayName(),
                        checked = checked,
                        onToggle = { viewModel.toggleAnimation(anim) },
                    )
                }
            }

            HorizontalDivider()

            // ============================= DISPLAY =============================
            SectionHeader(stringResource(R.string.section_display))

            Text(stringResource(R.string.image_fit), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.fillMode == FillMode.CONTAIN, stringResource(R.string.contain)) { viewModel.updateFillMode(FillMode.CONTAIN) }
                FilterChip(s.fillMode == FillMode.COVER, stringResource(R.string.cover)) { viewModel.updateFillMode(FillMode.COVER) }
            }
            SwitchItem(
                title = stringResource(R.string.adaptive_background),
                subtitle = stringResource(R.string.adaptive_background_desc),
                checked = s.adaptiveBackground,
                onToggle = { viewModel.toggleAdaptiveBackground() },
            )
            SwitchItem(
                title = stringResource(R.string.fullscreen),
                subtitle = stringResource(R.string.fullscreen_desc),
                checked = s.fullscreen,
                onToggle = { viewModel.toggleFullscreen() },
            )
            SwitchItem(
                title = stringResource(R.string.keep_screen_on),
                checked = s.keepScreenOn,
                onToggle = { viewModel.toggleKeepScreenOn() },
            )

            HorizontalDivider()

            // ============================= CLOCK =============================
            SectionHeader(stringResource(R.string.section_clock))

            SwitchItem(
                title = stringResource(R.string.show_clock),
                checked = s.showClock,
                onToggle = { viewModel.toggleClock() },
            )
            if (s.showClock) {
                Surface(
                    color = Color(0x80000000),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(Date()),
                        color = Color.White,
                        fontSize = s.clockSize.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                Text("${stringResource(R.string.clock_size)}: ${s.clockSize.toInt()}sp")
                Slider(
                    value = s.clockSize,
                    onValueChange = { viewModel.updateClockSize(it) },
                    valueRange = 24f..96f,
                )
                Text(
                    stringResource(R.string.drag_clock_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchItem(
                    title = stringResource(R.string.snap_to_grid),
                    subtitle = stringResource(R.string.snap_to_grid_desc),
                    checked = s.clockSnapToGrid,
                    onToggle = { viewModel.toggleClockSnapToGrid() },
                )
            }

            HorizontalDivider()

            // ============================= SYSTEM =============================
            SectionHeader(stringResource(R.string.section_system))

            SwitchItem(
                title = stringResource(R.string.start_on_boot),
                subtitle = if (s.startOnBoot && needsBootPermission() && !s.bootVerified) {
                    stringResource(R.string.boot_not_verified_desc)
                } else {
                    stringResource(R.string.start_on_boot_desc)
                },
                checked = s.startOnBoot,
                onToggle = {
                    viewModel.toggleStartOnBoot()
                    if (!s.startOnBoot && needsBootPermission()) {
                        showBootPermissionDialog = true
                    }
                },
            )
            if (s.startOnBoot && needsBootPermission() && !s.bootVerified) {
                TextButton(
                    onClick = { openBootPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_autostart), style = MaterialTheme.typography.labelLarge)
                }
            }

            if (!installedFromPlayStore) {
                SwitchItem(
                    title = stringResource(R.string.auto_update),
                    subtitle = stringResource(R.string.auto_update_desc),
                    checked = s.autoUpdate,
                    onToggle = { viewModel.toggleAutoUpdate() },
                )
            }

            HorizontalDivider()

            // ============================= ALBUMS =============================
            SectionHeader(stringResource(R.string.section_albums))

            Button(onClick = onChangeAlbums, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Text(stringResource(R.string.change_albums))
            }

            HorizontalDivider()

            // ============================= CONNECTION =============================
            SectionHeader(stringResource(R.string.section_connection))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditableFieldRow(
                        label = stringResource(R.string.server),
                        displayValue = state.serverUrl.ifBlank { stringResource(R.string.not_set) },
                        fieldLabel = stringResource(R.string.server_url),
                        draft = urlDraft,
                        onDraftChange = { urlDraft = it },
                        editing = editingUrl,
                        onEdit = { editingUrl = true },
                        onCancel = {
                            urlDraft = state.serverUrl
                            editingUrl = false
                        },
                        onSave = {
                            viewModel.updateServerUrl(urlDraft)
                            editingUrl = false
                        },
                        keyboardType = KeyboardType.Uri,
                    )

                    HorizontalDivider()

                    EditableFieldRow(
                        label = stringResource(R.string.api_key),
                        displayValue = if (state.apiKey.isBlank()) stringResource(R.string.not_set) else "•".repeat(20),
                        fieldLabel = stringResource(R.string.api_key),
                        draft = keyDraft,
                        onDraftChange = { keyDraft = it },
                        editing = editingKey,
                        onEdit = { editingKey = true },
                        onCancel = {
                            keyDraft = state.apiKey
                            editingKey = false
                        },
                        onSave = {
                            viewModel.updateApiKey(keyDraft)
                            editingKey = false
                        },
                    )
                }
            }

            HorizontalDivider()

            // ============================= DANGER ZONE =============================
            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reset_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Boot permission dialog
    if (showBootPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showBootPermissionDialog = false },
            title = { Text(stringResource(R.string.autostart_perm_title)) },
            text = {
                Text(stringResource(R.string.autostart_perm_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    showBootPermissionDialog = false
                    openBootPermissionSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showBootPermissionDialog = false }) { Text(stringResource(R.string.skip)) }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    viewModel.resetAll()
                    onReset()
                }) { Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// --- Helper composables ---

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
    )
}

/**
 * Read/edit/save row for a text field — shared by server URL and API key.
 */
@Composable
private fun EditableFieldRow(
    label: String,
    displayValue: String,
    fieldLabel: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    editing: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    if (!editing) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(displayValue, style = MaterialTheme.typography.bodyMedium)
    } else {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text(fieldLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        if (editing) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Button(onClick = onSave) { Text(stringResource(R.string.save)) }
        } else {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
        }
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun PhotoAnimation.displayName(): String = when (this) {
    PhotoAnimation.ZOOM_IN -> stringResource(R.string.anim_zoom_in)
    PhotoAnimation.ZOOM_OUT -> stringResource(R.string.anim_zoom_out)
    PhotoAnimation.PAN_LEFT -> stringResource(R.string.anim_pan_left)
    PhotoAnimation.PAN_RIGHT -> stringResource(R.string.anim_pan_right)
    PhotoAnimation.PAN_UP -> stringResource(R.string.anim_pan_up)
    PhotoAnimation.PAN_DOWN -> stringResource(R.string.anim_pan_down)
}
