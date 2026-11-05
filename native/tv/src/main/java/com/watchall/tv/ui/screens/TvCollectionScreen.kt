package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.MediaSummary
import com.watchall.core.vm.CollectionViewModel
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvGridSkeleton
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.theme.TvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

@Composable
fun TvCollectionScreen(
    viewModel: CollectionViewModel,
    onBack: () -> Unit,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val row = viewModel.row
    // Trending/curated rows are a fixed list on TMDB, so only discover-backed
    // rows can be narrowed with a genre or year.
    val filtersAvailable = row.query != null
    val firstItemFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var didInitialFocus by remember { mutableStateOf(false) }

    val years = remember {
        val current = LocalDate.now().year
        (current downTo 1990).toList()
    }

    LaunchedEffect(state.items.firstOrNull()?.key) {
        if (!didInitialFocus && state.items.isNotEmpty()) {
            delay(110)
            runCatching { firstItemFocus.requestFocus() }
                .onSuccess { didInitialFocus = true }
        }
    }

    // A new filter means a brand-new result set: start back at the top.
    LaunchedEffect(state.genreId, state.year) {
        runCatching { gridState.scrollToItem(0) }
    }

    // Lazy loading: when the last visible poster is within four items of the
    // end of the grid, silently fetch the next page. This keeps the collection
    // filling up as the viewer scrolls instead of stopping after page one.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 4) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Ink)
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (row.eyebrow.isNotBlank()) {
                    Text(
                        text = row.eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                        color = TvColors.Cyan,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(2.dp))
                }
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.items.isNotEmpty()) {
                Text(
                    text = if (state.totalPages > 1) {
                        "${state.items.size} titles · page ${state.page} of ${state.totalPages}"
                    } else {
                        "${state.items.size} titles"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TvColors.TextMuted,
                )
            }
        }

        if (filtersAvailable) {
            if (state.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "GENRE",
                        modifier = Modifier.width(54.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                        color = TvColors.TextMuted.copy(alpha = 0.75f),
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "all-genres") {
                            TvChip(
                                label = "All genres",
                                selected = state.genreId == null,
                                onClick = { viewModel.setGenre(null) },
                            )
                        }
                        items(state.genres, key = { it.id }) { genre ->
                            TvChip(
                                label = genre.name,
                                selected = state.genreId == genre.id,
                                onClick = { viewModel.setGenre(genre.id) },
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "YEAR",
                    modifier = Modifier.width(54.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = TvColors.TextMuted.copy(alpha = 0.75f),
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all-years") {
                        TvChip(
                            label = "All years",
                            selected = state.year == null,
                            onClick = { viewModel.setYear(null) },
                        )
                    }
                    items(years, key = { it }) { year ->
                        TvChip(
                            label = year.toString(),
                            selected = state.year == year,
                            onClick = { viewModel.setYear(year) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(10.dp))
        }

        when {
            state.loading && state.items.isEmpty() -> TvGridSkeleton(
                rows = 2,
                modifier = Modifier.weight(1f),
            )

            state.error != null && state.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TvError(
                    title = "Collection unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = viewModel::retry,
                )
            }

            state.items.isEmpty() -> TvEmpty(
                title = "Nothing here yet",
                message = if (state.hasFilters) {
                    "No titles match these filters. Try another genre or year."
                } else {
                    "This collection has no titles right now."
                },
                actionLabel = if (state.hasFilters) "Clear filters" else null,
                onAction = if (state.hasFilters) viewModel::clearFilters else null,
            )

            else -> {
                TvPosterGrid(
                    items = state.items,
                    onOpen = onOpenDetails,
                    modifier = Modifier.weight(1f),
                    state = gridState,
                    firstFocusRequester = firstItemFocus,
                    footer = if (state.hasMore) {
                        {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = TvColors.Cyan,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.size(9.dp))
                                    Text(
                                        text = "Loading more…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TvColors.TextMuted,
                                    )
                                } else {
                                    TvActionButton(
                                        label = "Load more",
                                        onClick = viewModel::loadMore,
                                        primary = true,
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
                if (state.error != null) {
                    Text(
                        text = state.error.orEmpty(),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.Coral,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
