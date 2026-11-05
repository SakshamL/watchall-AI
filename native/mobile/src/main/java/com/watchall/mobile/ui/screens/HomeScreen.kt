package com.watchall.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.Formatters
import com.watchall.core.data.HomeFeed
import com.watchall.core.data.HomeRow
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ResumeEntry
import com.watchall.core.vm.HomeViewModel
import com.watchall.mobile.ui.components.BrandLockup
import com.watchall.mobile.ui.components.ContinueWatchingCard
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.LoadingPanel
import com.watchall.mobile.ui.components.MediaRow
import com.watchall.mobile.ui.components.RemoteImage
import com.watchall.mobile.ui.components.RoundIconButton
import com.watchall.mobile.ui.components.SectionHeader
import com.watchall.mobile.ui.theme.HeroTitleStyle
import com.watchall.mobile.ui.theme.WatchColors
import com.watchall.mobile.ui.toPlayerRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenDetails: (MediaSummary) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onViewMore: (HomeRow) -> Unit,
    onRemoveContinueWatching: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshContinueWatching()
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    when {
        state.loading && state.feed == null -> Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = topInset)) {
                HomeHeader(
                    onSearch = onOpenSearch,
                    onSettings = onOpenSettings,
                )
            }
            LoadingPanel("Curating tonight's lineup…")
        }

        state.error != null && state.feed == null -> Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeHeader(
                    onSearch = onOpenSearch,
                    onSettings = onOpenSettings,
                )
                ErrorPanel(
                    title = "WatchAll is offline",
                    message = state.error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = { viewModel.refresh() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.feed != null -> HomeFeedContent(
            feed = state.feed!!,
            continueWatching = state.continueWatching,
            refreshing = state.refreshing,
            error = state.error,
            topInset = topInset,
            onOpenDetails = onOpenDetails,
            onPlay = onPlay,
            onResume = onResume,
            onViewMore = onViewMore,
            onRemoveContinueWatching = onRemoveContinueWatching,
            onOpenSearch = onOpenSearch,
            onOpenSettings = onOpenSettings,
            onRefresh = { viewModel.refresh(showRefreshIndicator = true) },
        )
    }
}

@Composable
private fun HomeHeader(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLockup()
        Spacer(Modifier.weight(1f))
        RoundIconButton(
            icon = Icons.Outlined.Search,
            contentDescription = "Search movies and TV shows",
            onClick = onSearch,
        )
        Spacer(Modifier.width(8.dp))
        RoundIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = "Settings",
            onClick = onSettings,
        )
    }
}

@Composable
private fun HomeFeedContent(
    feed: HomeFeed,
    continueWatching: List<ResumeEntry>,
    refreshing: Boolean,
    error: String?,
    topInset: androidx.compose.ui.unit.Dp,
    onOpenDetails: (MediaSummary) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onViewMore: (HomeRow) -> Unit,
    onRemoveContinueWatching: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, bottom = 28.dp),
    ) {
        item(key = "header") {
            HomeHeader(onSearch = onOpenSearch, onSettings = onOpenSettings)
        }
        if (refreshing) {
            item(key = "refreshing") {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = WatchColors.Cyan,
                    trackColor = Color.Transparent,
                )
            }
        }
        if (error != null) {
            item(key = "soft-error") {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = WatchColors.Coral,
                )
            }
        }
        if (feed.featured.isNotEmpty()) {
            item(key = "hero") {
                FeaturedPager(
                    featured = feed.featured,
                    onOpenDetails = onOpenDetails,
                    onPlay = onPlay,
                )
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-header") {
                SectionHeader(
                    title = "Continue watching",
                    eyebrow = "Pick up where you left off",
                )
            }
            item(key = "continue-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(continueWatching, key = { it.mediaKey }) { entry ->
                        ContinueWatchingCard(
                            entry = entry,
                            onClick = { onResume(entry) },
                            onRemove = { onRemoveContinueWatching(entry.mediaKey) },
                        )
                    }
                }
            }
        }
        items(feed.rows, key = { it.id }) { row ->
            Box(Modifier.padding(top = 12.dp)) {
                MediaRow(
                    title = row.title,
                    eyebrow = row.eyebrow,
                    items = row.items,
                    ranked = row.ranked,
                    onOpen = onOpenDetails,
                    onViewMore = row.query?.let { { onViewMore(row) } },
                )
            }
        }
        item(key = "footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandLockup(compact = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your private cinema, everywhere.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchColors.TextMuted.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Refresh to check for something new",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRefresh)
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun FeaturedPager(
    featured: List<MediaSummary>,
    onOpenDetails: (MediaSummary) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { featured.size })

    LaunchedEffect(featured.size) {
        if (featured.size <= 1) return@LaunchedEffect
        while (isActive) {
            delay(7_500)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % featured.size)
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(516.dp),
        ) { page ->
            val media = featured[page]
            HeroPage(
                media = media,
                onOpenDetails = { onOpenDetails(media) },
                onPlay = {
                    if (media.mediaType == MediaType.TV) {
                        onOpenDetails(media)
                    } else {
                        onPlay(media.toPlayerRequest())
                    }
                },
            )
        }
        if (featured.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(featured.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (selected) 22.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) WatchColors.Cyan else Color.White.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    media: MediaSummary,
    onOpenDetails: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchColors.DeepInk),
    ) {
        RemoteImage(
            path = media.backdropPath ?: media.posterPath,
            contentDescription = media.displayTitle,
            size = "w780",
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to WatchColors.Ink.copy(alpha = 0.15f),
                        0.78f to WatchColors.Ink.copy(alpha = 0.86f),
                        1f to WatchColors.Ink,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to WatchColors.Ink.copy(alpha = 0.7f),
                        0.55f to Color.Transparent,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
        ) {
            Text(
                text = "WATCHALL PREMIERE",
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.Cyan,
                letterSpacing = 1.7.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = media.displayTitle,
                style = HeroTitleStyle,
                color = WatchColors.Text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                media.rating.takeIf { it > 0 }?.let {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = null,
                        tint = WatchColors.Amber,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", it),
                        style = MaterialTheme.typography.labelMedium,
                        color = WatchColors.Amber,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = listOf(
                        media.year,
                        media.mediaType.label,
                        if (media.originalLanguage.isNotBlank()) {
                            Formatters.languageName(media.originalLanguage)
                        } else {
                            ""
                        },
                    ).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = WatchColors.Text.copy(alpha = 0.78f),
                )
            }
            if (media.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = media.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WatchColors.Text.copy(alpha = 0.76f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WatchColors.Text,
                        contentColor = WatchColors.Ink,
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (media.mediaType == MediaType.TV) "View episodes" else "Play now",
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = onOpenDetails,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = WatchColors.Text,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("More info")
                }
            }
        }
    }
}
