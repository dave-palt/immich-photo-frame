package com.dav3.immichframe.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType
import com.dav3.immichframe.ui.theme.ImmichFrameTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionScreen(
    onBack: () -> Unit,
    viewModel: MediaSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    MediaSelectionContent(
        state = state,
        thumbnailUrl = { viewModel.thumbnailUrl(it) },
        onBack = onBack,
        onToggleNewItemsShown = { viewModel.toggleNewItemsShown() },
        onSelectAll = { viewModel.selectAll() },
        onSelectNone = { viewModel.selectNone() },
        onToggleAsset = { viewModel.toggleAsset(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionContent(
    state: MediaSelectionUiState,
    thumbnailUrl: (String) -> String,
    onBack: () -> Unit,
    onToggleNewItemsShown: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onToggleAsset: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.media_selection_title))
                        Text(
                            stringResource(
                                R.string.media_selection_count,
                                state.shownCount,
                                state.totalCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
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
                .padding(padding),
        ) {
            // Mode toggle + select all/none bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.media_selection_new_items_default),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.newItemsShown,
                    onCheckedChange = { onToggleNewItemsShown() },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.media_selection_select_all))
                }
                TextButton(onClick = onSelectNone) {
                    Text(stringResource(R.string.media_selection_select_none))
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
                }

                state.assets.isEmpty() && !state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No photos in this album",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    ) {
                        items(
                            items = state.assets,
                            key = { it.id },
                        ) { asset ->
                            MediaThumbnail(
                                asset = asset,
                                url = thumbnailUrl(asset.id),
                                isShown = state.isShown(asset.id),
                                onClick = { onToggleAsset(asset.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    asset: Asset,
    url: String,
    isShown: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                // Dim the thumbnail itself when hidden — not just an overlay icon
                .alpha(if (isShown) 1f else 0.3f),
        )

        // Dim overlay when hidden (makes it unmistakable)
        if (!isShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
            )
        }

        // Selection indicator (top-end) — different icons for shown vs hidden
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        ) {
            if (isShown) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.media_selection_shown),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.media_selection_hidden),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // Video indicator (bottom-start) — dark pill background for prominence
        if (asset.type == AssetType.VIDEO) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
                Text(
                    "VIDEO",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 2.dp, end = 6.dp),
                )
            }
        }
    }
}

// region Previews

private val demoAssets = listOf(
    Asset(id = "asset-1", type = AssetType.IMAGE),
    Asset(id = "asset-2", type = AssetType.IMAGE),
    Asset(id = "asset-3", type = AssetType.VIDEO),
    Asset(id = "asset-4", type = AssetType.IMAGE),
    Asset(id = "asset-5", type = AssetType.IMAGE),
    Asset(id = "asset-6", type = AssetType.IMAGE),
)

private fun demoThumbnailUrl(id: String) = "file:///android_asset/demo/${id.replace("asset-", "photo_")}.jpg"

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 640)
@Composable
private fun MediaSelectionContentPreview_AllShown() {
    ImmichFrameTheme {
        MediaSelectionContent(
            state = MediaSelectionUiState(
                assets = demoAssets,
                newItemsShown = true,
            ),
            thumbnailUrl = ::demoThumbnailUrl,
            onBack = {},
            onToggleNewItemsShown = {},
            onSelectAll = {},
            onSelectNone = {},
            onToggleAsset = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 640)
@Composable
private fun MediaSelectionContentPreview_SomeHidden() {
    ImmichFrameTheme {
        MediaSelectionContent(
            state = MediaSelectionUiState(
                assets = demoAssets,
                newItemsShown = true,
                toggledIds = setOf("asset-3", "asset-5"),
            ),
            thumbnailUrl = ::demoThumbnailUrl,
            onBack = {},
            onToggleNewItemsShown = {},
            onSelectAll = {},
            onSelectNone = {},
            onToggleAsset = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 640)
@Composable
private fun MediaSelectionContentPreview_Loading() {
    ImmichFrameTheme {
        MediaSelectionContent(
            state = MediaSelectionUiState(isLoading = true),
            thumbnailUrl = ::demoThumbnailUrl,
            onBack = {},
            onToggleNewItemsShown = {},
            onSelectAll = {},
            onSelectNone = {},
            onToggleAsset = {},
        )
    }
}

// endregion
