package com.watchall.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watchall.core.data.MediaType
import com.watchall.core.data.SourceList
import com.watchall.mobile.ui.theme.WatchColors

/**
 * Server selection shown before a title starts playing on mobile.
 *
 * This mirrors the TV and web source chooser. Only lists that can play the
 * selected media type are enabled; Server List 2 stays visible but is marked
 * as movies-only when a TV episode is opened.
 */
@Composable
fun SourcePickerDialog(
    mediaType: MediaType,
    initialSource: SourceList = SourceList.SERVER_ONE,
    onSelect: (SourceList) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = remember(mediaType) { SourceList.available(mediaType).toSet() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = WatchColors.Surface,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(
                    text = "WATCHALL PLAYBACK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    ),
                    color = WatchColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Choose a server",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = WatchColors.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (mediaType == MediaType.TV) {
                        "TV shows play from Server List 1. Server List 2 is for movies only."
                    } else {
                        "Pick where WatchAll should look for this stream."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = WatchColors.TextMuted,
                )
                Spacer(Modifier.height(16.dp))

                SourceList.entries.forEachIndexed { index, source ->
                    val enabled = source in available
                    val selected = source == initialSource && enabled
                    val subtitle = when {
                        !enabled -> "Movies only · not available for TV shows"
                        source == SourceList.SERVER_ONE -> "Recommended · movies and TV shows"
                        source == SourceList.SERVER_THREE -> "VidSrc · embed player"
                        else -> "Alternative · movies only · can be slower"
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) { onSelect(source) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            WatchColors.Cyan.copy(alpha = 0.10f)
                        } else {
                            WatchColors.Card.copy(alpha = if (enabled) 0.72f else 0.32f)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (enabled) {
                                            WatchColors.Cyan.copy(alpha = 0.12f)
                                        } else {
                                            WatchColors.TextMuted.copy(alpha = 0.08f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (enabled) WatchColors.Cyan else WatchColors.TextMuted.copy(alpha = 0.6f),
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
                                    color = if (enabled) WatchColors.Text else WatchColors.TextMuted.copy(alpha = 0.65f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WatchColors.TextMuted.copy(alpha = if (enabled) 0.9f else 0.55f),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Currently selected",
                                    tint = WatchColors.Cyan,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else if (enabled) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = WatchColors.TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
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
                        color = WatchColors.TextMuted.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = WatchColors.TextMuted)
                    }
                }
            }
        }
    }
}
