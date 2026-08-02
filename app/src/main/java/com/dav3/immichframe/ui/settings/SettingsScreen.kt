package com.dav3.immichframe.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dav3.immichframe.BuildConfig
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.ClockFormat
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.PermissionStatus
import com.dav3.immichframe.domain.model.PhotoAnimation
import com.dav3.immichframe.domain.model.RequiredPermission
import com.dav3.immichframe.domain.system.hasOverlayPermission
import com.dav3.immichframe.domain.system.needsBootPermission
import com.dav3.immichframe.domain.system.openBootPermissionSettings
import com.dav3.immichframe.domain.system.openLauncherSettings
import com.dav3.immichframe.domain.system.openOverlayPermissionSettings
import com.dav3.immichframe.ui.onboarding.TourHost
import com.dav3.immichframe.ui.onboarding.TourScreen
import com.dav3.immichframe.ui.onboarding.TourSteps
import com.dav3.immichframe.ui.onboarding.rememberTourState
import com.dav3.immichframe.ui.onboarding.tourTarget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeAlbums: () -> Unit,
    onReset: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: com.dav3.immichframe.ui.update.UpdateViewModel = hiltViewModel(),
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
    var showOverlayDialog by remember { mutableStateOf(false) }

    // Track SYSTEM_ALERT_WINDOW ("Display over other apps") permission state.
    // Re-check on every ON_RESUME so the UI refreshes after the user returns
    // from the system permission settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var urlDraft by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var keyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey) }

    // API key security: reveal + biometric-gated copy
    var revealedApiKey by remember { mutableStateOf(false) }
    var showBiometricNotSetupDialog by remember { mutableStateOf(false) }
    val biometricLauncher = com.dav3.immichframe.ui.components.rememberBiometricLauncher()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val authTitleKey = stringResource(R.string.biometric_auth_title)
    val authSubtitleKey = stringResource(R.string.biometric_auth_subtitle_key)
    val authSubtitleAlbums = stringResource(R.string.biometric_auth_subtitle_albums)
    val apiKeyCopiedText = stringResource(R.string.api_key_copied)

    val tourState = rememberTourState()
    val completedSteps by viewModel.onboardingSteps.collectAsState()
    val scrollState = rememberScrollState()

    // Refresh permission status when the Settings screen is opened and whenever
    // the API key changes. Keying on state.apiKey (instead of Unit) ensures the
    // check fires once the real key has been collected from DataStore — on the
    // very first composition state.apiKey is still the default "" and the check
    // would otherwise be skipped entirely.
    LaunchedEffect(state.apiKey) {
        if (state.apiKey.isNotBlank()) viewModel.recheckPermissions()
    }

    TourHost(
        screen = TourScreen.SETTINGS,
        completedSteps = completedSteps,
        onStepCompleted = viewModel::markStepCompleted,
        onSkipped = { },
        tourState = tourState,
        onScrollToTarget = { targetKey ->
            // Scroll the target section header into view
            val step = TourSteps.SETTINGS.find { it.targetKey == targetKey }
            if (step != null) {
                // Approximate scroll positions for each section.
                // These are best-effort; the overlay will still work if slightly off.
                val targetY = when (step.id) {
                    "settings_system" -> 1200
                    "settings_cache" -> 2400
                    "settings_connection" -> 3200
                    else -> 0
                }
                scrollState.animateScrollTo(targetY)
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.settings))
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
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
                    .verticalScroll(scrollState),
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
                val videoDownloadDenied = state.permissionStatus
                    ?.let { it.statuses[RequiredPermission.ASSET_DOWNLOAD] == PermissionStatus.Denied }
                    ?: false

                SwitchItem(
                    title = stringResource(R.string.skip_videos),
                    subtitle = if (videoDownloadDenied) {
                        stringResource(R.string.skip_videos_locked_desc)
                    } else {
                        stringResource(R.string.skip_videos_desc)
                    },
                    checked = s.skipVideos,
                    onToggle = { viewModel.toggleSkipVideos() },
                    enabled = !videoDownloadDenied,
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

                // ============================= PHOTO METADATA =============================
                SectionHeader(stringResource(R.string.section_photo_metadata))
                SwitchItem(
                    title = stringResource(R.string.show_photo_date),
                    subtitle = stringResource(R.string.show_photo_date_desc),
                    checked = s.showPhotoDate,
                    onToggle = { viewModel.togglePhotoDate() },
                )
                SwitchItem(
                    title = stringResource(R.string.show_location),
                    subtitle = stringResource(R.string.show_location_desc),
                    checked = s.showLocation,
                    onToggle = { viewModel.toggleLocation() },
                )
                SwitchItem(
                    title = stringResource(R.string.show_description),
                    subtitle = stringResource(R.string.show_description_desc),
                    checked = s.showDescription,
                    onToggle = { viewModel.toggleDescription() },
                )
                SwitchItem(
                    title = stringResource(R.string.show_tags),
                    subtitle = stringResource(R.string.show_tags_desc),
                    checked = s.showTags,
                    onToggle = { viewModel.toggleTags() },
                )

                HorizontalDivider()

                // ============================= NIGHT MODE =============================
                SectionHeader(stringResource(R.string.section_night_mode))
                SwitchItem(
                    title = stringResource(R.string.night_mode),
                    subtitle = stringResource(R.string.night_mode_desc),
                    checked = s.nightMode,
                    onToggle = { viewModel.toggleNightMode() },
                )
                if (s.nightMode) {
                    Text(
                        stringResource(R.string.night_mode_alt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NightModeTimePicker(
                        label = stringResource(R.string.night_mode_start),
                        minutes = s.nightModeStart,
                        onTimeSelected = { viewModel.updateNightModeStart(it) },
                    )
                    NightModeTimePicker(
                        label = stringResource(R.string.night_mode_end),
                        minutes = s.nightModeEnd,
                        onTimeSelected = { viewModel.updateNightModeEnd(it) },
                    )
                    Text("${stringResource(R.string.night_mode_brightness)}: ${s.nightModeBrightness}%")
                    Slider(
                        value = s.nightModeBrightness.toFloat(),
                        onValueChange = { viewModel.updateNightModeBrightness(it.toInt()) },
                        valueRange = 0f..100f,
                    )
                    Text(
                        stringResource(R.string.night_mode_brightness_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
                        val hourTok = if (s.clockFormat == ClockFormat.H12) "hh" else "HH"
                        val secTok = if (s.clockSeconds) ":ss" else ""
                        val amPm = if (s.clockFormat == ClockFormat.H12) " a" else ""
                        val clockPreviewFmt = "$hourTok:mm$secTok$amPm"
                        Text(
                            SimpleDateFormat(clockPreviewFmt, LocalLocale.current.platformLocale).format(Date()),
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
                        stringResource(R.string.clock_format),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            s.clockFormat == ClockFormat.H24,
                            stringResource(R.string.clock_format_24h),
                        ) { viewModel.updateClockFormat(ClockFormat.H24) }
                        FilterChip(
                            s.clockFormat == ClockFormat.H12,
                            stringResource(R.string.clock_format_12h),
                        ) { viewModel.updateClockFormat(ClockFormat.H12) }
                    }
                    Text(
                        stringResource(R.string.drag_clock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchItem(
                        title = stringResource(R.string.clock_seconds),
                        subtitle = stringResource(R.string.clock_seconds_desc),
                        checked = s.clockSeconds,
                        onToggle = { viewModel.toggleClockSeconds() },
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
                Box(modifier = Modifier.tourTarget("settings_system_section", tourState)) {
                    SectionHeader(stringResource(R.string.section_system))
                }

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
                        if (!s.startOnBoot) {
                            // Turning ON: prompt for overlay permission if missing
                            if (!hasOverlay) {
                                showOverlayDialog = true
                            }
                            if (needsBootPermission()) {
                                showBootPermissionDialog = true
                            }
                        }
                    },
                )
                // Overlay ("Display over other apps") permission button — required on
                // Android 10+ for the boot receiver to launch the app.
                if (s.startOnBoot && !hasOverlay) {
                    TextButton(
                        onClick = { openOverlayPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.open_overlay),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (s.startOnBoot && needsBootPermission() && !s.bootVerified) {
                    TextButton(
                        onClick = { openBootPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.open_autostart), style = MaterialTheme.typography.labelLarge)
                    }
                }

                // ---- Launcher Mode (most reliable boot method) ----
                // Only visible when Start on Boot is enabled — launcher mode is a
                // complement to it for devices where BOOT_COMPLETED is unreliable.
                if (s.startOnBoot) {
                    SwitchItem(
                        title = stringResource(R.string.launcher_mode),
                        subtitle = stringResource(R.string.launcher_mode_desc),
                        checked = s.launcherMode,
                        onToggle = { viewModel.toggleLauncherMode(context) },
                    )
                    TextButton(
                        onClick = { openLauncherSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.open_launcher_settings),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                if (!installedFromPlayStore) {
                    SwitchItem(
                        title = stringResource(R.string.auto_update),
                        subtitle = stringResource(R.string.auto_update_desc),
                        checked = s.autoUpdate,
                        onToggle = { viewModel.toggleAutoUpdate() },
                    )
                    val updateState by updateViewModel.updateState.collectAsState()
                    TextButton(
                        onClick = { updateViewModel.checkForUpdateNow() },
                        enabled = !updateState.checking && !updateState.downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val label = when {
                            updateState.checking -> stringResource(R.string.update_checking)
                            updateState.downloading -> stringResource(R.string.update_downloading)
                            updateState.error != null -> stringResource(R.string.update_error)
                            else -> stringResource(R.string.check_now)
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // ---- Show Tour Again ----
                TextButton(
                    onClick = {
                        viewModel.resetOnboardingForSettings()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.show_tour),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                TextButton(
                    onClick = { viewModel.resetOnboarding() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.reset_all_tours),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider()

                // ============================= MEDIA CACHE =============================
                Box(modifier = Modifier.tourTarget("settings_cache_section", tourState)) {
                    SectionHeader(stringResource(R.string.section_media_cache))
                }

                val intervalValues = remember {
                    buildList {
                        add(1)
                        var v = 5
                        while (v <= 480) {
                            add(v)
                            v += 5
                        }
                    }
                }
                val currentIntervalIndex = intervalValues.indexOf(s.syncIntervalMinutes).coerceAtLeast(0)

                SwitchItem(
                    title = stringResource(R.string.auto_sync),
                    subtitle = stringResource(R.string.auto_sync_desc),
                    checked = s.autoSync,
                    onToggle = { viewModel.toggleAutoSync() },
                )
                Text("${stringResource(R.string.sync_interval)}: ${s.syncIntervalMinutes} min")
                Slider(
                    value = currentIntervalIndex.toFloat(),
                    onValueChange = {
                        val newIndex = it.roundToInt().coerceIn(0, intervalValues.lastIndex)
                        viewModel.updateSyncInterval(intervalValues[newIndex])
                    },
                    valueRange = 0f..intervalValues.lastIndex.toFloat(),
                    steps = intervalValues.lastIndex - 1,
                )
                TextButton(
                    onClick = { viewModel.syncNow() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_now))
                }

                HorizontalDivider()

                // ============================= ALBUMS =============================
                SectionHeader(stringResource(R.string.section_albums))

                Button(
                    onClick = {
                        biometricLauncher.launch(
                            title = authTitleKey,
                            subtitle = authSubtitleAlbums,
                            onNotSetup = { showBiometricNotSetupDialog = true },
                            onSuccess = { onChangeAlbums() },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.change_albums))
                }

                HorizontalDivider()

                // ============================= CONNECTION =============================
                Box(modifier = Modifier.tourTarget("settings_connection_section", tourState)) {
                    SectionHeader(stringResource(R.string.section_connection))
                }

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

                        // API Key — secure row: edit empties field, reveal/copy
                        // are biometric-gated. Key is never shown in plain text
                        // in an editable field.
                        // API Key — label on top, value below (full width so
                        // the key can wrap naturally), actions in a row below.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.api_key),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!editingKey) {
                                if (state.apiKey.isNotBlank()) {
                                    val displayText = if (revealedApiKey) {
                                        state.apiKey
                                    } else {
                                        "•".repeat(state.apiKey.length.coerceAtMost(20))
                                    }
                                    Text(
                                        displayText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = if (revealedApiKey) FontFamily.Monospace else null,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        IconButton(onClick = {
                                            biometricLauncher.launch(
                                                title = authTitleKey,
                                                subtitle = authSubtitleKey,
                                                onNotSetup = { showBiometricNotSetupDialog = true },
                                                onSuccess = { revealedApiKey = !revealedApiKey },
                                            )
                                        }) {
                                            Icon(
                                                if (revealedApiKey) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                                contentDescription = stringResource(
                                                    if (revealedApiKey) R.string.hide else R.string.reveal,
                                                ),
                                            )
                                        }
                                        IconButton(onClick = {
                                            biometricLauncher.launch(
                                                title = authTitleKey,
                                                subtitle = authSubtitleKey,
                                                onNotSetup = { showBiometricNotSetupDialog = true },
                                                onSuccess = {
                                                    val clipboard = context
                                                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText("API Key", state.apiKey),
                                                    )
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            apiKeyCopiedText,
                                                        )
                                                    }
                                                },
                                            )
                                        }) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.copy),
                                            )
                                        }
                                        TextButton(onClick = {
                                            keyDraft = ""
                                            editingKey = true
                                        }) { Text(stringResource(R.string.edit)) }
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            stringResource(R.string.not_set),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = {
                                            keyDraft = ""
                                            editingKey = true
                                        }) { Text(stringResource(R.string.edit)) }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = keyDraft,
                                    onValueChange = { keyDraft = it },
                                    label = { Text(stringResource(R.string.api_key)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = {
                                        keyDraft = state.apiKey
                                        editingKey = false
                                    }) { Text(stringResource(R.string.cancel)) }
                                    TextButton(onClick = {
                                        viewModel.updateApiKey(keyDraft)
                                        editingKey = false
                                    }) { Text(stringResource(R.string.save)) }
                                }
                            }
                        }
                    }
                }

                // ============================= PERMISSIONS =============================
                val permStatus = state.permissionStatus
                if (permStatus != null) {
                    PermissionStatusCard(
                        status = permStatus,
                        checking = state.permissionCheckInProgress,
                        onRecheck = { viewModel.recheckPermissions() },
                    )
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

    // Overlay permission dialog (SYSTEM_ALERT_WINDOW)
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text(stringResource(R.string.overlay_perm_title)) },
            text = {
                Text(stringResource(R.string.overlay_perm_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    openOverlayPermissionSettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) { Text(stringResource(R.string.skip)) }
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

    if (showBiometricNotSetupDialog) {
        AlertDialog(
            onDismissRequest = { showBiometricNotSetupDialog = false },
            title = { Text(stringResource(R.string.biometric_not_setup_title)) },
            text = { Text(stringResource(R.string.biometric_not_setup_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBiometricNotSetupDialog = false
                    com.dav3.immichframe.domain.system.BiometricHelper.openSecuritySettings(context)
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricNotSetupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
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
private fun PermissionStatusCard(
    status: com.dav3.immichframe.domain.model.PermissionCheckResult,
    checking: Boolean,
    onRecheck: () -> Unit,
) {
    val missingBlocking = status.missingBlocking
    val missingOptional = status.missingOptional

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (missingBlocking.isNotEmpty()) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.api_key_permissions),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onRecheck, enabled = !checking) {
                    Text(
                        if (checking) {
                            stringResource(R.string.checking)
                        } else {
                            stringResource(R.string.recheck_permissions)
                        },
                    )
                }
            }

            if (missingBlocking.isNotEmpty()) {
                Text(
                    stringResource(R.string.missing_required_perms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            // List each permission with its status icon
            RequiredPermission.entries.forEach { perm ->
                val st = status.statuses[perm]
                val (icon, tint) = when (st) {
                    PermissionStatus.Granted -> Icons.Default.Check to androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    PermissionStatus.Denied -> Icons.Default.Close to MaterialTheme.colorScheme.error
                    PermissionStatus.Unknown -> Icons.Default.Help to androidx.compose.ui.graphics.Color.Gray
                    null -> Icons.Default.Help to androidx.compose.ui.graphics.Color.Gray
                }
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            perm.scope,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            perm.featureName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            if (missingOptional.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.optional_perms_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
            )
        },
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

/**
 * Row showing a time label (e.g. "22:00") that opens a Material3 TimePicker
 * dialog when tapped. [minutes] is minutes-since-midnight (0–1439).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NightModeTimePicker(
    label: String,
    minutes: Int,
    onTimeSelected: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val locale = LocalLocale.current.platformLocale
    val hour = (minutes / 60) % 24
    val minute = minutes % 60
    val display = String.format(locale, "%02d:%02d", hour, minute)

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(display) },
        trailingContent = {
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.edit))
            }
        },
    )

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
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
