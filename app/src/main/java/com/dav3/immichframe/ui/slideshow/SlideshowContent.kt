package com.dav3.immichframe.ui.slideshow

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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetExif
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.domain.model.ClockFormat
import com.dav3.immichframe.domain.model.ClockPosition
import com.dav3.immichframe.domain.model.FillMode
import com.dav3.immichframe.domain.model.SlideshowSettings
import com.dav3.immichframe.ui.onboarding.TourState
import com.dav3.immichframe.ui.onboarding.tourTarget
import com.dav3.immichframe.ui.theme.ImmichFrameTheme

/**
 * Pure rendering layer for the slideshow. Takes plain state + lambdas — no
 * ViewModel, no lifecycle, no ExoPlayer. This makes it previewable for
 * screenshot generation.
 *
 * Video rendering is delegated to [videoContent] slot (ExoPlayer can't run
 * on JVM/Robolectric). The update status icon is delegated to [updateIcon]
 * slot for the same reason.
 */
@Composable
fun SlideshowContent(
    state: SlideshowUiState,
    settings: SlideshowSettings,
    imageUrl: (Asset) -> String,
    controlsVisible: Boolean,
    isPaused: Boolean,
    progress: Float,
    nightActive: Boolean,
    currentTime: String,
    containerSize: IntSize,
    adaptiveBrush: Brush?,
    tourState: TourState?,
    onToggleControls: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleMute: () -> Unit,
    onSettings: () -> Unit,
    onChangeAlbums: () -> Unit,
    onMediaSelection: () -> Unit,
    onSetClockPosition: (Float, Float) -> Unit,
    onImageLoaded: () -> Unit,
    onContainerSizeChanged: (IntSize) -> Unit,
    videoContent: @Composable (Asset) -> Unit = {},
    updateIcon: @Composable () -> Unit = {},
    showUpdateDialog: Boolean = false,
    updateMessage: String = "",
    onInstallUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    launcherMode: Boolean = false,
    onOpenOtherLauncher: () -> Unit = {},
) {
    val s = settings

    Surface(color = Color.Black) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(adaptiveBrush ?: Brush.verticalGradient(listOf(Color.Black, Color.Black)))
                .onSizeChanged { onContainerSizeChanged(it) }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
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
                            videoContent(currentAsset)
                        } else if (currentAsset != null) {
                            KenBurnsImage(
                                url = imageUrl(currentAsset),
                                contentScale = scale,
                                assetId = assetId,
                                photoAnimations = s.photoAnimations,
                                enabledAnims = s.enabledAnimations,
                                durationMs = s.intervalSeconds * 1000L,
                                onImageLoaded = { onImageLoaded() },
                            )
                        }
                    }
                }
            }

            // Photo metadata overlay (date, location, description, tags)
            if (!nightActive && !state.isLoading && state.error == null && state.assets.isNotEmpty()) {
                val currentAsset = state.assets[state.currentIndex]
                PhotoMetadataOverlay(
                    asset = currentAsset,
                    settings = s,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            // Draggable clock overlay
            if (s.showClock && currentTime.isNotEmpty() && containerSize.width > 0) {
                Box(modifier = Modifier.align(Alignment.TopStart)) {
                    DraggableClock(
                        time = currentTime,
                        fontSize = s.clockSize,
                        position = s.clockPosition,
                        containerSize = containerSize,
                        clockDrift = true,
                        snapToGrid = s.clockSnapToGrid,
                        onPositionChanged = { normX, normY ->
                            onSetClockPosition(normX, normY)
                        },
                    )
                }
            }

            // Progress bar
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

            // Top bar
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
                    Text(
                        stringResource(R.string.photos_count, state.currentIndex + 1, state.assets.size),
                        color = Color.White,
                    )
                    IconButton(
                        onClick = onMediaSelection,
                        modifier = tourState?.let { Modifier.tourTarget("slideshow_media_selection", it) }
                            ?: Modifier,
                    ) {
                        Icon(Icons.Default.GridView, "Media selection", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    updateIcon()
                    if (launcherMode) {
                        IconButton(onClick = onOpenOtherLauncher) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                "Switch to another launcher",
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(
                        onClick = onChangeAlbums,
                        modifier = tourState?.let { Modifier.tourTarget("slideshow_albums", it) }
                            ?: Modifier,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, "Albums", tint = Color.White)
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = tourState?.let { Modifier.tourTarget("slideshow_settings_gear", it) }
                            ?: Modifier,
                    ) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
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
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                IconButton(
                    onClick = onNext,
                    modifier = tourState?.let { Modifier.tourTarget("slideshow_next", it) } ?: Modifier,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        "Next",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
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
                        onClick = onTogglePause,
                        modifier = tourState?.let { Modifier.tourTarget("slideshow_pause", it) } ?: Modifier,
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            if (isPaused) "Play slideshow" else "Pause slideshow",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (s.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Update install dialog
            if (showUpdateDialog) {
                AlertDialog(
                    onDismissRequest = onDismissUpdate,
                    title = { Text(stringResource(R.string.update_available)) },
                    text = { Text(updateMessage) },
                    confirmButton = {
                        TextButton(onClick = onInstallUpdate) {
                            Text(stringResource(R.string.install), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissUpdate) {
                            Text(stringResource(R.string.later))
                        }
                    },
                )
            }
        }
    }
}

// region Previews

private val demoAsset = Asset(
    id = "asset-1",
    type = AssetType.IMAGE,
    exif = AssetExif(
        dateTimeOriginal = "2024-07-15T14:30:00.000Z",
        description = "Sunset at the beach",
        city = "Amalfi",
        country = "Italy",
    ),
    tags = listOf("vacation", "summer"),
)
private val demoState = SlideshowUiState(
    assets = listOf(demoAsset),
    currentIndex = 0,
)
private val demoImageSize = IntSize(360, 640)
private fun demoImageUrl(asset: Asset) = "file:///android_asset/demo/${asset.id.replace("asset-", "photo_")}.jpg"

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_PhotoContain() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(fillMode = FillMode.CONTAIN),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_PhotoCover() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(fillMode = FillMode.COVER),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_WithClock() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(
                showClock = true,
                clockSize = 48f,
                clockFormat = ClockFormat.H24,
                clockPosition = ClockPosition(x = 0.5f, y = 0.25f),
            ),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = false,
            currentTime = "14:30",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_ControlsVisible() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(),
            imageUrl = ::demoImageUrl,
            controlsVisible = true,
            isPaused = false,
            progress = 0.42f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_NightMode() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(nightMode = true),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = true,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_Paused() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(),
            imageUrl = ::demoImageUrl,
            controlsVisible = true,
            isPaused = true,
            progress = 0f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_Loading() {
    ImmichFrameTheme {
        SlideshowContent(
            state = SlideshowUiState(isLoading = true),
            settings = SlideshowSettings(),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 640)
@Composable
private fun SlideshowContentPreview_WithMetadata() {
    ImmichFrameTheme {
        SlideshowContent(
            state = demoState,
            settings = SlideshowSettings(
                showPhotoDate = true,
                showLocation = true,
                showDescription = true,
                showTags = true,
            ),
            imageUrl = ::demoImageUrl,
            controlsVisible = false,
            isPaused = false,
            progress = 0f,
            nightActive = false,
            currentTime = "",
            containerSize = demoImageSize,
            adaptiveBrush = null,
            tourState = null,
            onToggleControls = {},
            onPrevious = {},
            onNext = {},
            onTogglePause = {},
            onToggleMute = {},
            onSettings = {},
            onChangeAlbums = {},
            onMediaSelection = {},
            onSetClockPosition = { _, _ -> },
            onImageLoaded = {},
            onContainerSizeChanged = {},
        )
    }
}

// endregion
