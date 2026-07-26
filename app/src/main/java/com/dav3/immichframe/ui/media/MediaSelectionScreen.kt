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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.dav3.immichframe.R
import com.dav3.immichframe.domain.model.Asset
import com.dav3.immichframe.domain.model.AssetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionScreen(
    onBack: () -> Unit,
    viewModel: MediaSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

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
                    onCheckedChange = { viewModel.toggleNewItemsShown() },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.selectAll() }) {
                    Text(stringResource(R.string.media_selection_select_all))
                }
                TextButton(onClick = { viewModel.selectNone() }) {
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
                                url = viewModel.thumbnailUrl(asset.id),
                                isShown = state.isShown(asset.id),
                                onClick = { viewModel.toggleAsset(asset.id) },
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
                .height(120.dp),
        )

        // Dim overlay when hidden
        if (!isShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
            )
        }

        // Selection indicator (top-end)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            if (isShown) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.media_selection_shown),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.media_selection_hidden),
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Video indicator (bottom-start)
        if (asset.type == AssetType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
