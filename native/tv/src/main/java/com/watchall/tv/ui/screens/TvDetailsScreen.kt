package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.CastMember
import com.watchall.core.data.EpisodeSummary
import com.watchall.core.data.Formatters
import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.vm.DetailsUiState
import com.watchall.core.vm.DetailsViewModel
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvFocusable
import com.watchall.tv.ui.components.TvLoading
import com.watchall.tv.ui.components.TvPill
import com.watchall.tv.ui.components.TvPosterRow
import com.watchall.tv.ui.components.TvRemoteImage
import com.watchall.tv.ui.components.TvSectionHeader
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.components.TvSourcePickerDialog
import com.watchall.tv.ui.theme.TvColors
import com.watchall.tv.ui.toPlayerRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvDetailsScreen(
    viewModel: DetailsViewModel,
    onPlay: (PlayerRequest) -> Unit,
    onOpenDetails: (MediaSummary) -> Unit,
    onOpenPerson: (CastMember) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(state.details, state.currentEpisode) {
        if (didInitialFocus) return@LaunchedEffect
        val details = state.details
        val ready = details != null &&
            (details.mediaType == MediaType.MOVIE || state.currentEpisode != null)
        if (ready) {
            delay(90)
            runCatching { playFocus.requestFocus() }.onSuccess { didInitialFocus = true }
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null && state.details == null) {
            delay(90)
            runCatching { retryFocus.requestFocus() }
        }
    }

    when {
        state.loading && state.details == null -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusable(),
            ) {
                TvLoading("Opening the story…")
            }
        }

        state.error != null && state.details == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(TvSizes.PagePadding),
                contentAlignment = Alignment.Center,
            ) {
                TvError(
                    title = "Could not open this title",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = viewModel::load,
                    primaryFocus = retryFocus,
                )
            }
        }

        state.details != null -> TvDetailsContent(
            state = state,
            onPlay = onPlay,
            onOpenDetails = onOpenDetails,
            onOpenPerson = onOpenPerson,
            onToggleWatchlist = viewModel::toggleWatchlist,
            onSelectSeason = viewModel::selectSeason,
            onSelectEpisode = viewModel::selectEpisode,
            playFocus = playFocus,
        )
    }
}

@Composable
private fun TvDetailsContent(
    state: DetailsUiState,
    onPlay: (PlayerRequest) -> Unit,
    onOpenDetails: (MediaSummary) -> Unit,
    onOpenPerson: (CastMember) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    playFocus: FocusRequester,
) {
    val details = state.details!!
    val selectedEpisode = state.currentEpisode
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pendingPlay by remember { mutableStateOf<PlayerRequest?>(null) }

    val watchLabel = when {
        details.mediaType == MediaType.TV && selectedEpisode != null -> "Play ${selectedEpisode.code}"
        details.mediaType == MediaType.TV -> "Play episode"
        else -> "Play movie"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item(key = "hero") {
                TvDetailsHero(
                    details = details,
                    watchlisted = state.watchlisted,
                    watchLabel = watchLabel,
                    selectedEpisodeCode = selectedEpisode?.code,
                    onWatch = {
                        pendingPlay = if (details.mediaType == MediaType.TV && selectedEpisode != null) {
                            details.toPlayerRequest(
                                seasonNumber = selectedEpisode.seasonNumber,
                                episodeNumber = selectedEpisode.episodeNumber,
                                episodeTitle = selectedEpisode.name,
                            )
                        } else {
                            details.toPlayerRequest()
                        }
                    },
                    onToggleWatchlist = onToggleWatchlist,
                    playFocus = playFocus,
                    onPlayFocused = { scope.launch { listState.scrollToItem(0) } },
                    enabled = details.mediaType == MediaType.MOVIE || selectedEpisode != null,
                )
            }

            if (details.mediaType == MediaType.TV) {
                item(key = "episodes") {
                    Column(
                        modifier = Modifier.padding(horizontal = TvSizes.PagePadding),
                    ) {
                        TvSectionHeader(
                            title = "Episodes",
                            eyebrow = state.season?.let { season ->
                                "${season.name} · ${season.episodes.size} episodes"
                            } ?: "Pick a season",
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                details.seasons.filter { it.seasonNumber > 0 },
                                key = { it.seasonNumber },
                            ) { season ->
                                TvChip(
                                    label = season.name,
                                    selected = state.selectedSeason == season.seasonNumber,
                                    onClick = { onSelectSeason(season.seasonNumber) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        when {
                            state.seasonLoading -> Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = TvColors.Cyan,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = "Loading episodes…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TvColors.TextMuted,
                                )
                            }

                            state.seasonError != null -> TvError(
                                title = "Episodes unavailable",
                                message = state.seasonError.orEmpty(),
                                primaryLabel = "Try again",
                                onPrimary = { state.selectedSeason?.let(onSelectSeason) },
                            )

                            state.season != null -> TvEpisodeRow(
                                episodes = state.season!!.episodes,
                                selectedEpisode = state.selectedEpisode,
                                onSelect = { episode ->
                                    onSelectEpisode(episode.episodeNumber)
                                    onPlay(
                                        details.toPlayerRequest(
                                            seasonNumber = episode.seasonNumber,
                                            episodeNumber = episode.episodeNumber,
                                            episodeTitle = episode.name,
                                        ),
                                    )
                                },
                            )

                            else -> Text(
                                text = "No episodes are available for this season.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TvColors.TextMuted,
                                modifier = Modifier.padding(vertical = 20.dp),
                            )
                        }
                    }
                }
            }

            if (details.cast.isNotEmpty()) {
                item(key = "cast") {
                    Column(Modifier.padding(horizontal = TvSizes.PagePadding)) {
                        TvSectionHeader(
                            title = "Cast",
                            eyebrow = "Faces you may know",
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                        ) {
                            items(details.cast.take(16), key = { it.id }) { cast ->
                                TvCastCard(
                                    cast = cast,
                                    onClick = { onOpenPerson(cast) },
                                )
                            }
                        }
                    }
                }
            }

            if (details.recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    Column(Modifier.padding(horizontal = TvSizes.PagePadding)) {
                        TvSectionHeader(
                            title = "More like this",
                            eyebrow = "Keep the night going",
                        )
                        TvPosterRow(
                            items = details.recommendations,
                            onOpen = onOpenDetails,
                        )
                    }
                }
            }
        }

        pendingPlay?.let { request ->
            TvSourcePickerDialog(
                mediaType = request.mediaType,
                initialSource = request.initialSource,
                onSelect = { source ->
                    pendingPlay = null
                    onPlay(request.copy(initialSource = source))
                },
                onDismiss = {
                    pendingPlay = null
                    scope.launch {
                        delay(140)
                        runCatching { playFocus.requestFocus() }
                    }
                },
            )
        }
    }
}

@Composable
private fun TvDetailsHero(
    details: MediaDetails,
    watchlisted: Boolean,
    watchLabel: String,
    selectedEpisodeCode: String?,
    onWatch: () -> Unit,
    onToggleWatchlist: () -> Unit,
    playFocus: FocusRequester,
    onPlayFocused: () -> Unit = {},
    enabled: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(356.dp),
    ) {
        val showPoster = maxWidth > 1120.dp && details.posterPath != null
        Box(Modifier.fillMaxSize()) {
            TvRemoteImage(
                path = details.backdropPath ?: details.posterPath,
                contentDescription = details.displayTitle,
                size = "w1280",
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to TvColors.Ink.copy(alpha = 0.97f),
                            0.44f to TvColors.Ink.copy(alpha = 0.74f),
                            0.86f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.36f),
                            0.50f to Color.Transparent,
                            1f to TvColors.Ink,
                        ),
                    ),
            )

            if (showPoster) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TvSizes.PagePadding)
                        .width(132.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                ) {
                    TvRemoteImage(
                        path = details.posterPath,
                        contentDescription = details.displayTitle,
                        size = "w500",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(if (showPoster) 0.66f else 0.76f)
                    .padding(start = TvSizes.PagePadding, end = 16.dp, bottom = 22.dp),
            ) {
                Text(
                    text = if (details.mediaType == MediaType.TV) "WATCHALL SERIES" else "WATCHALL FILM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = TvColors.Cyan,
                    letterSpacing = 1.7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = details.displayTitle,
                    style = MaterialTheme.typography.displaySmall,
                    color = TvColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details.tagline.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = details.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = TvColors.Text.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (details.rating > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = null,
                                tint = TvColors.Amber,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = Formatters.rating(details.rating),
                                style = MaterialTheme.typography.labelMedium,
                                color = TvColors.Amber,
                            )
                        }
                    }
                    if (details.year.isNotBlank()) TvPill(details.year)
                    if (details.certification.isNotBlank()) TvPill(details.certification)
                    if (selectedEpisodeCode != null) {
                        TvPill(selectedEpisodeCode)
                    } else {
                        TvPill(
                            if (details.mediaType == MediaType.TV) {
                                "${details.numberOfSeasons} seasons"
                            } else {
                                Formatters.runtime(details.runtimeMinutes).ifBlank { "Movie" }
                            },
                        )
                    }
                    if (details.originalLanguage.isNotBlank()) {
                        TvPill(Formatters.languageName(details.originalLanguage))
                    }
                }
                if (details.genres.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        details.genres.take(3).forEach { genre ->
                            TvPill(genre.name)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = details.overview.ifBlank { "No overview is available for this title yet." },
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = TvColors.Text.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.96f),
                )
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvActionButton(
                        label = watchLabel,
                        onClick = onWatch,
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        focusRequester = playFocus,
                        bringIntoViewOnFocus = false,
                        enabled = enabled,
                        onFocusChange = { if (it) onPlayFocused() },
                    )
                    TvActionButton(
                        label = if (watchlisted) "In My List" else "Add to My List",
                        onClick = onToggleWatchlist,
                        icon = if (watchlisted) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCastCard(
    cast: CastMember,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.width(104.dp),
        shape = RoundedCornerShape(12.dp),
        scaleOnFocus = 1.055f,
        containerColor = Color.Transparent,
        focusedContainerColor = TvColors.Card,
        onFocusChange = { focused = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(TvColors.CardHigh)
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) TvColors.Cyan else Color.White.copy(alpha = 0.10f),
                        shape = CircleShape,
                    ),
            ) {
                TvRemoteImage(
                    path = cast.profilePath,
                    contentDescription = cast.name,
                    size = "w185",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = cast.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) TvColors.Cyan else TvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (focused) "View movies & shows" else cast.character,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = if (focused) TvColors.Cyan.copy(alpha = 0.78f) else TvColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TvEpisodeRow(
    episodes: List<EpisodeSummary>,
    selectedEpisode: Int?,
    onSelect: (EpisodeSummary) -> Unit,
) {
    if (episodes.isEmpty()) return
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(174.dp),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(episodes, key = { _, episode -> episode.episodeNumber }) { _, episode ->
            TvEpisodeCard(
                episode = episode,
                selected = selectedEpisode == episode.episodeNumber,
                onClick = { onSelect(episode) },
            )
        }
    }
}

@Composable
private fun TvEpisodeCard(
    episode: EpisodeSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier
            .width(212.dp)
            .height(166.dp),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.035f,
        containerColor = if (selected) TvColors.CardHigh else TvColors.Card,
        focusedContainerColor = TvColors.CardHigh,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
            ) {
                TvRemoteImage(
                    path = episode.stillPath,
                    contentDescription = episode.name,
                    size = "w300",
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                            ),
                        ),
                )
                Text(
                    text = episode.code,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = if (selected) TvColors.Cyan else TvColors.Text,
                    fontWeight = FontWeight.Bold,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.50f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            ) {
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOf(
                        episode.runtimeMinutes.takeIf { it > 0 }?.let(Formatters::runtime),
                        episode.airDate,
                    ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = TvColors.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = modifier.size(42.dp),
        shape = RoundedCornerShape(10.dp),
        focusRequester = focusRequester,
        rightFocusRequester = rightFocusRequester,
        downFocusRequester = downFocusRequester,
        scaleOnFocus = 1.05f,
        containerColor = Color.Black.copy(alpha = 0.42f),
        focusedContainerColor = TvColors.CardHigh,
        onFocusChange = { focused = it },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (focused) TvColors.Cyan else TvColors.Text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
