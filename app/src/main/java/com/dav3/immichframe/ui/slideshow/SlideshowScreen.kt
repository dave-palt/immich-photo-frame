package com.dav3.immichframe.ui.slideshow

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
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
    LaunchedEffect(state.currentIndex, s.adaptiveBackground) {
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
                        transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
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
                            BurnInPanImage(
                                url = viewModel.imageUrl(assetId),
                                contentScale = scale,
                                enabled = s.burnInProtection,
                                durationMs = s.intervalSeconds * 1000L,
                            )
                        }
                    }
                }
            }

            // Draggable clock overlay — placed anywhere via absolute offset
            if (s.showClock && currentTime.isNotEmpty()) {
                DraggableClock(
                    time = currentTime,
                    fontSize = s.clockSize,
                    position = s.clockPosition,
                    containerSize = containerSize,
                    burnInProtection = s.burnInProtection,
                    snapToGrid = s.clockSnapToGrid,
                    onPositionChanged = { normX, normY ->
                        viewModel.setClockPosition(ClockPosition(normX, normY))
                    },
                )
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
                    Text("${state.assets.size} photos", color = Color.White)
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
                    IconButton(onClick = { /* mute handled via settings */ }) {
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
private fun BurnInPanImage(
    url: String,
    contentScale: ContentScale,
    enabled: Boolean,
    durationMs: Long,
) {
    if (!enabled) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    // Slow pan + slight zoom over the display interval.
    // Alternates direction per image via hash of URL to avoid predictable patterns.
    val seed = url.hashCode()
    val panX = if (seed % 2 == 0) -1 else 1
    val panY = if ((seed / 2) % 2 == 0) -1 else 1

    val transition = rememberInfiniteTransition(label = "burnIn")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs.toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "panProgress",
    )

    // Scale 1.0 → 1.08, pan ±20px
    val scale = 1f + 0.08f * progress
    val dx = panX * 20f * progress
    val dy = panY * 20f * progress

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop, // always crop when panning
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = dx
                translationY = dy
            },
    )
}

@Composable
private fun DraggableClock(
    time: String,
    fontSize: Float,
    position: ClockPosition,
    containerSize: IntSize,
    burnInProtection: Boolean,
    snapToGrid: Boolean,
    onPositionChanged: (Float, Float) -> Unit,
) {
    // Default position: bottom-left with padding
    val isDefault = position.x < 0f
    val defaultNormX = 0.02f
    val defaultNormY = 0.85f
    val normX = if (isDefault) defaultNormX else position.x
    val normY = if (isDefault) defaultNormY else position.y

    // Snap-to-grid: grid cell size based on clock font size
    // Clock is roughly fontSize wide per character, ~5 chars in "HH:MM"
    val clockApproxWidth = fontSize * 3.5f + 80f // padding
    val clockApproxHeight = fontSize * 1.5f + 32f
    val gridStepX = (clockApproxWidth * 0.5f).coerceAtLeast(20f) // half clock width
    val gridStepY = (clockApproxHeight * 0.5f).coerceAtLeast(20f) // half clock height

    // Current pixel position from normalized coords
    var pxX by remember(position) { mutableFloatStateOf(normX * containerSize.width) }
    var pxY by remember(position) { mutableFloatStateOf(normY * containerSize.height) }

    // Update when container size changes
    LaunchedEffect(containerSize) {
        pxX = normX * containerSize.width
        pxY = normY * containerSize.height
    }

    // Burn-in drift: slow oscillation centered on the clock's position
    val driftX = if (burnInProtection) {
        val t = rememberInfiniteTransition(label = "clockDriftX")
        t.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(30_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "driftX",
        ).value
    } else {
        0f
    }
    val driftY = if (burnInProtection) {
        val t = rememberInfiniteTransition(label = "clockDriftY")
        t.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(45_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "driftY",
        ).value
    } else {
        0f
    }

    Surface(
        color = Color(0x80000000),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .offset { IntOffset(pxX.toInt(), pxY.toInt()) }
            .graphicsLayer {
                translationX = driftX
                translationY = driftY
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (snapToGrid) {
                            val snappedX = ((pxX + gridStepX / 2) / gridStepX).toInt() * gridStepX
                            val snappedY = ((pxY + gridStepY / 2) / gridStepY).toInt() * gridStepY
                            pxX = snappedX.coerceIn(0f, (containerSize.width - clockApproxWidth).coerceAtLeast(0f))
                            pxY = snappedY.coerceIn(0f, (containerSize.height - clockApproxHeight).coerceAtLeast(0f))
                        }
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            onPositionChanged(pxX / containerSize.width, pxY / containerSize.height)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    pxX = (pxX + dragAmount.x)
                        .coerceIn(0f, (containerSize.width - clockApproxWidth).coerceAtLeast(0f))
                    pxY = (pxY + dragAmount.y)
                        .coerceIn(0f, (containerSize.height - clockApproxHeight).coerceAtLeast(0f))
                }
            },
    ) {
        Text(
            time,
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
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

/** Downloads bitmap via Coil and extracts dominant color via Palette. */
private suspend fun extractDominantColor(
    context: android.content.Context,
    url: String,
): Color = try {
    val loader = coil3.ImageLoader(context)
    val request = coil3.request.ImageRequest.Builder(context)
        .data(url)
        .size(64)
        .build()
    val result = loader.execute(request)
    val image = result.image as? coil3.BitmapImage
    if (image != null) {
        val palette = androidx.palette.graphics.Palette.from(image.bitmap).generate()
        Color(palette.getDominantColor(0xFF000000.toInt()))
    } else {
        Color.Black
    }
} catch (_: Exception) {
    Color.Black
}
