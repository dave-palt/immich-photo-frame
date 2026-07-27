package com.dav3.immichframe.ui.slideshow

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.ClockFormat
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.domain.system.openOtherLauncher
import com.dav3.immichframe.ui.onboarding.TourHost
import com.dav3.immichframe.ui.onboarding.TourScreen
import com.dav3.immichframe.ui.onboarding.TourSteps
import com.dav3.immichframe.ui.onboarding.rememberTourState
import com.dav3.immichframe.ui.onboarding.tourTarget
import com.dav3.immichframe.ui.update.UpdateViewModel
import com.dav3.immichframe.util.extractDominantColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onChangeAlbums: () -> Unit,
    onMediaSelection: () -> Unit = {},
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = SlideshowSettings())
    val s = settings

    val tourState = rememberTourState()
    val completedSteps by viewModel.onboardingSteps.collectAsState()
    val tourActive = remember(completedSteps) {
        TourSteps.forScreen(TourScreen.SLIDESHOW).any { it.id !in completedSteps }
    }

    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.updateState.collectAsState()
    val updateDismissed by updateVm.updateDismissed.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    val authTitle = stringResource(R.string.biometric_auth_title)
    val authSubtitleMedia = stringResource(R.string.biometric_auth_subtitle_media)

    LaunchedEffect(Unit) { viewModel.load() }

    // Album deleted on server — bounce back to album selection so the user
    // can pick again. Only fires once per load() that sets the flag.
    LaunchedEffect(state.albumGone) {
        if (state.albumGone) onChangeAlbums()
    }

    // Immersive fullscreen
    val view = LocalView.current
    DisposableEffect(s.fullscreen) {
        val window = (view.context as? Activity)?.window
        if (s.fullscreen && window != null) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        } else if (window != null) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
        onDispose {
            window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    var isPaused by remember { mutableStateOf(false) }
    var isVideoPaused by remember { mutableStateOf(false) }

    // Track container size for clock position normalization
    var containerSize by remember { mutableStateOf(IntSize(0, 0)) }

    // Night Mode active state — polled every few seconds, used to:
    // 1) pause the auto-advance timer below, and
    // 2) render a black screen instead of photo/video content.
    // A short poll interval keeps the transition snappy when settings change
    // or when crossing the window boundary. The 60s tick used previously meant
    // up to a full minute of latency after returning from Settings.
    var nightActive by remember { mutableStateOf(false) }
    LaunchedEffect(s.nightMode, s.nightModeStart, s.nightModeEnd) {
        if (!s.nightMode) {
            nightActive = false
            return@LaunchedEffect
        }
        while (true) {
            val cal = java.util.Calendar.getInstance()
            val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                cal.get(java.util.Calendar.MINUTE)
            nightActive = s.isNightModeActive(nowMin)
            kotlinx.coroutines.delay(5_000L)
        }
    }

    // Auto-advance with progress tracking
    // For images: the timer starts only once the image has finished decoding
    // (coil onState = Success/Error). This prevents the timer from counting
    // down during a slow decode (e.g. a 77MB GIF) — the user sees the photo
    // for the full interval, not whatever's left after decode.
    // For videos: VideoPlayer drives advancing (calls viewModel.next() when
    // the video ends, not the interval timer).
    // Exception: if the video is manually paused, the timer takes over.
    var progress by remember { mutableStateOf(0f) }
    // false = image still decoding; true = video or image ready.
    // Reset to false on every index change so the timer waits for decode.
    var imageReady by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentIndex) {
        // Videos are immediately "ready" — ExoPlayer handles its own timeline.
        imageReady = state.assets.getOrNull(state.currentIndex)?.type == AssetType.VIDEO
    }
    LaunchedEffect(state.currentIndex, isPaused, isVideoPaused, s.intervalSeconds, nightActive, imageReady) {
        progress = 0f
        if (!isPaused && !nightActive && imageReady && state.assets.isNotEmpty()) {
            val currentAsset = state.assets[state.currentIndex]
            if (currentAsset.type == AssetType.VIDEO && !isVideoPaused) {
                // Video playing normally — VideoPlayer calls viewModel.next() on end
                return@LaunchedEffect
            }
            val total = s.intervalSeconds * 1000L
            val tick = 50L
            var elapsed = 0L
            while (elapsed < total) {
                delay(tick)
                elapsed += tick
                progress = elapsed.toFloat() / total
            }
            viewModel.next()
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }

    // Force controls visible during onboarding tour, suppress auto-hide
    LaunchedEffect(tourActive) {
        if (tourActive) controlsVisible = true
    }

    LaunchedEffect(controlsVisible, tourActive) {
        if (controlsVisible && !tourActive) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Keep screen on
    LaunchedEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
    }

    // Night Mode brightness application — dim the screen when nightActive.
    // Uses per-window brightness (WindowManager.LayoutParams.screenBrightness).
    // BRIGHTNESS_OVERRIDE_NONE (-1f) = defer to the system/user brightness.
    // nightActive is declared + polled above, alongside the auto-advance timer.
    DisposableEffect(nightActive, s.nightModeBrightness) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = if (nightActive) {
                s.nightModeBrightness / 100f
            } else {
                android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }
        onDispose {
            // Restore system brightness when leaving the slideshow
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val params = window.attributes
                params.screenBrightness =
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    // Clock
    var currentTime by remember { mutableStateOf("") }
    if (s.showClock) {
        LaunchedEffect(s.clockSeconds, s.clockFormat) {
            while (true) {
                val hourToken = if (s.clockFormat == ClockFormat.H12) "hh" else "HH"
                val secToken = if (s.clockSeconds) ":ss" else ""
                val amPm = if (s.clockFormat == ClockFormat.H12) " a" else ""
                val pattern = "$hourToken:mm$secToken$amPm"
                currentTime = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
                // With seconds: update every 1s. Without: every 10s is enough
                // (the minute changes at most once per 60s, and the 10s tick
                // ensures we roll over promptly without drifting).
                delay(if (s.clockSeconds) 1_000L else 10_000L)
            }
        }
    }

    // Adaptive background — extract dominant color from current image
    var dominantColor by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current
    LaunchedEffect(state.currentIndex, s.adaptiveBackground, state.assets.size) {
        if (s.adaptiveBackground && state.assets.isNotEmpty()) {
            val asset = state.assets[state.currentIndex]
            // Always extract from the small JPEG thumbnail — it's fast for
            // all asset types (GIFs, videos, regular images) since Coil only
            // needs a 128px bitmap for Palette.
            dominantColor = extractDominantColor(context, viewModel.thumbnailUrl(asset.id))
        } else {
            dominantColor = Color.Black
        }
    }

    TourHost(
        screen = TourScreen.SLIDESHOW,
        completedSteps = completedSteps,
        onStepCompleted = viewModel::markStepCompleted,
        onSkipped = { },
        tourState = tourState,
    ) {
        Surface(color = Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (s.adaptiveBackground && !nightActive) dominantColor else Color.Black)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // Night Mode active — black screen, no photo/video rendering.
                    // The timer (above) is also paused via the nightActive flag.
                    nightActive -> { /* pure black surface, nothing to render */ }

                    state.isLoading -> CircularProgressIndicator()

                    state.error != null -> Text(state.error!!, color = Color.White)

                    state.assets.isNotEmpty() -> {
                        val asset = state.assets[state.currentIndex]
                        val scale = if (s.fillMode == FillMode.COVER) {
                            ContentScale.Crop
                        } else {
                            ContentScale.Fit
                        }

                        AnimatedContent(
                            targetState = asset.id,
                            transitionSpec = {
                                val ms = (s.transitionSeconds * 1000).toInt()
                                fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                            },
                            label = "slideshow",
                        ) { assetId ->
                            val currentAsset = state.assets.find { it.id == assetId }
                            if (currentAsset?.type == AssetType.VIDEO) {
                                VideoPlayer(
                                    asset = currentAsset,
                                    viewModel = viewModel,
                                    muted = s.muted,
                                    isSlideshowPaused = isPaused,
                                    isVideoPaused = isVideoPaused,
                                    fillMode = s.fillMode,
                                )
                            } else if (currentAsset != null) {
                                KenBurnsImage(
                                    url = viewModel.imageUrl(currentAsset),
                                    contentScale = scale,
                                    assetId = assetId,
                                    photoAnimations = s.photoAnimations,
                                    enabledAnims = s.enabledAnimations,
                                    durationMs = s.intervalSeconds * 1000L,
                                    onImageLoaded = { imageReady = true },
                                )
                            }
                        }
                    }
                }

                // Draggable clock overlay — positioned from top-left via absolute offset
                if (s.showClock && currentTime.isNotEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        DraggableClock(
                            time = currentTime,
                            fontSize = s.clockSize,
                            position = s.clockPosition,
                            containerSize = containerSize,
                            clockDrift = true,
                            snapToGrid = s.clockSnapToGrid,
                            onPositionChanged = { normX, normY ->
                                viewModel.setClockPosition(ClockPosition(normX, normY))
                            },
                        )
                    }
                }

                // Progress bar (bottom, thin line) — shows when controls visible
                AnimatedVisibility(
                    visible = controlsVisible && !isPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        color = Color.White,
                        trackColor = Color(0x33FFFFFF),
                    )
                }

                // Top bar: photo count + mute + albums + settings + close
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x80000000))
                            .displayCutoutPadding()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.photos_count, state.currentIndex + 1, state.assets.size), color = Color.White)
                        // Media selection — biometric-gated grid to pick which
                        // photos are shown in the slideshow
                        val biometric = com.dav3.immichframe.ui.components.rememberBiometricLauncher()
                        var showBioNotSetup by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                biometric.launch(
                                    title = authTitle,
                                    subtitle = authSubtitleMedia,
                                    onNotSetup = { showBioNotSetup = true },
                                    onSuccess = { onMediaSelection() },
                                )
                            },
                            modifier = Modifier.tourTarget("slideshow_media_selection", tourState),
                        ) {
                            Icon(Icons.Default.GridView, "Media selection", tint = Color.White)
                        }
                        if (showBioNotSetup) {
                            AlertDialog(
                                onDismissRequest = { showBioNotSetup = false },
                                title = { Text(stringResource(R.string.biometric_not_setup_title)) },
                                text = { Text(stringResource(R.string.biometric_not_setup_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showBioNotSetup = false
                                        com.dav3.immichframe.domain.system.BiometricHelper
                                            .openSecuritySettings(context)
                                    }) { Text(stringResource(R.string.open_settings)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showBioNotSetup = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // Update status icon — shows checking/downloading/ready states.
                        // Clicking opens the install dialog (only when download is ready).
                        // During the tour, force-show a placeholder so the coachmark
                        // has a target (the icon normally only appears when an update
                        // is available, which would leave the tour step highlighting
                        // nothing).
                        Box(modifier = Modifier.tourTarget("slideshow_update", tourState)) {
                            UpdateStatusIcon(
                                state = updateState,
                                onClick = {
                                    if (updateState.available && !updateState.downloading && updateState.downloadedApkPath != null) {
                                        updateVm.resetDismissed()
                                        showUpdateDialog = true
                                    }
                                },
                                forceVisible = tourState.activeTargetKey == "slideshow_update",
                            )
                        }
                        if (s.launcherMode) {
                            val context = LocalContext.current
                            IconButton(onClick = { openOtherLauncher(context) }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Switch to another launcher", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = onChangeAlbums,
                            modifier = Modifier.tourTarget("slideshow_albums", tourState),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, "Albums", tint = Color.White)
                        }
                        IconButton(
                            onClick = onSettings,
                            modifier = Modifier.tourTarget("slideshow_settings_gear", tourState),
                        ) {
                            Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.tourTarget("slideshow_close", tourState),
                        ) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }
                }

                // Left/right nav
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    IconButton(onClick = { viewModel.previous() }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.tourTarget("slideshow_next", tourState),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }

                // Pause/play + mute (bottom-center)
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .displayCutoutPadding()
                            .padding(vertical = 24.dp),
                    ) {
                        IconButton(
                            onClick = { isPaused = !isPaused },
                            modifier = Modifier.tourTarget("slideshow_pause", tourState),
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                if (isPaused) "Play slideshow" else "Pause slideshow",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = { viewModel.setMuted(!s.muted) }) {
                            Icon(
                                if (s.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
                // Big centered video play/pause overlay — only for videos
                if (isVideoPaused) {
                    val currentAsset = state.assets.getOrNull(state.currentIndex)
                    if (currentAsset?.type == AssetType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color(0x80000000), shape = androidx.compose.foundation.shape.CircleShape)
                                .size(96.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { isVideoPaused = false })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                "Play video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }

                // Update install dialog — triggered by the update status icon.
                if (showUpdateDialog && updateState.available && !updateState.downloading && updateState.downloadedApkPath != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showUpdateDialog = false
                            updateVm.dismissUpdate()
                        },
                        title = { Text(stringResource(R.string.update_available)) },
                        text = {
                            Text(stringResource(R.string.update_message, updateState.newVersion.take(14)))
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUpdateDialog = false
                                updateVm.installUpdate()
                            }) {
                                Text(stringResource(R.string.install), color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showUpdateDialog = false
                                updateVm.dismissUpdate()
                            }) {
                                Text(stringResource(R.string.later))
                            }
                        },
                    )
                }

                // Video paused indicator (small, centered) — tap to resume
                if (!isVideoPaused) {
                    val currentAsset = state.assets.getOrNull(state.currentIndex)
                    if (currentAsset?.type == AssetType.VIDEO && controlsVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { isVideoPaused = true })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                "Pause video",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val TAG_VIDEO = "VideoPlayer"

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayer(
    asset: Asset,
    viewModel: SlideshowViewModel,
    muted: Boolean,
    isSlideshowPaused: Boolean,
    isVideoPaused: Boolean,
    fillMode: FillMode,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(asset.id) {
        val url = viewModel.videoUrl(asset.id)
        android.util.Log.d(TAG_VIDEO, "Loading video: assetId=${asset.id} url=$url")
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        exoPlayer.prepare()
        onDispose {
            exoPlayer.release()
        }
    }

    // React to pause/play state
    DisposableEffect(asset.id, isSlideshowPaused, isVideoPaused) {
        when {
            isSlideshowPaused -> {
                // Slideshow paused: video loops, no auto-advance
                exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                exoPlayer.playWhenReady = true
            }
            isVideoPaused -> {
                // Video manually paused: slideshow timer takes over
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.playWhenReady = false
            }
            else -> {
                // Normal: play once, advance on end
                exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
                exoPlayer.playWhenReady = true
            }
        }
        onDispose { }
    }

    // Player listener: logging + advance on end (only when not paused)
    DisposableEffect(asset.id, isSlideshowPaused, isVideoPaused) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                android.util.Log.d(TAG_VIDEO, "State changed: $stateName (asset=${asset.id})")
                if (playbackState == Player.STATE_ENDED && !isSlideshowPaused && !isVideoPaused) {
                    viewModel.next()
                }
            }

            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                if (error != null) {
                    android.util.Log.e(TAG_VIDEO, "Playback error: ${error.errorCodeName}", error)
                    android.util.Log.e(TAG_VIDEO, "Cause: ${error.cause?.javaClass?.name}: ${error.cause?.message}")
                    // Skip to next on error so slideshow isn't stuck
                    viewModel.next()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(muted) {
        exoPlayer.volume = if (muted) 0f else 1f
        onDispose { }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = if (fillMode == FillMode.COVER) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Update status icon for the slideshow top bar.
 * Shows different states:
 * - checking: spinner
 * - downloading: spinner with percentage overlay (tap → tooltip with ETA)
 * - ready (downloaded): SystemUpdate icon with accent color (clickable → opens dialog)
 * - error: SystemUpdate icon with red tint
 * - idle: not shown (unless forceVisible for tour)
 */
@Composable
private fun UpdateStatusIcon(
    state: com.dav3.immichframe.data.update.UpdateState,
    onClick: () -> Unit,
    forceVisible: Boolean = false,
) {
    var showTooltip by remember { mutableStateOf(false) }

    when {
        state.checking -> {
            IconButton(onClick = {}) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
        }
        state.downloading -> {
            IconButton(onClick = { showTooltip = !showTooltip }) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                    Text(
                        text = "${(state.downloadProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
            if (showTooltip) {
                val eta = state.downloadEtaSeconds
                val etaText = when {
                    eta <= 0 -> "Calculating…"
                    eta < 60 -> "${eta}s left"
                    else -> "${eta / 60}m ${eta % 60}s left"
                }
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopCenter,
                    onDismissRequest = { showTooltip = false },
                ) {
                    Surface(
                        color = Color(0xCC000000),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "Downloading update ($etaText)",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        state.error != null -> {
            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.SystemUpdate,
                    "Update check failed",
                    tint = Color.Red,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        state.available && state.downloadedApkPath != null -> {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.SystemUpdate,
                    "Update ready — tap to install",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        forceVisible -> {
            // Tour placeholder — no real update, but show the icon so the
            // coachmark has a visible target to highlight.
            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.SystemUpdate,
                    "Update indicator",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
