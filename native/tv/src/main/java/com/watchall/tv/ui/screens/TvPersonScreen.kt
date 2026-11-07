package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.vm.PersonViewModel
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvEmpty
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvGridSkeleton
import com.watchall.tv.ui.components.TvPosterGrid
import com.watchall.tv.ui.components.TvRemoteImage
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.theme.TvColors
import kotlinx.coroutines.delay

private enum class PersonFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    TV_SHOWS("TV Shows"),
}

@Composable
fun TvPersonScreen(
    viewModel: PersonViewModel,
    onOpenDetails: (MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val retryFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var filter by remember { mutableStateOf(PersonFilter.ALL) }
    var didInitialFocus by remember { mutableStateOf(false) }

    val movies = state.credits.filter { it.mediaType == MediaType.MOVIE }
    val shows = state.credits.filter { it.mediaType == MediaType.TV }
    val items = when (filter) {
        PersonFilter.ALL -> state.credits
        PersonFilter.MOVIES -> movies
        PersonFilter.TV_SHOWS -> shows
    }

    LaunchedEffect(state.loading, state.credits.size) {
        if (!state.loading && !didInitialFocus && state.credits.isNotEmpty()) {
            delay(120)
            runCatching { firstItemFocus.requestFocus() }
                .onSuccess { didInitialFocus = true }
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null && state.credits.isEmpty()) {
            delay(110)
            runCatching { retryFocus.requestFocus() }
        }
    }

    LaunchedEffect(filter) {
        runCatching { gridState.scrollToItem(0) }
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
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(TvColors.CardHigh, TvColors.Card)),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (viewModel.profilePath != null) {
                    TvRemoteImage(
                        path = viewModel.profilePath,
                        contentDescription = viewModel.name,
                        size = "w185",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = TvColors.TextMuted,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "FILMOGRAPHY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = TvColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = viewModel.name.ifBlank { "Cast member" },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append("${movies.size} movie${if (movies.size == 1) "" else "s"}")
                        append(" · ")
                        append("${shows.size} TV show${if (shows.size == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextMuted,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonFilter.entries.forEach { option ->
                TvChip(
                    label = when (option) {
                        PersonFilter.ALL -> "All ${state.credits.size}"
                        PersonFilter.MOVIES -> "Movies ${movies.size}"
                        PersonFilter.TV_SHOWS -> "TV ${shows.size}"
                    },
                    selected = filter == option,
                    onClick = { filter = option },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            state.loading && state.credits.isEmpty() -> TvGridSkeleton(
                rows = 2,
                modifier = Modifier.weight(1f),
            )

            state.error != null && state.credits.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                TvError(
                    title = "Filmography unavailable",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = viewModel::load,
                    primaryFocus = retryFocus,
                )
            }

            items.isEmpty() -> TvEmpty(
                title = "No credits here yet",
                message = "TMDB has no ${filter.label.lowercase()} listed for ${viewModel.name} right now.",
            )

            else -> TvPosterGrid(
                items = items,
                onOpen = onOpenDetails,
                modifier = Modifier.weight(1f),
                firstFocusRequester = if (!didInitialFocus) firstItemFocus else null,
                state = gridState,
            )
        }
    }
}
