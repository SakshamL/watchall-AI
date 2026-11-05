package com.watchall.mobile.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.watchall.core.data.Formatters
import com.watchall.core.data.ImageUrls
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.ResumeEntry
import com.watchall.mobile.ui.theme.WatchColors

@Composable
fun RemoteImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: String = "w500",
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(WatchColors.CardHigh, WatchColors.Card, WatchColors.DeepInk),
            ),
        ),
    ) {
        AsyncImage(
            model = ImageUrls.poster(path, size),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun BrandLockup(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 36.dp)
                .clip(RoundedCornerShape(if (compact) 9.dp else 11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(WatchColors.Cyan, WatchColors.Teal, WatchColors.Violet),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = WatchColors.Ink,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "WatchAll",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = WatchColors.Text,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    eyebrow: String = "",
    onSeeAll: (() -> Unit)? = null,
    actionLabel: String = "View More",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow.isNotBlank()) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchColors.Cyan,
                    letterSpacing = 1.3.sp,
                )
                Spacer(Modifier.height(3.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = WatchColors.Text,
            )
        }
        if (onSeeAll != null) {
            Surface(
                onClick = onSeeAll,
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = WatchColors.TextMuted,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = WatchColors.TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0) return
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(WatchColors.Amber.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = WatchColors.Amber,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = Formatters.rating(rating),
            style = MaterialTheme.typography.labelSmall,
            color = WatchColors.Amber,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.075f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = WatchColors.Text.copy(alpha = 0.82f),
    )
}

@Composable
fun PosterCard(
    media: MediaSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 136.dp,
    rank: Int? = null,
) {
    val sizedModifier = if (width == null) modifier.fillMaxWidth() else modifier.width(width)
    Column(
        modifier = sizedModifier
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(17.dp)),
        ) {
            RemoteImage(
                path = media.posterPath,
                contentDescription = media.displayTitle,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                        ),
                    ),
            )
            if (media.mediaType == MediaType.TV) {
                TagPill(
                    text = "SERIES",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
            if (media.rating > 0) {
                RatingPill(
                    rating = media.rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
            if (rank != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                            ),
                        )
                        .padding(start = 9.dp, bottom = 5.dp),
                ) {
                    Text(
                        text = "TOP 10",
                        style = MaterialTheme.typography.labelSmall,
                        color = WatchColors.Cyan,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        lineHeight = 38.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.titleSmall,
            color = WatchColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = listOf(media.year, media.mediaType.label).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = WatchColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaSummary>,
    onOpen: (MediaSummary) -> Unit,
    eyebrow: String = "",
    ranked: Boolean = false,
    onViewMore: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            eyebrow = eyebrow,
            onSeeAll = onViewMore,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            itemsIndexed(items, key = { _, media -> media.key }) { index, media ->
                PosterCard(
                    media = media,
                    onClick = { onOpen(media) },
                    rank = if (ranked) index + 1 else null,
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    entry: ResumeEntry,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(238.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(WatchColors.Card)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            RemoteImage(
                path = entry.backdropPath ?: entry.posterPath,
                contentDescription = entry.title,
                size = "w780",
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.46f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Resume ${entry.title}",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (entry.progress > 0f) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = WatchColors.Cyan,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    drawStopIndicator = {},
                )
            }
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${entry.title} from Continue watching",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = WatchColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = WatchColors.Text,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchColors.TextMuted,
                )
            }
        }
        action?.invoke()
    }
}

/** Branded loading mark shared by every first-load and buffering panel. */
@Composable
fun WatchLoaderMark(size: Dp = 54.dp) {
    val transition = rememberInfiniteTransition(label = "watch-loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "watch-loader-rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "watch-loader-pulse",
    )

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            val stroke = size.toPx() * 0.045f
            drawArc(
                color = WatchColors.Cyan,
                startAngle = 12f,
                sweepAngle = 108f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = WatchColors.Teal.copy(alpha = 0.55f),
                startAngle = 150f,
                sweepAngle = 66f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = WatchColors.Violet.copy(alpha = 0.12f),
                radius = this.size.minDimension * 0.34f,
                center = center,
            )
        }
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(RoundedCornerShape(size * 0.18f))
                .background(
                    Brush.linearGradient(
                        listOf(WatchColors.Cyan, WatchColors.Teal, WatchColors.Violet),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = WatchColors.Ink,
                modifier = Modifier.size(size * 0.36f),
            )
        }
    }
}

@Composable
fun LoadingPanel(message: String = "Loading WatchAll…") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WatchLoaderMark()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "WATCHALL",
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.Cyan,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = WatchColors.TextMuted,
            )
        }
    }
}

@Composable
fun ErrorPanel(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(WatchColors.Coral.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = WatchColors.Coral,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = WatchColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = WatchColors.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onPrimary,
            colors = ButtonDefaults.buttonColors(
                containerColor = WatchColors.Cyan,
                contentColor = WatchColors.Ink,
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
        ) {
            Text(primaryLabel, fontWeight = FontWeight.Bold)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSecondary,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(secondaryLabel, color = WatchColors.Text)
            }
        }
    }
}

@Composable
fun EmptyPanel(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(WatchColors.CardHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = WatchColors.Cyan, modifier = Modifier.size(29.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = WatchColors.Text)
        Spacer(Modifier.height(7.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = WatchColors.TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FilledTonalButton(onClick = onAction, shape = CircleShape) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.075f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = WatchColors.Text, modifier = Modifier.size(22.dp))
    }
}
