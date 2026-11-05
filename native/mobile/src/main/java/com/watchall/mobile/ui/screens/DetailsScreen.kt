package com.watchall.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.CastMember
import com.watchall.core.data.EpisodeSummary
import com.watchall.core.data.Formatters
import com.watchall.core.data.ImageUrls
import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.SeasonSummary
import com.watchall.core.vm.DetailsUiState
import com.watchall.core.vm.DetailsViewModel
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.LoadingPanel
import com.watchall.mobile.ui.components.MediaRow
import com.watchall.mobile.ui.components.RatingPill
import com.watchall.mobile.ui.components.RemoteImage
import com.watchall.mobile.ui.components.RoundIconButton
import com.watchall.mobile.ui.components.SectionHeader
import com.watchall.mobile.ui.components.TagPill
import com.watchall.mobile.ui.theme.WatchColors
import com.watchall.mobile.ui.toPlayerRequest

@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    onBack: () -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onOpenDetails: (com.watchall.core.data.MediaSummary) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.details == null -> {
                LoadingPanel("Opening the story…")
                BackButton(onBack, Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 16.dp))
            }

            state.error != null && state.details == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorPanel(
                        title = "Could not open this title",
                        message = state.error.orEmpty(),
                        primaryLabel = "Try again",
                        onPrimary = viewModel::load,
                        secondaryLabel = "Go back",
                        onSecondary = onBack,
                    )
                }
                BackButton(onBack, Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 16.dp))
            }

            state.details != null -> DetailsContent(
                state = state,
                onBack = onBack,
                onPlay = onPlay,
                onOpenDetails = onOpenDetails,
                onToggleWatchlist = viewModel::toggleWatchlist,
                onSelectSeason = viewModel::selectSeason,
                onSelectEpisode = viewModel::selectEpisode,
            )
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsUiState,
    onBack: () -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onOpenDetails: (com.watchall.core.data.MediaSummary) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
) {
    val details = state.details!!
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var overviewExpanded by remember { mutableStateOf(false) }
    val selectedEpisode = state.currentEpisode
    val selectedSeasonDetails = state.season
    val playLabel = when {
        details.mediaType == MediaType.TV && selectedEpisode != null -> "Play ${selectedEpisode.code}"
        details.mediaType == MediaType.TV -> "Play episode"
        else -> "Play movie"
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 34.dp),
        ) {
            item(key = "hero") {
                DetailsHero(
                    details = details,
                    watchlisted = state.watchlisted,
                    onToggleWatchlist = onToggleWatchlist,
                )
            }
            item(key = "top") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = details.displayTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        color = WatchColors.Text,
                    )
                    if (details.tagline.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = details.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WatchColors.Cyan.copy(alpha = 0.85f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingPill(details.rating)
                        Spacer(Modifier.width(8.dp))
                        TagPill(details.year.ifBlank { "—" })
                        if (details.certification.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            TagPill(details.certification)
                        }
                        Spacer(Modifier.width(6.dp))
                        TagPill(
                            if (details.mediaType == MediaType.TV) {
                                "${details.numberOfSeasons} season${if (details.numberOfSeasons == 1) "" else "s"}"
                            } else {
                                Formatters.runtime(details.runtimeMinutes).ifBlank { "Movie" }
                            },
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onPlay(
                                    if (details.mediaType == MediaType.TV && selectedEpisode != null) {
                                        details.toPlayerRequest(
                                            seasonNumber = selectedEpisode.seasonNumber,
                                            episodeNumber = selectedEpisode.episodeNumber,
                                            episodeTitle = selectedEpisode.name,
                                        )
                                    } else {
                                        details.toPlayerRequest()
                                    },
                                )
                            },
                            enabled = details.mediaType == MediaType.MOVIE || selectedEpisode != null,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WatchColors.Text,
                                contentColor = WatchColors.Ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(playLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Surface(
                            onClick = onToggleWatchlist,
                            shape = CircleShape,
                            color = if (state.watchlisted) WatchColors.Cyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (state.watchlisted) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (state.watchlisted) "Remove from My List" else "Add to My List",
                                    tint = if (state.watchlisted) WatchColors.Cyan else WatchColors.Text,
                                    modifier = Modifier.size(21.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (state.watchlisted) "Saved" else "My list",
                                    color = if (state.watchlisted) WatchColors.Cyan else WatchColors.Text,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "Overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = WatchColors.Text,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = details.overview.ifBlank { "No overview is available for this title yet." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = WatchColors.TextMuted,
                        lineHeight = 23.sp,
                        maxLines = if (overviewExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (details.overview.length > 260) {
                        Text(
                            text = if (overviewExpanded) "Show less" else "Read more",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clip(CircleShape)
                                .clickable { overviewExpanded = !overviewExpanded }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = WatchColors.Cyan,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (details.mediaType == MediaType.TV) {
                item(key = "seasons-header") {
                    SectionHeader(
                        title = "Episodes",
                        eyebrow = details.seasons.filter { it.seasonNumber > 0 }.joinToString(" · ") {
                            it.name
                        }.ifBlank { "Seasons" },
                    )
                }
                item(key = "seasons") {
                    SeasonChooser(
                        seasons = details.seasons.filter { it.seasonNumber > 0 },
                        selected = state.selectedSeason,
                        onSelect = onSelectSeason,
                    )
                }
                when {
                    state.seasonLoading -> item(key = "episodes-loading") {
                        LoadingPanel("Loading episodes…")
                    }

                    state.seasonError != null -> item(key = "episodes-error") {
                        ErrorPanel(
                            title = "Episodes unavailable",
                            message = state.seasonError.orEmpty(),
                            primaryLabel = "Try again",
                            onPrimary = {
                                state.selectedSeason?.let(onSelectSeason)
                            },
                        )
                    }

                    selectedSeasonDetails != null -> {
                        selectedSeasonDetails.episodes.forEach { episode ->
                            item(key = "episode-${episode.seasonNumber}-${episode.episodeNumber}") {
                                EpisodeCard(
                                    episode = episode,
                                    selected = episode.episodeNumber == state.selectedEpisode,
                                    onClick = { onSelectEpisode(episode.episodeNumber) },
                                    onPlay = {
                                        onPlay(
                                            details.toPlayerRequest(
                                                seasonNumber = episode.seasonNumber,
                                                episodeNumber = episode.episodeNumber,
                                                episodeTitle = episode.name,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (details.cast.isNotEmpty()) {
                item(key = "cast-header") {
                    SectionHeader(title = "Cast", eyebrow = "Faces you may know")
                }
                item(key = "cast") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(details.cast, key = { it.id }) { cast ->
                            CastCard(cast = cast)
                        }
                    }
                }
            }

            if (details.recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    Box(Modifier.padding(top = 18.dp)) {
                        MediaRow(
                            title = "More like this",
                            items = details.recommendations,
                            onOpen = onOpenDetails,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Go back",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            RoundIconButton(
                icon = if (state.watchlisted) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (state.watchlisted) "Remove from My List" else "Add to My List",
                onClick = onToggleWatchlist,
            )
        }
    }
}

@Composable
private fun DetailsHero(
    details: MediaDetails,
    watchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(370.dp),
    ) {
        RemoteImage(
            path = details.backdropPath ?: details.posterPath,
            contentDescription = details.displayTitle,
            size = "w1280",
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to WatchColors.Ink.copy(alpha = 0.2f),
                        1f to WatchColors.Ink,
                    ),
                ),
        )
    }
}

@Composable
private fun SeasonChooser(
    seasons: List<SeasonSummary>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    if (seasons.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it.seasonNumber }) { season ->
            val isSelected = season.seasonNumber == selected
            Surface(
                onClick = { onSelect(season.seasonNumber) },
                shape = CircleShape,
                color = if (isSelected) WatchColors.Cyan.copy(alpha = 0.16f) else WatchColors.Card,
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, WatchColors.Cyan.copy(alpha = 0.45f)) else null,
            ) {
                Text(
                    text = season.name,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) WatchColors.Cyan else WatchColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) WatchColors.CardHigh else WatchColors.Card)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) WatchColors.Cyan.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(122.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            RemoteImage(
                path = episode.stillPath,
                contentDescription = episode.name,
                size = "w300",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.code,
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.Cyan,
                letterSpacing = 0.7.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleSmall,
                color = WatchColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchColors.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(
                    episode.runtimeMinutes.takeIf { it > 0 }?.let(Formatters::runtime),
                    episode.airDate,
                ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.TextMuted.copy(alpha = 0.8f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) WatchColors.Cyan else Color.White.copy(alpha = 0.07f))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play ${episode.code}",
                tint = if (selected) WatchColors.Ink else WatchColors.Text,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun CastCard(cast: CastMember) {
    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(WatchColors.CardHigh),
        ) {
            RemoteImage(
                path = cast.profilePath,
                contentDescription = cast.name,
                size = "w185",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = cast.name,
            style = MaterialTheme.typography.labelMedium,
            color = WatchColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (cast.character.isNotBlank()) {
            Text(
                text = cast.character,
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(
        icon = Icons.Filled.ArrowBack,
        contentDescription = "Go back",
        onClick = onBack,
        modifier = modifier,
    )
}
