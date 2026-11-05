package com.watchall.tv.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.toPlayerRequest
import com.watchall.core.repo.WatchAllRepository
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvContinueRow
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvPageHeader
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvSectionHeader
import com.watchall.tv.ui.components.TvSizes

@Composable
fun TvLibraryScreen(
    repository: WatchAllRepository,
    onOpenDetails: (MediaSummary) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onBrowse: () -> Unit,
) {
    val resume = repository.continueWatching()
    val saved = repository.watchlist().map { it.media }
    val contentFocus = LocalTvContentFocusRequester.current

    if (resume.isEmpty() && saved.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp),
        ) {
            TvPageHeader(
                title = "Library",
                subtitle = "Saved stories and unfinished nights.",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TvEmpty(
                    title = "Your library is waiting",
                    message = "Save a film or series, or start watching something, and it will appear here.",
                    actionLabel = "Browse titles",
                    onAction = onBrowse,
                    actionFocus = contentFocus,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp),
    ) {
        TvPageHeader(
            title = "Library",
            subtitle = "Saved stories and unfinished nights.",
        )

        if (resume.isNotEmpty()) {
            TvSectionHeader(
                title = "Continue watching",
                eyebrow = "Pick up the thread",
            )
            TvContinueRow(
                entries = resume,
                onResume = { entry ->
                    val request = entry.toPlayerRequest()
                    if (request != null) {
                        onPlay(request)
                    } else {
                        entry.mediaKey.substringAfter('-').substringBefore('-')
                            .toIntOrNull()
                            ?.let { id -> onOpenDetails(entry.asSummary(id)) }
                    }
                },
                firstFocusRequester = contentFocus,
            )
        }

        if (saved.isNotEmpty()) {
            TvSectionHeader(
                title = "My list",
                eyebrow = "${saved.size} saved title${if (saved.size == 1) "" else "s"}",
            )
            TvPosterGrid(
                items = saved,
                onOpen = onOpenDetails,
                modifier = Modifier.weight(1f),
                firstFocusRequester = if (resume.isEmpty()) contentFocus else null,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun com.watchall.core.data.ResumeEntry.asSummary(id: Int) = MediaSummary(
    id = id,
    title = title,
    posterPath = posterPath,
    backdropPath = backdropPath,
    year = "",
    mediaType = mediaType,
)
