package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.data.Formatters
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ResumeEntry
import com.watchall.core.vm.HomeViewModel
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvContinueRow
import com.watchall.tv.ui.components.TvError
import com.watchall.tv.ui.components.TvHomeSkeleton
import com.watchall.tv.ui.components.TvPill
import com.watchall.tv.ui.components.TvPosterRow
import com.watchall.tv.ui.components.TvRemoteImage
import com.watchall.tv.ui.components.TvSectionHeader
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.components.TvSourcePickerDialog
import com.watchall.tv.ui.components.TvViewMoreButton
import com.watchall.tv.ui.theme.TvColors
import com.watchall.tv.ui.toPlayerRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onOpenDetails: (MediaSummary) -> Unit,
    onPlay: (PlayerRequest) -> Unit,
    onResume: (ResumeEntry) -> Unit,
    onViewMore: (com.watchall.core.data.HomeRow) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentFocus = LocalTvContentFocusRequester.current
    val fallbackFocus = remember { FocusRequester() }
    // The Play button is the first focus target on Home, so it also serves as
    // the destination for Down from the top navigation bar.
    val heroPlayFocus = contentFocus ?: fallbackFocus
    val scope = rememberCoroutineScope()
    var pendingPlay by remember { mutableStateOf<PlayerRequest?>(null) }

    when {
        state.loading && state.feed == null -> Box(
            Modifier
                .fillMaxSize()
                .focusRequester(heroPlayFocus)
                .focusable(),
        ) {
            TvHomeSkeleton()
        }

        state.error != null && state.feed == null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvSizes.PagePadding),
            contentAlignment = Alignment.Center,
        ) {
            TvError(
                title = "WatchAll is offline",
                message = state.error.orEmpty(),
                primaryLabel = "Try again",
                onPrimary = { viewModel.refresh() },
                primaryFocus = contentFocus,
            )
        }

        state.feed != null -> {
            val feed = state.feed!!
            val hero = feed.featured.firstOrNull()
            val continueFocus = if (hero == null) contentFocus else null
            val firstRowFocus = if (hero == null && state.continueWatching.isEmpty()) contentFocus else null

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = TvSizes.PagePadding,
                    end = TvSizes.PagePadding,
                    top = 16.dp,
                    bottom = 36.dp,
                ),
            ) {
                if (hero != null) {
                    item(key = "hero") {
                        TvHero(
                            media = hero,
                            onOpenDetails = { onOpenDetails(hero) },
                            onPlay = {
                                if (hero.mediaType == MediaType.TV) {
                                    onOpenDetails(hero)
                                } else {
                                    pendingPlay = hero.toPlayerRequest()
                                }
                            },
                            playFocus = heroPlayFocus,
                        )
                    }
                }

                if (state.continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        Column {
                            TvSectionHeader(
                                title = "Continue watching",
                                eyebrow = "Right where you left off",
                            )
                            TvContinueRow(
                                entries = state.continueWatching,
                                onResume = onResume,
                                firstFocusRequester = continueFocus,
                            )
                        }
                    }
                }

                feed.rows.forEachIndexed { index, row ->
                    if (row.items.isNotEmpty()) {
                        item(key = row.id) {
                            Column {
                                TvSectionHeader(
                                    title = row.title,
                                    eyebrow = row.eyebrow,
                                    action = if (row.query != null) {
                                        {
                                            TvViewMoreButton(
                                                onClick = { onViewMore(row) },
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                                TvPosterRow(
                                    items = row.items,
                                    onOpen = onOpenDetails,
                                    ranked = row.ranked,
                                    landscape = index == 0 || row.ranked,
                                    firstFocusRequester = if (index == 0) firstRowFocus else null,
                                )
                            }
                        }
                    }
                }

                item(key = "footer") {
                    Spacer(Modifier.height(22.dp))
                    Text(
                        text = "WatchAll · Your private cinema, no interruptions",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvColors.TextMuted.copy(alpha = 0.52f),
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
                    runCatching { heroPlayFocus.requestFocus() }
                }
            },
        )
    }
}

@Composable
private fun TvHero(
    media: MediaSummary,
    onOpenDetails: () -> Unit,
    onPlay: () -> Unit,
    playFocus: androidx.compose.ui.focus.FocusRequester?,
) {
    // A 16:9 hero at 320 dp leaves room for the first row to peek in, which is
    // the standard 10-foot pattern used by Prime Video and Netflix.
    val heroHeight = 320.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        TvRemoteImage(
            path = media.backdropPath ?: media.posterPath,
            contentDescription = media.displayTitle,
            size = "w1280",
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TvColors.Ink.copy(alpha = 0.97f),
                        0.38f to TvColors.Ink.copy(alpha = 0.80f),
                        0.78f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.20f),
                        0.52f to Color.Transparent,
                        1f to TvColors.Ink.copy(alpha = 0.96f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.66f)
                .padding(start = 30.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = "FEATURED TONIGHT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = TvColors.Cyan,
                letterSpacing = 1.7.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = media.displayTitle,
                style = MaterialTheme.typography.displaySmall,
                color = TvColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (media.rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = null,
                            tint = TvColors.Amber,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = Formatters.rating(media.rating),
                            style = MaterialTheme.typography.labelMedium,
                            color = TvColors.Amber,
                        )
                    }
                }
                if (media.year.isNotBlank()) TvPill(media.year)
                TvPill(media.mediaType.label)
                if (media.originalLanguage.isNotBlank()) {
                    TvPill(Formatters.languageName(media.originalLanguage))
                }
            }
            if (media.overview.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = media.overview,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = TvColors.Text.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.94f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvActionButton(
                    label = if (media.mediaType == MediaType.TV) "View episodes" else "Play now",
                    onClick = onPlay,
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    focusRequester = playFocus,
                )
                TvActionButton(
                    label = "More info",
                    onClick = onOpenDetails,
                    icon = Icons.Outlined.Info,
                )
            }
        }
    }
}
