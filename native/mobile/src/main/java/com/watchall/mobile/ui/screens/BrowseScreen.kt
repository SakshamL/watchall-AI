package com.watchall.mobile.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.WatchAllGraph
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.vm.CatalogViewModel
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.PosterCard
import com.watchall.mobile.ui.components.ScreenTitle
import com.watchall.mobile.ui.theme.WatchColors
import com.watchall.mobile.ui.watchAllViewModel

private data class BrowseLanguage(val label: String, val code: String?)

private val browseLanguages = listOf(
    BrowseLanguage("All languages", null),
    BrowseLanguage("Hindi", "hi"),
    BrowseLanguage("English", "en"),
    BrowseLanguage("Punjabi", "pa"),
    BrowseLanguage("Tamil", "ta"),
    BrowseLanguage("Telugu", "te"),
    BrowseLanguage("Malayalam", "ml"),
    BrowseLanguage("Kannada", "kn"),
)

@Composable
fun BrowseScreen(
    graph: WatchAllGraph,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(MediaType.MOVIE) }
    val viewModel = watchAllViewModel<CatalogViewModel>(key = "catalog-${type.apiValue}") {
        CatalogViewModel(graph.repository, type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(type, state.language, state.genreId) {
        gridState.scrollToItem(0)
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset),
    ) {
        ScreenTitle(
            title = "Browse",
            subtitle = "Movies and series, sorted your way",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaType.entries.forEach { option ->
                BrowseChip(
                    label = if (option == MediaType.MOVIE) "Movies" else "TV Shows",
                    selected = type == option,
                    onClick = { type = option },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(browseLanguages.size) { index ->
                val language = browseLanguages[index]
                BrowseChip(
                    label = language.label,
                    selected = state.language == language.code,
                    onClick = { viewModel.setLanguage(language.code) },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all-genres") {
                BrowseChip(
                    label = "All genres",
                    selected = state.genreId == null,
                    onClick = { viewModel.setGenre(null) },
                )
            }
            items(state.genres.size, key = { state.genres[it].id }) { index ->
                val genre = state.genres[index]
                BrowseChip(
                    label = genre.name,
                    selected = state.genreId == genre.id,
                    onClick = { viewModel.setGenre(genre.id) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            state.loading && state.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = WatchColors.Cyan, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Loading the catalogue…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchColors.TextMuted,
                    )
                }
            }

            state.error != null && state.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ErrorPanel(
                    title = "Catalogue unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = viewModel::retry,
                )
            }

            state.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing matches those filters. Try another genre or language.",
                    modifier = Modifier.padding(horizontal = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WatchColors.TextMuted,
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.items, key = { it.key }) { media ->
                        PosterCard(
                            media = media,
                            width = null,
                            onClick = { onOpenDetails(media) },
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
                                    .padding(vertical = 12.dp),
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
                                    text = "Loading more titles…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WatchColors.TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = WatchColors.Cyan.copy(alpha = 0.16f),
            selectedLabelColor = WatchColors.Cyan,
            labelColor = WatchColors.TextMuted,
            containerColor = Color.Transparent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = WatchColors.Hairline,
            selectedBorderColor = WatchColors.Cyan.copy(alpha = 0.45f),
        ),
    )
}
