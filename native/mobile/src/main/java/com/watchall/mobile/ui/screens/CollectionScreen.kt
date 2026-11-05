package com.watchall.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.MediaSummary
import com.watchall.core.vm.CollectionViewModel
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.PosterCard
import com.watchall.mobile.ui.components.RoundIconButton
import com.watchall.mobile.ui.theme.WatchColors

@Composable
fun CollectionScreen(
    viewModel: CollectionViewModel,
    onBack: () -> Unit,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val row = viewModel.row
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 6
        }
    }

    LaunchedEffect(nearEnd, state.page, state.totalPages, state.loading, state.loadingMore) {
        if (nearEnd && !state.loading && !state.loadingMore && state.page < state.totalPages) {
            viewModel.loadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WatchColors.DeepInk, WatchColors.Ink),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(WatchColors.Cyan.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topInset + 8.dp,
                bottom = 34.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            onClick = onBack,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (state.totalPages > 1) {
                                "${state.items.size} titles · page ${state.page} of ${state.totalPages}"
                            } else {
                                "${state.items.size} titles"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = WatchColors.TextMuted,
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    Text(
                        text = row.eyebrow.ifBlank { "WATCHALL COLLECTION" }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = WatchColors.Cyan,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = WatchColors.Text,
                        letterSpacing = (-0.8).sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(WatchColors.Cyan.copy(alpha = 0.7f), Color.Transparent),
                                ),
                            ),
                    )
                }
            }

            when {
                state.loading && state.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "loading",
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = WatchColors.Cyan, strokeWidth = 2.5.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Curating this collection…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WatchColors.TextMuted,
                            )
                        }
                    }
                }

                state.error != null && state.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "error",
                ) {
                    ErrorPanel(
                        title = "Collection unavailable",
                        message = state.error.orEmpty(),
                        primaryLabel = "Try again",
                        onPrimary = viewModel::retry,
                    )
                }

                state.items.isEmpty() -> item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "empty",
                ) {
                    Text(
                        text = "Nothing is available in this collection yet.",
                        modifier = Modifier.padding(vertical = 48.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchColors.TextMuted,
                    )
                }

                else -> {
                    itemsIndexed(
                        items = state.items,
                        key = { _, media -> media.key },
                    ) { index, media ->
                        PosterCard(
                            media = media,
                            onClick = { onOpenDetails(media) },
                            width = null,
                            rank = if (row.ranked && index < 10) index + 1 else null,
                        )
                    }
                    if (state.error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "soft-error") {
                            Text(
                                text = state.error.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = WatchColors.Coral,
                            )
                        }
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = WatchColors.Cyan,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = "Loading more…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WatchColors.TextMuted,
                                )
                            }
                        }
                    } else if (state.page < state.totalPages) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load-more") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Button(
                                    onClick = viewModel::loadMore,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        contentColor = WatchColors.Text,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
                                ) {
                                    Text("Load more", fontWeight = FontWeight.SemiBold)
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(17.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
