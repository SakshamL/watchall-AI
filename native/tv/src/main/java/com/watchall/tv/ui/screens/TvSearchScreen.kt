package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.MediaSummary
import com.watchall.core.vm.SearchFilter
import com.watchall.core.vm.SearchViewModel
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvFocusable
import com.watchall.tv.ui.components.TvLoaderMark
import com.watchall.tv.ui.components.TvPageHeader
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvSectionHeader
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.components.TvViewMoreButton
import com.watchall.tv.ui.theme.TvColors

@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fallbackFocus = remember { FocusRequester() }
    val searchFocus = LocalTvContentFocusRequester.current ?: fallbackFocus

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp),
    ) {
        TvPageHeader(
            title = "Search",
            subtitle = "Find a film, a series, or an actor.",
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .focusRequester(searchFocus),
            placeholder = {
                Text(
                    text = "Search movies, shows, actors…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextMuted,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TvColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                if (state.query.isNotBlank()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = TvColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.submit() },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TvColors.Card,
                unfocusedContainerColor = TvColors.Card,
                focusedBorderColor = TvColors.Cyan.copy(alpha = 0.75f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.09f),
                focusedTextColor = TvColors.Text,
                unfocusedTextColor = TvColors.Text,
                cursorColor = TvColors.Cyan,
            ),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SearchFilter.entries, key = { it.name }) { filter ->
                TvChip(
                    label = filter.label,
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.query.isBlank() -> {
                if (state.recent.isNotEmpty()) {
                    TvSectionHeader(
                        title = "Recent searches",
                        action = {
                            TvViewMoreButton(
                                onClick = viewModel::clearRecent,
                                label = "Clear all",
                            )
                        },
                    )
                    state.recent.take(6).forEach { query ->
                        TvFocusable(
                            onClick = {
                                viewModel.chooseRecent(query)
                                runCatching { searchFocus.requestFocus() }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            scaleOnFocus = 1.015f,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = TvColors.TextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TvColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    text = "Try a title, an actor, or a mood. Search covers the whole WatchAll catalogue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextMuted,
                )
            }

            state.error != null && state.results.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TvError(
                    title = "Search unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = { viewModel.submit() },
                )
            }

            state.searching && state.results.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TvLoaderMark(size = 42.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColors.TextMuted,
                    )
                }
            }

            state.filteredResults.isEmpty() -> TvEmpty(
                title = "No matches",
                message = "Try a different spelling or filter.",
            )

            else -> {
                Text(
                    text = "${state.filteredResults.size} results for “${state.query.trim()}”",
                    style = MaterialTheme.typography.labelMedium,
                    color = TvColors.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TvPosterGrid(
                    items = state.filteredResults,
                    onOpen = onOpenDetails,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
