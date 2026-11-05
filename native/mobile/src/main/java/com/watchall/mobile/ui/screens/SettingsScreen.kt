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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.vm.SettingsViewModel
import com.watchall.mobile.BuildConfig
import com.watchall.mobile.ui.components.ScreenTitle
import com.watchall.mobile.ui.theme.WatchColors

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var address by rememberSaveable { mutableStateOf(state.settings.apiBaseUrl) }

    LaunchedEffect(state.settings.apiBaseUrl) {
        address = state.settings.apiBaseUrl
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset, start = 20.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle(
                title = "Settings",
                subtitle = "Make WatchAll work on your network",
            )
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(WatchColors.Cyan.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Dns, contentDescription = null, tint = WatchColors.Cyan)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WatchAll server",
                            style = MaterialTheme.typography.titleMedium,
                            color = WatchColors.Text,
                        )
                        Text(
                            text = if (state.settings.usesDefaultServer) "Using the default address" else "Custom address",
                            style = MaterialTheme.typography.labelSmall,
                            color = WatchColors.TextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server address") },
                    placeholder = { Text("http://192.168.1.10:3001") },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.save(address)
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WatchColors.Cyan.copy(alpha = 0.65f),
                        unfocusedBorderColor = WatchColors.Hairline,
                        focusedContainerColor = WatchColors.DeepInk,
                        unfocusedContainerColor = WatchColors.DeepInk,
                        cursorColor = WatchColors.Cyan,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.save(address)
                        },
                        enabled = !state.testing,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WatchColors.Cyan,
                            contentColor = WatchColors.Ink,
                        ),
                    ) {
                        if (state.testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = WatchColors.Ink,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save & test", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.test()
                        },
                        enabled = !state.testing,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test")
                    }
                }
                if (!state.settings.usesDefaultServer) {
                    Text(
                        text = "Reset to default",
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(CircleShape)
                            .clickable {
                                focusManager.clearFocus()
                                viewModel.reset()
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = WatchColors.Cyan,
                    )
                }
                state.status?.let { status ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                (if (state.statusIsError) WatchColors.Coral else WatchColors.Success)
                                    .copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = if (state.statusIsError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (state.statusIsError) WatchColors.Coral else WatchColors.Success,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.statusIsError) WatchColors.Coral else WatchColors.Success,
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                InfoRow(
                    title = "Server List 1",
                    description = "Recommended. Works with movies and TV shows, and offers multiple players.",
                    tag = "Default",
                )
                Spacer(Modifier.height(14.dp))
                InfoRow(
                    title = "Server List 2",
                    description = "Alternative source for movies. TV shows are only available on Server List 1.",
                    tag = "Movies",
                )
            }
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = WatchColors.TextMuted)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Private by design",
                        style = MaterialTheme.typography.titleSmall,
                        color = WatchColors.Text,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "WatchAll has no accounts, ads or cloud profiles. Titles come from TMDB, and playback runs through your own server. Watch history stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchColors.TextMuted,
                    lineHeight = 19.sp,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "WatchAll ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = WatchColors.TextMuted,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "A personal cinema project",
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchColors.TextMuted.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = WatchColors.Card,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoRow(title: String, description: String, tag: String) {
    Row {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WatchColors.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = WatchColors.TextMuted,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = tag,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.07f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = WatchColors.TextMuted,
        )
    }
}
