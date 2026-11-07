package com.watchall.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watchall.core.vm.SettingsViewModel
import com.watchall.tv.BuildConfig
import com.watchall.tv.ui.components.LocalTvContentFocusRequester
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvChip
import com.watchall.tv.ui.components.TvPageHeader
import com.watchall.tv.ui.components.TvSizes
import com.watchall.tv.ui.theme.TvColors

@Composable
fun TvSettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val fallbackFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }
    // Down from the navigation bar should land on Save & test, not on the
    // editable field (focusing a text field opens the on-screen keyboard).
    val saveFocus = LocalTvContentFocusRequester.current ?: fallbackFocus
    val testFocus = remember { FocusRequester() }
    var address by rememberSaveable { mutableStateOf(state.settings.apiBaseUrl) }

    LaunchedEffect(state.settings.apiBaseUrl) {
        address = state.settings.apiBaseUrl
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = TvSizes.PagePadding, end = TvSizes.PagePadding, top = 16.dp, bottom = 28.dp),
    ) {
        TvPageHeader(
            title = "Settings",
            subtitle = "Connect WatchAll to your private server.",
        )

        SettingsPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = TvColors.Cyan,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Server address",
                        style = MaterialTheme.typography.titleMedium,
                        color = TvColors.Text,
                    )
                    Text(
                        text = if (state.settings.usesDefaultServer) {
                            "Using the default address"
                        } else {
                            "Using a custom address"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .focusRequester(addressFocus),
                label = { Text("Server address") },
                placeholder = { Text("http://192.168.1.4:3001") },
                singleLine = true,
                shape = RoundedCornerShape(11.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.save(address)
                        focusManager.clearFocus()
                        runCatching { saveFocus.requestFocus() }
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TvColors.Cyan.copy(alpha = 0.75f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.09f),
                    focusedContainerColor = TvColors.DeepInk,
                    unfocusedContainerColor = TvColors.DeepInk,
                    cursorColor = TvColors.Cyan,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvActionButton(
                    label = if (state.testing) "Testing…" else "Save & test",
                    onClick = {
                        viewModel.save(address)
                        focusManager.clearFocus()
                        runCatching { saveFocus.requestFocus() }
                    },
                    primary = true,
                    focusRequester = saveFocus,
                    enabled = !state.testing,
                )
                TvActionButton(
                    label = "Test connection",
                    onClick = {
                        viewModel.test()
                        focusManager.clearFocus()
                        runCatching { testFocus.requestFocus() }
                    },
                    focusRequester = testFocus,
                    enabled = !state.testing,
                )
                if (!state.settings.usesDefaultServer) {
                    TvChip(
                        label = "Reset",
                        selected = false,
                        onClick = {
                            viewModel.reset()
                            focusManager.clearFocus()
                            runCatching { saveFocus.requestFocus() }
                        },
                    )
                }
            }
            if (state.testing) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = TvColors.Cyan,
                        strokeWidth = 1.8.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Checking the server…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.TextMuted,
                    )
                }
            }
            state.status?.let { status ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .background(
                            (if (state.statusIsError) TvColors.Coral else TvColors.Success).copy(alpha = 0.10f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (state.statusIsError) {
                            Icons.Outlined.ErrorOutline
                        } else {
                            Icons.Outlined.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (state.statusIsError) TvColors.Coral else TvColors.Success,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                        color = if (state.statusIsError) TvColors.Coral else TvColors.Success,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsPanel(Modifier.weight(1f)) {
                Text(
                    "Server List 1",
                    style = MaterialTheme.typography.titleSmall,
                    color = TvColors.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recommended. Movies and TV shows, with multiple players inside.",
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                    color = TvColors.TextMuted,
                )
            }
            SettingsPanel(Modifier.weight(1f)) {
                Text(
                    "Server List 2",
                    style = MaterialTheme.typography.titleSmall,
                    color = TvColors.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Alternative movie source. TV shows are only available on Server List 1.",
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                    color = TvColors.TextMuted,
                )
            }
            SettingsPanel(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = TvColors.TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Private",
                        style = MaterialTheme.typography.titleSmall,
                        color = TvColors.Text,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "No accounts, ads, or cloud profiles. History stays on this device.",
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp),
                    color = TvColors.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "WatchAll TV ${BuildConfig.VERSION_NAME} · private sideload build",
            style = MaterialTheme.typography.labelSmall,
            color = TvColors.TextMuted.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = TvColors.Card,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}
