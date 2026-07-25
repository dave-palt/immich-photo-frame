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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
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
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = SlideshowSettings())
    val s = settings

    LaunchedEffect(Unit) { viewModel.load() }

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

    // Track container size for clock position normalization
    var containerSize by remember { mutableStateOf(IntSize(0, 0)) }

    // Auto-advance with progress tracking
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(state.currentIndex, isPaused, s.intervalSeconds) {
        progress = 0f
        if (!isPaused && state.assets.isNotEmpty()) {
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
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Keep screen on
    LaunchedEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
    }

    // Clock
    var currentTime by remember { mutableStateOf("") }
    if (s.showClock) {
        LaunchedEffect(Unit) {
            while (true) {
                currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(30_000)
            }
        }
    }

    // Adaptive background — extract dominant color from current image
    var dominantColor by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current
    LaunchedEffect(state.currentIndex, s.adaptiveBackground, state.assets.size) {
        if (s.adaptiveBackground && state.assets.isNotEmpty()) {
            val asset = state.assets[state.currentIndex]
            if (asset.type != AssetType.VIDEO) {
                dominantColor = extractDominantColor(context, viewModel.imageUrl(asset.id))
            }
        } else {
            dominantColor = Color.Black
        }
    }

    Surface(color = Color.Black) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (s.adaptiveBackground) dominantColor else Color.Black)
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
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
                            )
                        } else {
                            KenBurnsImage(
                                url = viewModel.imageUrl(assetId),
                                contentScale = scale,
                                assetId = assetId,
                                photoAnimations = s.photoAnimations,
                                enabledAnims = s.enabledAnimations,
                                durationMs = s.intervalSeconds * 1000L,
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
                        clockDrift = s.photoAnimations,
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
                    modifier = Modifier.fillMaxWidth(),
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.photos_count, state.assets.size), color = Color.White)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onChangeAlbums) {
                        Icon(Icons.Default.PhotoLibrary, "Albums", tint = Color.White)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                    IconButton(onClick = onClose) {
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
                IconButton(onClick = { viewModel.next() }) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPaused = !isPaused }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            if (isPaused) "Play" else "Pause",
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
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoPlayer(
    asset: Asset,
    viewModel: SlideshowViewModel,
    muted: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(asset.id) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(viewModel.videoUrl(asset.id))))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose {
            exoPlayer.release()
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
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
