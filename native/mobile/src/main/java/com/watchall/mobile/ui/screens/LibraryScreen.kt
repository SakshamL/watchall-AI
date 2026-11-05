package com.watchall.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.ResumeEntry
import com.watchall.core.repo.WatchAllRepository
import com.watchall.mobile.ui.components.ContinueWatchingCard
import com.watchall.mobile.ui.components.EmptyPanel
import com.watchall.mobile.ui.components.PosterCard
import com.watchall.mobile.ui.components.RoundIconButton
import com.watchall.mobile.ui.components.ScreenTitle
import com.watchall.mobile.ui.components.SectionHeader
import com.watchall.mobile.ui.theme.WatchColors

@Composable
fun LibraryScreen(
    repository: WatchAllRepository,
    onOpenDetails: (MediaSummary) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onContinueWatchingRemoved: () -> Unit = {},
) {
    var version by remember { mutableIntStateOf(0) }
    val resume = remember(version) { repository.continueWatching() }
    val saved = remember(version) { repository.watchlist().map { it.media } }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    if (resume.isEmpty() && saved.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset),
        ) {
            ScreenTitle(
                title = "Library",
                subtitle = "Your saved stories and unfinished nights",
                action = {
                    RoundIconButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Refresh library",
                        onClick = { version++ },
                    )
                },
            )
            EmptyPanel(
                icon = Icons.Outlined.BookmarkBorder,
                title = "Your library is waiting",
                message = "Save a film or series from its details page, and it will live here.",
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, start = 20.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(
                title = "Library",
                subtitle = "Your saved stories and unfinished nights",
                action = {
                    RoundIconButton(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "Refresh library",
                        onClick = { version++ },
                    )
                },
            )
        }

        if (resume.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionHeader(
                        title = "Continue watching",
                        eyebrow = "Unfinished business",
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(resume, key = { it.mediaKey }) { entry ->
                            ContinueWatchingCard(
                                entry = entry,
                                onClick = { onResume(entry) },
                                onRemove = {
                                    repository.clearProgress(entry.mediaKey)
                                    version++
                                    onContinueWatchingRemoved()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (saved.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = 8.dp)) {
                    SectionHeader(
                        title = "My list",
                        eyebrow = "${saved.size} saved title${if (saved.size == 1) "" else "s"}",
                    )
                }
            }
            gridItems(saved, key = { it.key }) { media ->
                PosterCard(
                    media = media,
                    width = null,
                    onClick = { onOpenDetails(media) },
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    SectionHeader(title = "My list", eyebrow = "Nothing saved yet")
                    Text(
                        text = "Open a title and tap the bookmark button to keep it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WatchColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}
