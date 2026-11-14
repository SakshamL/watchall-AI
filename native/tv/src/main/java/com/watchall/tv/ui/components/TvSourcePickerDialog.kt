package com.watchall.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watchall.core.data.MediaType
import com.watchall.core.data.SourceList
import com.watchall.tv.ui.theme.TvColors
import kotlinx.coroutines.delay

/**
 * Server selection shown before a title starts.
 *
 * This mirrors the web player's server chooser. Only the lists that can actually
 * play the selected media type are enabled; Server List 2 is visible but clearly
 * marked as movies-only when the user is opening a TV show.
 */
@Composable
fun TvSourcePickerDialog(
    mediaType: MediaType,
    initialSource: SourceList = SourceList.SERVER_ONE,
    onSelect: (SourceList) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val available = remember(mediaType) { SourceList.available(mediaType).toSet() }
    val firstAvailable = remember(mediaType) {
        SourceList.entries.firstOrNull { it in available }
    }

    LaunchedEffect(Unit) {
        delay(90)
        runCatching { firstFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.width(660.dp),
            shape = RoundedCornerShape(16.dp),
            color = TvColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = 26.dp, vertical = 24.dp)) {
                Text(
                    text = "WATCHALL PLAYBACK",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = TvColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Choose a server",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 25.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (mediaType == MediaType.TV) {
                        "Server List 2 is for movies only. Server List 1 and Server List 3 play TV shows."
                    } else {
                        "Pick where WatchAll should look for this stream."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextMuted,
                )
                Spacer(Modifier.height(16.dp))

                SourceList.entries.forEachIndexed { index, source ->
                    val enabled = source in available
                    val selected = source == initialSource && enabled
                    val subtitle = when {
                        !enabled -> "Movies only · not available for TV shows"
                        source == SourceList.SERVER_ONE -> "Recommended · movies and TV shows"
                        source == SourceList.SERVER_THREE -> "Netnaija · movies and TV shows"
                        else -> "Alternative · movies only · can be slower"
                    }
                    TvFocusable(
                        onClick = { if (enabled) onSelect(source) },
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        focusRequester = if (source == firstAvailable) firstFocus else null,
                        scaleOnFocus = 1.01f,
                        containerColor = if (selected) {
                            TvColors.Cyan.copy(alpha = 0.10f)
                        } else {
                            TvColors.Card.copy(alpha = if (enabled) 0.62f else 0.32f)
                        },
                        focusedContainerColor = TvColors.CardHigh,
                        borderColor = if (selected) {
                            TvColors.Cyan.copy(alpha = 0.34f)
                        } else {
                            TvColors.Hairline
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (enabled) {
                                            TvColors.Cyan.copy(alpha = 0.12f)
                                        } else {
                                            TvColors.TextMuted.copy(alpha = 0.08f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (enabled) TvColors.Cyan else TvColors.TextMuted.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 21.sp,
                                    ),
                                    color = if (enabled) TvColors.Text else TvColors.TextMuted.copy(alpha = 0.65f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TvColors.TextMuted.copy(alpha = if (enabled) 0.9f else 0.55f),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Currently selected",
                                    tint = TvColors.Cyan,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else if (enabled) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = TvColors.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (mediaType == MediaType.TV) {
                            "More lists are added over time."
                        } else {
                            "You can switch servers later from the player."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.TextMuted.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    TvActionButton(
                        label = "Cancel",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}
