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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = com.dav3.immichframe.domain.model.SlideshowSettings())
    val s = settings

    LaunchedEffect(Unit) { viewModel.load() }

    var isPaused by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentIndex, isPaused, s.intervalSeconds) {
        if (!isPaused && state.assets.isNotEmpty()) {
            delay(s.intervalSeconds * 1000L)
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

    // Keep screen on via view flag
    val view = LocalView.current
    LaunchedEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
    }

    Scaffold(containerColor = Color.Black) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> Text(state.error!!, color = Color.White)

                state.assets.isNotEmpty() -> {
                    val asset = state.assets[state.currentIndex]
                    val scale = if (s.fillMode == com.dav3.immichframe.domain.model.FillMode.COVER)
                        ContentScale.Crop else ContentScale.Fit

                    AnimatedContent(
                        targetState = asset.id,
                        transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
                        label = "slideshow"
                    ) { assetId ->
                        val url = viewModel.imageUrl(assetId)
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = scale,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Top bar: close + settings
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x80000000))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.assets.size} photos", color = Color.White)
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Default.NavigateBefore, "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.NavigateNext, "Next", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            // Pause/play
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                IconButton(onClick = { isPaused = !isPaused }) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (isPaused) "Play" else "Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
