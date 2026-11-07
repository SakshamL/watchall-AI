package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.WatchAllGraph
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.vm.CatalogViewModel
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvGridSkeleton
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.theme.TvColors
import com.watchall.tv.ui.watchAllTvViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

private data class LanguageFilter(val label: String, val code: String?)

private val languages = listOf(
    LanguageFilter("All languages", null),
    LanguageFilter("Hindi", "hi"),
    LanguageFilter("English", "en"),
    LanguageFilter("Punjabi", "pa"),
    LanguageFilter("Tamil", "ta"),
    LanguageFilter("Telugu", "te"),
)

/**
 * Compact discovery page.
 *
 * Everything above the grid is one 32 dp row: the type switch and the filters
 * share a single horizontal strip instead of stacking a page header and two
 * full chip rows. That leaves roughly two complete poster rows visible on a
 * 1080p TV (960 x 540 dp) and keeps the grid out of the bottom overscan area.
 *
 * Paging is automatic. The old "Load more" button sat inside the grid and
 * disabled itself while loading, which removed the focused item and threw the
 * remote back to the top of the app. Discovery now fills in as the viewer
 * reaches the final row, so focus and scroll position never move.
 */
@Composable
fun TvBrowseScreen(
    graph: WatchAllGraph,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(MediaType.MOVIE) }
    val viewModel = watchAllTvViewModel<CatalogViewModel>(key = "catalog-${type.apiValue}") {
        CatalogViewModel(graph.repository, type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentFocus = LocalTvContentFocusRequester.current
    val gridState = rememberLazyGridState()
    val firstPosterFocus = remember { FocusRequester() }
    var lastFilterKey by rememberSaveable { mutableStateOf<String?>(null) }

    val filterKey = "${type.apiValue}|${state.language.orEmpty()}|${state.genreId ?: 0}"
    LaunchedEffect(filterKey) {
        // Only scroll to the top when a filter really changed. Re-entering the
        // tab (or returning from details) must keep the viewer's place.
        if (lastFilterKey != null && lastFilterKey != filterKey) {
            runCatching { gridState.scrollToItem(0) }
        }
        lastFilterKey = filterKey
    }

    // Prefetch the next page as the last rows come into view. The repository
    // guards against duplicate requests, so this cannot spam the server.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 6) {
                    viewModel.loadMore()
                }
            }
    }

    val gridHasItems = state.items.isNotEmpty()
    val gridDownFocus = if (gridHasItems) firstPosterFocus else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Browse",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TvColors.Text,
            )
            Spacer(Modifier.width(10.dp))
            if (state.loadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = TvColors.Cyan,
                    strokeWidth = 1.5.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (state.items.isNotEmpty()) {
                Text(
                    text = "${state.items.size} titles · page ${state.page} of ${state.totalPages}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TvColors.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "type-movies") {
                TvChip(
                    label = "Movies",
                    selected = type == MediaType.MOVIE,
                    onClick = { type = MediaType.MOVIE },
                    focusRequester = if (type == MediaType.MOVIE) contentFocus else null,
                    downFocusRequester = gridDownFocus,
                )
            }
            item(key = "type-tv") {
                TvChip(
                    label = "TV Shows",
                    selected = type == MediaType.TV,
                    onClick = { type = MediaType.TV },
                    focusRequester = if (type == MediaType.TV) contentFocus else null,
                    downFocusRequester = gridDownFocus,
                )
            }
            item(key = "type-divider") {
                FilterDivider()
            }
            item(key = "all-languages") {
                TvChip(
                    label = "All languages",
                    selected = state.language == null,
                    onClick = { viewModel.setLanguage(null) },
                    downFocusRequester = gridDownFocus,
                )
            }
            items(languages.drop(1), key = { "lang-${it.code}" }) { language ->
                TvChip(
                    label = language.label,
                    selected = state.language == language.code,
                    onClick = { viewModel.setLanguage(language.code) },
                    downFocusRequester = gridDownFocus,
                )
            }
            item(key = "genre-divider") {
                FilterDivider()
            }
            item(key = "all-genres") {
                TvChip(
                    label = "All genres",
                    selected = state.genreId == null,
                    onClick = { viewModel.setGenre(null) },
                    downFocusRequester = gridDownFocus,
                )
            }
            items(state.genres, key = { "genre-${it.id}" }) { genre ->
                TvChip(
                    label = genre.name,
                    selected = state.genreId == genre.id,
                    onClick = { viewModel.setGenre(genre.id) },
                    downFocusRequester = gridDownFocus,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

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
                    primaryFocus = contentFocus,
                )
            }

            state.items.isEmpty() -> TvEmpty(
                title = "Nothing matches those filters",
                message = "Try another genre or language.",
            )

            else -> TvPosterGrid(
                items = state.items,
                onOpen = onOpenDetails,
                modifier = Modifier.weight(1f),
                state = gridState,
                firstFocusRequester = firstPosterFocus,
                firstItemUpFocusRequester = contentFocus,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 26.dp),
                minColumnWidth = 96.dp,
            )
        }
    }
}

@Composable
private fun FilterDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(16.dp)
            .background(Color.White.copy(alpha = 0.12f)),
    )
}
