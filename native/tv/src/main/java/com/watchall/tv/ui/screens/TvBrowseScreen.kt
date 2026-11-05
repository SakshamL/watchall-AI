package com.watchall.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.WatchAllGraph
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.vm.CatalogViewModel
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvGridSkeleton
import com.watchall.tv.ui.components.TvPageHeader
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.theme.TvColors
import com.watchall.tv.ui.watchAllTvViewModel

private data class LanguageFilter(val label: String, val code: String?)

private val languages = listOf(
    LanguageFilter("All languages", null),
    LanguageFilter("Hindi", "hi"),
    LanguageFilter("English", "en"),
    LanguageFilter("Punjabi", "pa"),
    LanguageFilter("Tamil", "ta"),
    LanguageFilter("Telugu", "te"),
)

@Composable
fun TvBrowseScreen(
    graph: WatchAllGraph,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    var type by remember { mutableStateOf(MediaType.MOVIE) }
    val viewModel = watchAllTvViewModel<CatalogViewModel>(key = "catalog-${type.apiValue}") {
        CatalogViewModel(graph.repository, type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentFocus = LocalTvContentFocusRequester.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(type, state.language, state.genreId) {
        gridState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp),
    ) {
        TvPageHeader(
            title = "Browse",
            subtitle = "Movies and series, sorted your way.",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvChip(
                        label = "Movies",
                        selected = type == MediaType.MOVIE,
                        onClick = { type = MediaType.MOVIE },
                        focusRequester = contentFocus,
                    )
                    TvChip(
                        label = "TV Shows",
                        selected = type == MediaType.TV,
                        onClick = { type = MediaType.TV },
                    )
                }
            },
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(languages, key = { it.label }) { language ->
                TvChip(
                    label = language.label,
                    selected = state.language == language.code,
                    onClick = { viewModel.setLanguage(language.code) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(12.dp))

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
                    title = "Catalogue unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = viewModel::retry,
                )
            }

            state.items.isEmpty() -> TvEmpty(
                title = "Nothing matches those filters",
                message = "Try another genre or language.",
            )

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.items.size} titles · page ${state.page} of ${state.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvColors.TextMuted,
                    )
                    if (state.loadingMore) {
                        Spacer(Modifier.size(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = TvColors.Cyan,
                            strokeWidth = 1.5.dp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.error != null) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TvColors.Coral,
                            maxLines = 1,
                        )
                    }
                }
                TvPosterGrid(
                    items = state.items,
                    onOpen = onOpenDetails,
                    modifier = Modifier.weight(1f),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    footer = if (state.page < state.totalPages) {
                        {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                TvActionButton(
                                    label = if (state.loadingMore) "Loading…" else "Load more",
                                    onClick = viewModel::loadMore,
                                    primary = true,
                                    enabled = !state.loadingMore,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
