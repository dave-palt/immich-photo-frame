package com.dav3.immichframe.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.ui.onboarding.TourHost
import com.dav3.immichframe.ui.onboarding.TourScreen
import com.dav3.immichframe.ui.onboarding.rememberTourState
import com.dav3.immichframe.ui.onboarding.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSelectionScreen(
    onStartSlideshow: () -> Unit,
    onSettings: () -> Unit,
    viewModel: AlbumSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val tourState = rememberTourState()
    val completedSteps by viewModel.onboardingSteps.collectAsState()

    TourHost(
        screen = TourScreen.ALBUMS,
        completedSteps = completedSteps,
        onStepCompleted = viewModel::markStepCompleted,
        onSkipped = { },
        tourState = tourState,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.select_albums)) },
                    actions = {
                        IconButton(
                            onClick = onSettings,
                            modifier = Modifier.tourTarget("albums_settings_gear", tourState),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            },
            bottomBar = {
                if (state.selectedIds.isNotEmpty()) {
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.tourTarget("albums_start", tourState),
                    ) {
                        Text(
                            stringResource(R.string.albums_selected, state.selectedIds.size),
                            modifier = Modifier.padding(start = 16.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                viewModel.startSlideshow()
                                onStartSlideshow()
                            },
                            modifier = Modifier.padding(end = 16.dp),
                        ) { Text(stringResource(R.string.start_slideshow)) }
                    }
                }
            },
        ) { padding ->
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.noAlbumsAvailable -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.no_albums_available),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.no_albums_available_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.retry() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.retry() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp, padding.calculateTopPadding().value.toInt().dp, 16.dp, 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(padding)
                        .tourTarget("albums_grid", tourState),
                ) {
                    items(state.albums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            thumbnailUrl = viewModel.thumbnailUrl(album.thumbnailAssetId),
                            isSelected = album.id in state.selectedIds,
                            onClick = { viewModel.toggleAlbum(album.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    thumbnailUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Circle, contentDescription = null, tint = Color.Gray) }
            }

            // Selection indicator
            IconButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                )
            }

            // Album info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                Text(
                    "${album.assetCount} photos",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
