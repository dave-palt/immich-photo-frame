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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Album
import com.dav3.immichframe.ui.onboarding.TourHost
import com.dav3.immichframe.ui.onboarding.TourScreen
import com.dav3.immichframe.ui.onboarding.TourState
import com.dav3.immichframe.ui.onboarding.rememberTourState
import com.dav3.immichframe.ui.onboarding.tourTarget
import com.dav3.immichframe.ui.theme.ImmichFrameTheme

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
        AlbumSelectionContent(
            state = state,
            thumbnailUrl = { assetId -> viewModel.thumbnailUrl(assetId) },
            onToggleAlbum = viewModel::toggleAlbum,
            onRetry = viewModel::retry,
            onStartSlideshow = {
                viewModel.startSlideshow()
                onStartSlideshow()
            },
            onSettings = onSettings,
            tourState = tourState,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumSelectionContent(
    state: AlbumSelectionUiState,
    thumbnailUrl: (String?) -> String?,
    onToggleAlbum: (String) -> Unit,
    onRetry: () -> Unit,
    onStartSlideshow: () -> Unit,
    onSettings: () -> Unit,
    tourState: TourState? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_albums)) },
                actions = {
                    IconButton(
                        onClick = onSettings,
                        modifier = tourState?.let { Modifier.tourTarget("albums_settings_gear", it) }
                            ?: Modifier,
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
                    modifier = tourState?.let { Modifier.tourTarget("albums_start", it) }
                        ?: Modifier,
                ) {
                    Text(
                        stringResource(R.string.albums_selected, state.selectedIds.size),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onStartSlideshow,
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
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRetry) {
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
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding().value.toInt().dp, 16.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = tourState?.let { Modifier.tourTarget("albums_grid", it).padding(padding) }
                    ?: Modifier.padding(padding),
            ) {
                items(state.albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        thumbnailUrl = thumbnailUrl(album.thumbnailAssetId),
                        isSelected = album.id in state.selectedIds,
                        onClick = { onToggleAlbum(album.id) },
                    )
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

// region Previews

private val demoAlbums = listOf(
    Album("1", "Summer Vacation 2025", 247, "asset-1"),
    Album("2", "Family Reunion", 89, "asset-2"),
    Album("3", "Nature & Hiking", 156, "asset-3"),
    Album("4", "City Life", 73, "asset-4"),
    Album("5", "Pets", 42, "asset-5"),
    Album("6", "Food & Cooking", 118, "asset-6"),
)

private val demoThumbnails: (String?) -> String? = { assetId ->
    assetId?.let { "file:///android_asset/demo/${it.replace("asset-", "photo_")}.jpg" }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlbumSelectionContentPreview_AlbumsLoaded() {
    ImmichFrameTheme {
        AlbumSelectionContent(
            state = AlbumSelectionUiState(
                albums = demoAlbums,
                selectedIds = setOf("1", "3"),
            ),
            thumbnailUrl = demoThumbnails,
            onToggleAlbum = {},
            onRetry = {},
            onStartSlideshow = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlbumSelectionContentPreview_Loading() {
    ImmichFrameTheme {
        AlbumSelectionContent(
            state = AlbumSelectionUiState(isLoading = true),
            thumbnailUrl = { null },
            onToggleAlbum = {},
            onRetry = {},
            onStartSlideshow = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlbumSelectionContentPreview_NoAlbums() {
    ImmichFrameTheme {
        AlbumSelectionContent(
            state = AlbumSelectionUiState(noAlbumsAvailable = true),
            thumbnailUrl = { null },
            onToggleAlbum = {},
            onRetry = {},
            onStartSlideshow = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlbumSelectionContentPreview_Error() {
    ImmichFrameTheme {
        AlbumSelectionContent(
            state = AlbumSelectionUiState(error = "Could not reach the Immich server"),
            thumbnailUrl = { null },
            onToggleAlbum = {},
            onRetry = {},
            onStartSlideshow = {},
            onSettings = {},
        )
    }
}

// endregion
