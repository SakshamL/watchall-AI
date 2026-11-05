package com.watchall.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.MediaSummary
import com.watchall.core.vm.SearchFilter
import com.watchall.core.vm.SearchViewModel
import com.watchall.mobile.ui.components.EmptyPanel
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.PosterCard
import com.watchall.mobile.ui.components.ScreenTitle
import com.watchall.mobile.ui.theme.WatchColors

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset),
    ) {
        ScreenTitle(
            title = "Search",
            subtitle = "Movies, series and hidden gems",
        )
        SearchField(
            query = state.query,
            searching = state.searching,
            focusManager = focusManager,
            onQueryChange = viewModel::setQuery,
            onSubmit = {
                focusManager.clearFocus()
                viewModel.submit()
            },
            onClear = viewModel::clear,
        )
        if (state.searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 20.dp),
                color = WatchColors.Cyan,
                trackColor = Color.Transparent,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { filter ->
                val selected = state.filter == filter
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
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
        }

        when {
            state.query.isBlank() -> SearchLanding(
                recent = state.recent,
                onRecent = {
                    focusManager.clearFocus()
                    viewModel.chooseRecent(it)
                },
                onRemoveRecent = viewModel::removeRecent,
                onClearRecent = viewModel::clearRecent,
            )

            state.error != null && state.results.isEmpty() -> Box(Modifier.weight(1f)) {
                ErrorPanel(
                    title = "Search unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    },
                )
            }

            state.searching && state.results.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = WatchColors.Cyan, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Searching the catalogue…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchColors.TextMuted,
                    )
                }
            }

            state.filteredResults.isEmpty() && !state.searching -> Box(Modifier.weight(1f)) {
                EmptyPanel(
                    icon = Icons.Outlined.Search,
                    title = "No matches",
                    message = "Try a different spelling or switch the filter.",
                )
            }

            else -> {
                Text(
                    text = "${state.filteredResults.size} result${if (state.filteredResults.size == 1) "" else "s"} for “${state.query.trim()}”",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = WatchColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    gridItems(state.filteredResults, key = { it.key }) { media ->
                        PosterCard(
                            media = media,
                            width = null,
                            onClick = {
                                focusManager.clearFocus()
                                onOpenDetails(media)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    searching: Boolean,
    focusManager: FocusManager,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        placeholder = {
            Text(
                text = "Search titles, people, moods…",
                color = WatchColors.TextMuted,
            )
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = WatchColors.TextMuted)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = WatchColors.TextMuted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSubmit()
            },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = WatchColors.Card,
            unfocusedContainerColor = WatchColors.Card,
            focusedBorderColor = WatchColors.Cyan.copy(alpha = 0.65f),
            unfocusedBorderColor = WatchColors.Hairline,
            focusedTextColor = WatchColors.Text,
            unfocusedTextColor = WatchColors.Text,
            cursorColor = WatchColors.Cyan,
        ),
    )
}

@Composable
private fun SearchLanding(
    recent: List<String>,
    onRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (recent.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleMedium,
                        color = WatchColors.Text,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Clear",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClearRecent)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = WatchColors.Cyan,
                    )
                }
            }
            items(recent) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRecent(query) }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = WatchColors.TextMuted,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = query,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemoveRecent(query) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $query from search history",
                            tint = WatchColors.TextMuted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WatchColors.Card)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(WatchColors.Violet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Whatshot,
                        contentDescription = null,
                        tint = WatchColors.Violet,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Not sure what to watch?",
                        style = MaterialTheme.typography.titleSmall,
                        color = WatchColors.Text,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Try a title, an actor, or simply “hindi action”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WatchColors.TextMuted,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
