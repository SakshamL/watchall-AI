package com.watchall.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.watchall.core.data.Formatters
import com.watchall.core.data.ImageUrls
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.WatchedEntry
import com.watchall.tv.ui.theme.TvColors

/**
 * Compact 10-foot sizing. Android TV reports about 960 x 540 dp at 1080p, so a
 * 120 dp poster leaves room for six posters plus a peek of the next, a hero,
 * and several rows on one screen. The previous 172-204 dp cards only fit four
 * and felt oversized.
 */
object TvSizes {
    val PosterWidth = 118.dp
    val PosterGap = 14.dp
    val ContinueWidth = 284.dp
    val ContinueHeight = 98.dp
    val PagePadding = 32.dp
}

/** The focus target the selected nav tab should move to when Down is pressed. */
val LocalTvContentFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

/** Kept for screens that want to send focus back to the selected nav tab. */
val LocalTvNavFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
fun TvRemoteImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: String = "w500",
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(TvColors.CardHigh, TvColors.Card, TvColors.DeepInk)),
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

/**
 * Shared focus surface for every remote-controlled button and card.
 *
 * The old version animated an 18 dp coloured shadow and used a spring, which
 * forced the TV GPU to redraw blurred layers on every D-pad move. This version
 * only scales and draws a 2 dp border over 110 ms, so focus feels immediate and
 * stays cheap even on low-end boxes.
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    scaleOnFocus: Float = 1.05f,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = TvColors.CardHigh,
    containerBrush: Brush? = null,
    focusedContainerBrush: Brush? = null,
    borderColor: Color = Color.White.copy(alpha = 0.09f),
    focusBorderColor: Color = TvColors.Cyan,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "tv-focus-scale",
    )
    val bringIntoView = remember { BringIntoViewRequester() }

    LaunchedEffect(focused) {
        if (focused && bringIntoViewOnFocus) {
            runCatching { bringIntoView.bringIntoView() }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                    onFocusChange?.invoke(it.isFocused)
                }
            }
            .focusProperties {
                if (!enabled) canFocus = false
                if (leftFocusRequester != null) {
                    left = leftFocusRequester
                }
                if (rightFocusRequester != null) {
                    right = rightFocusRequester
                }
                if (upFocusRequester != null) {
                    up = upFocusRequester
                }
                if (downFocusRequester != null) {
                    down = downFocusRequester
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(
                brush = if (focused) {
                    focusedContainerBrush ?: SolidColor(focusedContainerColor)
                } else {
                    containerBrush ?: SolidColor(containerColor)
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) focusBorderColor else borderColor,
                shape = shape,
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            )
            .padding(2.dp),
    ) {
        content()
    }
}

@Composable
fun TvPosterCard(
    media: MediaSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = TvSizes.PosterWidth,
    rank: Int? = null,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = false,
) {
    val columnModifier = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Column(modifier = columnModifier) {
        TvFocusable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(10.dp),
            focusRequester = focusRequester,
            scaleOnFocus = 1.05f,
            onFocusChange = onFocusChange,
            leftFocusRequester = leftFocusRequester,
            rightFocusRequester = rightFocusRequester,
            upFocusRequester = upFocusRequester,
            downFocusRequester = downFocusRequester,
            bringIntoViewOnFocus = bringIntoViewOnFocus,
        ) {
            Box(Modifier.fillMaxSize()) {
                TvRemoteImage(
                    path = media.posterPath,
                    contentDescription = media.displayTitle,
                    size = "w342",
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.26f)),
                            ),
                        ),
                )
                if (media.rating > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = TvColors.Amber,
                            modifier = Modifier.size(9.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = Formatters.rating(media.rating),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                            color = TvColors.Amber,
                        )
                    }
                }
                if (rank != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                                ),
                            )
                            .padding(start = 8.dp, bottom = 5.dp),
                    ) {
                        Text(
                            text = "TOP 10",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                            color = TvColors.Cyan,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 27.sp,
                                lineHeight = 28.sp,
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.labelLarge,
            color = TvColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOf(media.year, media.mediaType.label).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = TvColors.TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
fun TvContinueCard(
    entry: ResumeEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    leftFocusRequester: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier
            .width(TvSizes.ContinueWidth)
            .height(TvSizes.ContinueHeight),
        shape = RoundedCornerShape(11.dp),
        focusRequester = focusRequester,
        scaleOnFocus = 1.035f,
        onFocusChange = onFocusChange,
        leftFocusRequester = leftFocusRequester,
        bringIntoViewOnFocus = bringIntoViewOnFocus,
        containerColor = TvColors.Card,
        focusedContainerColor = TvColors.CardHigh,
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(134.dp)
                    .height(TvSizes.ContinueHeight),
            ) {
                TvRemoteImage(
                    path = entry.backdropPath ?: entry.posterPath,
                    contentDescription = entry.title,
                    size = "w500",
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, TvColors.Card.copy(alpha = 0.82f)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Resume ${entry.title}",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (entry.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = TvColors.Cyan,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        drawStopIndicator = {},
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 9.dp),
            ) {
                Text(
                    text = entry.subtitle.ifBlank { entry.mediaType.label },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = TvColors.Cyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TvColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (entry.progress > 0f) {
                        "${(entry.progress * 100).toInt()}% watched"
                    } else {
                        "Resume"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = TvColors.TextMuted,
                )
            }
        }
    }
}

@Composable
fun TvBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    Brush.linearGradient(listOf(TvColors.Cyan, TvColors.Teal, TvColors.Violet)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TvColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = "WatchAll",
            style = MaterialTheme.typography.titleLarge,
            color = TvColors.Text,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun TvPageHeader(
    title: String,
    subtitle: String = "",
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvHeadingAccent(
            height = if (subtitle.isBlank()) 34.dp else 50.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun TvHeadingAccent(height: Dp) {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(listOf(TvColors.Cyan, TvColors.Teal)),
            ),
    )
}

@Composable
fun TvSectionHeader(
    title: String,
    eyebrow: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvHeadingAccent(
            height = if (eyebrow.isBlank()) 28.dp else 43.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TvColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (eyebrow.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = TvColors.TextMuted.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.9.sp,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun TvViewMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "View More",
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        scaleOnFocus = 1.03f,
        containerColor = TvColors.Card.copy(alpha = 0.72f),
        focusedContainerColor = TvColors.CardHigh,
        borderColor = Color.White.copy(alpha = 0.08f),
        onFocusChange = { focused = it },
    ) {
        Row(
            modifier = Modifier
                // wrapContentWidth (not fillMaxSize) is essential here: a
                // full-size row makes the button claim all of the header's
                // width when it sits in a Row next to a weighted title column,
                // which squeezed the section titles down to zero width.
                .fillMaxHeight()
                .wrapContentWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) TvColors.Cyan else TvColors.Text.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (focused) TvColors.Cyan else TvColors.TextMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onFocusChange: ((Boolean) -> Unit)? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val primaryBrush = Brush.linearGradient(
        listOf(Color(0xFF7CEEFF), TvColors.Cyan, TvColors.Teal),
    )
    val primaryFocusedBrush = Brush.linearGradient(
        listOf(Color(0xFFD5FBFF), Color(0xFF8DF5FF), TvColors.Cyan),
    )
    val contentColor = when {
        !enabled -> TvColors.TextMuted.copy(alpha = 0.62f)
        primary -> TvColors.Ink
        focused -> TvColors.Cyan
        else -> TvColors.Text
    }

    TvFocusable(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(9.dp),
        focusRequester = focusRequester,
        enabled = enabled,
        scaleOnFocus = if (primary) 1.045f else 1.035f,
        leftFocusRequester = leftFocusRequester,
        rightFocusRequester = rightFocusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        bringIntoViewOnFocus = bringIntoViewOnFocus,
        containerColor = when {
            !enabled -> TvColors.Card.copy(alpha = 0.46f)
            primary -> Color.Transparent
            else -> TvColors.Card.copy(alpha = 0.82f)
        },
        focusedContainerColor = TvColors.CardHigh,
        containerBrush = if (primary && enabled) primaryBrush else null,
        focusedContainerBrush = if (primary) primaryFocusedBrush else null,
        borderColor = when {
            !enabled -> Color.White.copy(alpha = 0.06f)
            primary -> TvColors.Cyan.copy(alpha = 0.32f)
            else -> Color.White.copy(alpha = 0.09f)
        },
        focusBorderColor = if (primary) Color.White else TvColors.Cyan,
        onFocusChange = {
            focused = it
            onFocusChange?.invoke(it)
        },
    ) {
        Row(
            modifier = Modifier
                // Wrap the label instead of filling the parent: when these
                // buttons are placed in a plain Row (hero, details, footer)
                // fillMaxSize made the first button swallow the whole width
                // and pushed every following button to zero width.
                .fillMaxHeight()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                ),
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.height(if (compact) 30.dp else 34.dp),
        shape = RoundedCornerShape(8.dp),
        focusRequester = focusRequester,
        scaleOnFocus = 1.035f,
        containerColor = if (selected) TvColors.Cyan.copy(alpha = 0.13f) else TvColors.Card.copy(alpha = 0.82f),
        focusedContainerColor = if (selected) TvColors.Cyan.copy(alpha = 0.19f) else TvColors.CardHigh,
        borderColor = if (selected) TvColors.Cyan.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f),
        onFocusChange = { focused = it },
        leftFocusRequester = leftFocusRequester,
        rightFocusRequester = rightFocusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
    ) {
        Box(
            modifier = Modifier
                // Same fix as above: chips must size to their label so a
                // second chip in a Row is not squeezed out of view.
                .fillMaxHeight()
                .wrapContentWidth()
                .padding(horizontal = if (compact) 13.dp else 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    selected -> TvColors.Cyan
                    focused -> TvColors.Text
                    else -> TvColors.TextMuted
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Animated WatchAll mark: a rotating dual arc around the gradient play tile. */
@Composable
fun TvLoaderMark(modifier: Modifier = Modifier, size: Dp = 58.dp) {
    val transition = rememberInfiniteTransition(label = "tv-loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tv-loader-rotation",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tv-loader-pulse",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation },
        ) {
            val stroke = size.toPx() * 0.045f
            drawArc(
                color = TvColors.Cyan,
                startAngle = 12f,
                sweepAngle = 108f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = TvColors.Teal.copy(alpha = 0.55f),
                startAngle = 150f,
                sweepAngle = 66f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = TvColors.Violet.copy(alpha = 0.12f),
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
                .clip(RoundedCornerShape(size * 0.17f))
                .background(Brush.linearGradient(listOf(TvColors.Cyan, TvColors.Teal, TvColors.Violet))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TvColors.Ink,
                modifier = Modifier.size(size * 0.36f),
            )
        }
    }
}

@Composable
fun TvLoading(message: String = "Loading…") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TvLoaderMark()
            Spacer(Modifier.height(18.dp))
            Text(
                text = "WATCHALL",
                style = MaterialTheme.typography.labelSmall,
                color = TvColors.Cyan,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.TextMuted,
            )
        }
    }
}

/**
 * First-load skeleton for Home. One pulse animation drives the whole block, so
 * the TV is not running dozens of shimmer transitions while the feed loads.
 */
@Composable
fun TvHomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "home-skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home-skeleton-pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(listOf(TvColors.CardHigh, TvColors.Card, TvColors.DeepInk)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            TvLoaderMark(size = 54.dp)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.graphicsLayer { alpha = pulse }) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TvColors.CardHigh),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) {
                    Column(Modifier.width(120.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TvColors.Card),
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(9.dp)
                                .clip(CircleShape)
                                .background(TvColors.CardHigh),
                        )
                    }
                }
            }
        }
    }
}

/** Compact poster skeleton used by Browse, Search, and collections. */
@Composable
fun TvGridSkeleton(modifier: Modifier = Modifier, rows: Int = 2) {
    val transition = rememberInfiniteTransition(label = "grid-skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "grid-skeleton-pulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(7) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TvColors.Card),
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(9.dp)
                                .clip(CircleShape)
                                .background(TvColors.CardHigh),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvError(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryFocus: FocusRequester? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TvColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (secondaryLabel != null && onSecondary != null) {
                TvActionButton(label = secondaryLabel, onClick = onSecondary)
            }
            TvActionButton(
                label = primaryLabel,
                onClick = onPrimary,
                primary = true,
                focusRequester = primaryFocus,
            )
        }
    }
}

@Composable
fun TvEmpty(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionFocus: FocusRequester? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TvColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TvColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            TvActionButton(
                label = actionLabel,
                onClick = onAction,
                primary = true,
                focusRequester = actionFocus,
            )
        }
    }
}

@Composable
fun TvPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TvColors.Card.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
        color = TvColors.Text.copy(alpha = 0.84f),
        maxLines = 1,
    )
}

@Composable
fun TvPosterRow(
    items: List<MediaSummary>,
    onOpen: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    ranked: Boolean = false,
    landscape: Boolean = false,
    firstFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (landscape) 14.dp else TvSizes.PosterGap),
    ) {
        itemsIndexed(items, key = { _, media -> media.key }) { index, media ->
            if (landscape) {
                TvLandscapeCard(
                    media = media,
                    onClick = { onOpen(media) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            } else {
                TvPosterCard(
                    media = media,
                    onClick = { onOpen(media) },
                    width = TvSizes.PosterWidth,
                    rank = if (ranked && index < 10) index + 1 else null,
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            }
        }
    }
}

@Composable
fun TvContinueRow(
    entries: List<ResumeEntry>,
    onResume: (ResumeEntry) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    if (entries.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.mediaKey + entry.updatedAt }) { index, entry ->
            TvContinueCard(
                entry = entry,
                onClick = { onResume(entry) },
                focusRequester = if (index == 0) firstFocusRequester else null,
            )
        }
    }
}

@Composable
fun TvWatchedCard(
    entry: WatchedEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        onLongClick = onRemove,
        modifier = modifier.width(148.dp),
        shape = RoundedCornerShape(11.dp),
        focusRequester = focusRequester,
        scaleOnFocus = 1.035f,
        onFocusChange = { focused = it },
        containerColor = TvColors.Card,
        focusedContainerColor = TvColors.CardHigh,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                TvRemoteImage(
                    path = entry.posterPath ?: entry.backdropPath,
                    contentDescription = entry.displayTitle,
                    size = "w342",
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(TvColors.Cyan.copy(alpha = 0.94f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (focused) {
                    Text(
                        text = "Hold OK to remove",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            Column(Modifier.padding(start = 9.dp, end = 8.dp, top = 7.dp, bottom = 8.dp)) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = TvColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TvColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun TvWatchedRow(
    entries: List<WatchedEntry>,
    onOpen: (WatchedEntry) -> Unit,
    onRemove: (WatchedEntry) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    if (entries.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.mediaKey }) { index, entry ->
            TvWatchedCard(
                entry = entry,
                onClick = { onOpen(entry) },
                onRemove = { onRemove(entry) },
                focusRequester = if (index == 0) firstFocusRequester else null,
            )
        }
    }
}

@Composable
fun TvPosterGrid(
    items: List<MediaSummary>,
    onOpen: (MediaSummary) -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    firstItemUpFocusRequester: FocusRequester? = null,
    minColumnWidth: Dp = 118.dp,
    landscape: Boolean = false,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    footer: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = if (landscape) 240.dp else minColumnWidth),
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items, key = { _, media -> media.key }) { index, media ->
            if (landscape) {
                TvLandscapeCard(
                    media = media,
                    onClick = { onOpen(media) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            } else {
                TvPosterCard(
                    media = media,
                    onClick = { onOpen(media) },
                    width = null,
                    focusRequester = if (index == 0) firstFocusRequester else null,
                    upFocusRequester = if (index == 0) firstItemUpFocusRequester else null,
                )
            }
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                footer()
            }
        }
    }
}

@Composable
fun TvLandscapeCard(
    media: MediaSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    focusRequester: FocusRequester? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.width(width)) {
        TvFocusable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(10.dp),
            focusRequester = focusRequester,
            scaleOnFocus = 1.04f,
            onFocusChange = { focused = it; onFocusChange?.invoke(it) },
            containerColor = TvColors.Card,
            focusedContainerColor = TvColors.CardHigh,
        ) {
            Box(Modifier.fillMaxSize()) {
                TvRemoteImage(
                    path = media.backdropPath ?: media.posterPath,
                    contentDescription = media.displayTitle,
                    size = "w500",
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )
                if (media.rating > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = TvColors.Amber, modifier = Modifier.size(9.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(text = Formatters.rating(media.rating), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TvColors.Amber)
                    }
                }
                Text(
                    text = media.displayTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOf(media.year, media.mediaType.label).filter { it.isNotBlank() }.joinToString(" � "),
            style = MaterialTheme.typography.labelSmall,
            color = TvColors.TextMuted,
            maxLines = 1,
        )
    }
}
