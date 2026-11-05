package com.watchall.tv.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.watchall.core.data.Formatters
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.SourceList
import com.watchall.core.playback.PlaybackSnapshot
import com.watchall.core.playback.PlaybackController
import com.watchall.core.vm.PlayerUiState
import com.watchall.core.vm.PlayerViewModel
import com.watchall.tv.ui.components.TvActionButton
import com.watchall.tv.ui.components.TvBrand
import com.watchall.tv.ui.components.TvFocusable
import com.watchall.tv.ui.components.TvLoaderMark
import com.watchall.tv.ui.theme.TvColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private object PlayerColors {
    val Accent = Color(0xFF5B4DFF)
    val Accent2 = Color(0xFF2EC4B6)
    val Focus = Color(0xFF72F1E6)
    val Danger = Color(0xFFFF6B81)
}

private enum class TvPlayerSheet {
    SOURCE,
    QUALITY,
    AUDIO,
    SUBTITLES,
    SPEED,
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun TvPlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val controller = remember { PlaybackController(context) }
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(controller) {
        controller.onVariantRequested = { index ->
            viewModel.selectVariant(index, controller.currentPositionMs())
        }
    }
    val snackbarHost = remember { SnackbarHostState() }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    val topBackFocus = remember { FocusRequester() }
    val topSourceFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val audioFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var seekFocused by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<TvPlayerSheet?>(null) }
    var sheetReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var selectedQuality by remember { mutableStateOf<Int?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }

    fun openSheet(next: TvPlayerSheet, returnFocus: FocusRequester?) {
        controlsVisible = true
        sheetReturnFocus = returnFocus
        sheet = next
    }

    fun exitPlayer() {
        viewModel.saveProgressAtExit(controller.currentPositionMs(), snapshot.durationMs)
        onBack()
    }

    BackHandler(enabled = sheet == null) { exitPlayer() }

    DisposableEffect(activity) {
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            viewModel.saveProgressAtExit(controller.currentPositionMs(), controller.snapshot.value.durationMs)
            controller.release()
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(state.stream?.activeVariant?.playbackUrl) {
        val stream = state.stream ?: return@LaunchedEffect
        controlsVisible = true
        controller.load(
            stream = stream,
            startPositionMs = state.startPositionMs,
            title = viewModel.request.title,
        )
    }

    LaunchedEffect(state.loading) {
        if (state.loading) {
            controller.pause()
            controlsVisible = false
        }
    }

    LaunchedEffect(state.stream?.activeVariant?.playbackUrl) {
        if (state.stream == null) return@LaunchedEffect
        while (isActive) {
            delay(5_000)
            val current = controller.snapshot.value
            if (current.positionMs > 0 && current.durationMs > 0) {
                viewModel.saveProgress(current.positionMs, current.durationMs)
            }
        }
    }

    LaunchedEffect(state.stream?.activeVariant?.playbackUrl, snapshot.failure) {
        val failure = snapshot.failure ?: return@LaunchedEffect
        if (!state.loading) viewModel.onPlaybackFailure(failure, controller.currentPositionMs())
    }

    LaunchedEffect(state.notice?.id) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHost.showSnackbar(notice.text)
        viewModel.clearNotice()
    }

    LaunchedEffect(controlsVisible, snapshot.isPlaying, sheet, interactionTick, seekFocused) {
        if (controlsVisible && snapshot.isPlaying && sheet == null) {
            delay(6_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, sheet, state.stream, state.hasError, state.loading) {
        when {
            state.hasError && sheet == null -> {
                delay(220)
                runCatching { errorFocus.requestFocus() }
            }

            sheet != null || state.loading -> Unit

            controlsVisible -> {
                delay(100)
                if (sheetReturnFocus == null) {
                    runCatching { playFocus.requestFocus() }
                }
            }

            else -> runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(sheet) {
        val returnTo = sheetReturnFocus
        if (sheet != null) {
            delay(140)
            runCatching { sheetFocus.requestFocus() }
        } else if (returnTo != null) {
            controlsVisible = true
            delay(150)
            runCatching { returnTo.requestFocus() }
            sheetReturnFocus = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionTick++
                    val mediaKey = event.key in setOf(
                        Key.MediaPlayPause,
                        Key.MediaPlay,
                        Key.MediaPause,
                        Key.MediaFastForward,
                        Key.MediaRewind,
                    )
                    val navigationKey = event.key in setOf(
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionCenter,
                        Key.Enter,
                    )
                    if (!controlsVisible && sheet == null && state.stream != null && !state.hasError && (mediaKey || navigationKey)) {
                        controlsVisible = true
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            controller.togglePlay()
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }

                        Key.MediaFastForward -> {
                            controller.seekBy(10_000)
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }

                        Key.MediaRewind -> {
                            controller.seekBy(-10_000)
                            controlsVisible = true
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.stream != null && !state.hasError) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.68f),
                                    0.28f to Color.Transparent,
                                    0.68f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.86f),
                                ),
                            ),
                    )
                    TvPlayerTopBar(
                        request = viewModel.request,
                        state = state,
                        onBack = { exitPlayer() },
                        backFocus = topBackFocus,
                        sourceFocus = topSourceFocus,
                        playFocus = playFocus,
                        onOpenSource = { openSheet(TvPlayerSheet.SOURCE, topSourceFocus) },
                    )
                    TvPlayerCenter(
                        isPlaying = snapshot.isPlaying,
                        playFocus = playFocus,
                        seekFocus = seekFocus,
                        topFocus = topSourceFocus,
                        onTogglePlay = {
                            interactionTick++
                            controller.togglePlay()
                        },
                        onRewind = {
                            interactionTick++
                            controller.seekBy(-10_000)
                        },
                        onForward = {
                            interactionTick++
                            controller.seekBy(10_000)
                        },
                    )
                    TvPlayerBottom(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        snapshot = snapshot,
                        seekFocus = seekFocus,
                        playFocus = playFocus,
                        actionFocus = actionFocus,
                        subtitlesFocus = subtitlesFocus,
                        audioFocus = audioFocus,
                        qualityFocus = qualityFocus,
                        speedFocus = speedFocus,
                        seekFocused = seekFocused,
                        onSeekFocusChange = {
                            seekFocused = it
                            if (it) interactionTick++
                        },
                        onSeek = { delta ->
                            interactionTick++
                            controller.seekBy(delta)
                        },
                        onTogglePlay = {
                            interactionTick++
                            controller.togglePlay()
                        },
                        onOpenSource = { openSheet(TvPlayerSheet.SOURCE, actionFocus) },
                        onOpenSubtitles = { openSheet(TvPlayerSheet.SUBTITLES, subtitlesFocus) },
                        onOpenAudio = { openSheet(TvPlayerSheet.AUDIO, audioFocus) },
                        onOpenQuality = { openSheet(TvPlayerSheet.QUALITY, qualityFocus) },
                        onOpenSpeed = { openSheet(TvPlayerSheet.SPEED, speedFocus) },
                    )
                }
            }

            if (snapshot.isBuffering && !state.loading) {
                TvLoaderMark(
                    modifier = Modifier.align(Alignment.Center),
                    size = 42.dp,
                )
            }
        }

        if (state.loading) {
            TvPlayerLoading(state = state, request = viewModel.request)
        }

        if (state.hasError) {
            TvPlayerError(
                state = state,
                focusRequester = errorFocus,
                onRetrySource = viewModel::retrySource,
                onRetryPlayback = viewModel::retryPlayback,
                onSwitchList = viewModel::switchList,
                onOpenSource = { openSheet(TvPlayerSheet.SOURCE, null) },
                onBack = { exitPlayer() },
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 142.dp),
        )
    }

    sheet?.let { activeSheet ->
        Dialog(
            onDismissRequest = { sheet = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .width(760.dp)
                    .heightIn(max = 620.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                    ),
                shape = RoundedCornerShape(16.dp),
                color = TvColors.Surface,
            ) {
                TvPlayerSheetContent(
                    sheet = activeSheet,
                    state = state,
                    snapshot = snapshot,
                    selectedQuality = selectedQuality,
                    focusRequester = sheetFocus,
                    onClose = { sheet = null },
                    onSelectQuality = {
                        selectedQuality = it
                        controller.setVideoQuality(it)
                        sheet = null
                    },
                    onSelectAudio = {
                        controller.setAudioTrack(it)
                        sheet = null
                    },
                    onSelectText = {
                        controller.setTextTrack(it)
                        sheet = null
                    },
                    onSelectSpeed = {
                        controller.setSpeed(it)
                        sheet = null
                    },
                    onSelectList = {
                        viewModel.rememberPosition(controller.snapshot.value.positionMs)
                        viewModel.switchList(it)
                        sheet = null
                    },
                    onSelectServer = {
                        viewModel.rememberPosition(controller.snapshot.value.positionMs)
                        viewModel.selectServer(it)
                        sheet = null
                    },
                    onRetryServers = viewModel::loadServers,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerTopBar(
    request: PlayerRequest,
    state: PlayerUiState,
    onBack: () -> Unit,
    backFocus: FocusRequester,
    sourceFocus: FocusRequester,
    playFocus: FocusRequester,
    onOpenSource: () -> Unit,
) {
    var sourceFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
            .padding(horizontal = 40.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.focusProperties {
                right = sourceFocus
                down = playFocus
            },
            focusRequester = backFocus,
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = request.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.63f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(18.dp))
        TvFocusable(
            onClick = onOpenSource,
            modifier = Modifier
                .height(46.dp)
                .focusRequester(sourceFocus)
                .focusProperties {
                    left = backFocus
                    down = playFocus
                },
            shape = RoundedCornerShape(9.dp),
            scaleOnFocus = 1.035f,
            containerColor = Color.Black.copy(alpha = 0.45f),
            focusedContainerColor = TvColors.CardHigh,
            borderColor = Color.White.copy(alpha = 0.10f),
            onFocusChange = { sourceFocused = it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = PlayerColors.Accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.sourceList.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sourceFocused) PlayerColors.Accent else Color.White,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerCenter(
    isPlaying: Boolean,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    topFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPlayerRoundButton(
            icon = Icons.Filled.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = onRewind,
            size = 72,
            focusUp = topFocus,
            focusDown = seekFocus,
        )
        Spacer(Modifier.width(48.dp))
        TvPlayerRoundButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onTogglePlay,
            size = 96,
            filled = true,
            focusRequester = playFocus,
            focusUp = topFocus,
            focusDown = seekFocus,
        )
        Spacer(Modifier.width(48.dp))
        TvPlayerRoundButton(
            icon = Icons.Filled.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = onForward,
            size = 72,
            focusUp = topFocus,
            focusDown = seekFocus,
        )
    }
}

@Composable
private fun TvPlayerRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int,
    filled: Boolean = false,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .then(
                if (focusUp != null || focusDown != null) {
                    Modifier.focusProperties {
                        if (focusUp != null) up = focusUp
                        if (focusDown != null) down = focusDown
                    }
                } else {
                    Modifier
                },
            ),
        shape = CircleShape,
        focusRequester = focusRequester,
        scaleOnFocus = 1.06f,
        containerColor = if (filled) Color.Transparent else Color.Black.copy(alpha = 0.38f),
        focusedContainerColor = if (filled) PlayerColors.Accent else TvColors.CardHigh,
        containerBrush = if (filled) {
            Brush.linearGradient(listOf(PlayerColors.Accent2, PlayerColors.Accent))
        } else {
            null
        },
        focusedContainerBrush = if (filled) {
            Brush.linearGradient(listOf(PlayerColors.Accent, Color(0xFFB8F8FF)))
        } else {
            null
        },
        borderColor = if (filled) PlayerColors.Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.10f),
        focusBorderColor = if (filled) Color.White else PlayerColors.Accent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (filled) TvColors.Ink else Color.White,
                modifier = Modifier.size((size * 0.46f).dp),
            )
        }
    }
}

@Composable
private fun TvPlayerBottom(
    modifier: Modifier = Modifier,
    snapshot: PlaybackSnapshot,
    seekFocus: FocusRequester,
    playFocus: FocusRequester,
    actionFocus: FocusRequester,
    subtitlesFocus: FocusRequester,
    audioFocus: FocusRequester,
    qualityFocus: FocusRequester,
    speedFocus: FocusRequester,
    seekFocused: Boolean,
    onSeekFocusChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(horizontal = 46.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Formatters.time(snapshot.positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.width(14.dp))
            TvSeekBar(
                modifier = Modifier.weight(1f),
                snapshot = snapshot,
                focusRequester = seekFocus,
                upFocusRequester = playFocus,
                downFocusRequester = actionFocus,
                focused = seekFocused,
                onFocusChange = onSeekFocusChange,
                onSeek = onSeek,
                onTogglePlay = onTogglePlay,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = Formatters.time(snapshot.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(start = 2.dp, top = 5.dp),
        ) {
            if (seekFocused) {
                Text(
                    text = "◀ 10 seconds   ·   ▶ 10 seconds   ·   OK play/pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlayerColors.Accent.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvPlayerAction(
                icon = Icons.Outlined.Layers,
                label = "Source",
                onClick = onOpenSource,
                focusRequester = actionFocus,
                upFocusRequester = seekFocus,
            )
            TvPlayerAction(
                icon = Icons.Outlined.Subtitles,
                label = if (snapshot.activeTextId != null) "Subtitles on" else "Subtitles",
                onClick = onOpenSubtitles,
                active = snapshot.activeTextId != null,
                focusRequester = subtitlesFocus,
                upFocusRequester = seekFocus,
            )
            TvPlayerAction(
                icon = Icons.Outlined.Audiotrack,
                label = "Audio",
                onClick = onOpenAudio,
                focusRequester = audioFocus,
                upFocusRequester = seekFocus,
            )
            TvPlayerAction(
                icon = Icons.Outlined.HighQuality,
                label = "Quality",
                onClick = onOpenQuality,
                focusRequester = qualityFocus,
                upFocusRequester = seekFocus,
            )
            TvPlayerAction(
                icon = Icons.Outlined.Speed,
                label = "${snapshot.speed}×",
                onClick = onOpenSpeed,
                focusRequester = speedFocus,
                upFocusRequester = seekFocus,
            )
        }
    }
}

@Composable
private fun TvSeekBar(
    snapshot: PlaybackSnapshot,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (snapshot.durationMs > 0) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val buffered = if (snapshot.durationMs > 0) {
        (snapshot.bufferedMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackHeight by animateDpAsState(
        targetValue = if (focused) 7.dp else 5.dp,
        label = "tv-seek-track",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (focused) 16.dp else 10.dp,
        label = "tv-seek-thumb",
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) PlayerColors.Accent.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.035f),
            )
            .border(
                width = 1.dp,
                color = if (focused) PlayerColors.Accent.copy(alpha = 0.62f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .focusProperties {
                up = upFocusRequester
                down = downFocusRequester
            }
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeek(-10_000)
                        true
                    }

                    Key.DirectionRight -> {
                        onSeek(10_000)
                        true
                    }

                    Key.DirectionCenter, Key.Enter -> {
                        onTogglePlay()
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(28.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val movableWidthPx = with(density) { maxWidth.toPx() } - with(density) { thumbSize.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(buffered)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(PlayerColors.Accent2, PlayerColors.Accent)),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset((movableWidthPx * progress).roundToInt(), 0)
                    }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(PlayerColors.Accent)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            )
        }
    }
}

@Composable
private fun TvPlayerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusable(
        onClick = onClick,
        modifier = Modifier
            .height(50.dp)
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(9.dp),
        focusRequester = focusRequester,
        scaleOnFocus = 1.04f,
        containerColor = if (active) PlayerColors.Accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
        focusedContainerColor = if (active) PlayerColors.Accent.copy(alpha = 0.20f) else TvColors.CardHigh,
        borderColor = if (active) PlayerColors.Accent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f),
        onFocusChange = { focused = it },
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active || focused) PlayerColors.Accent else Color.White.copy(alpha = 0.86f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active || focused) PlayerColors.Accent else Color.White.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun TvPlayerLoading(
    state: PlayerUiState,
    request: PlayerRequest,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(TvColors.Card, TvColors.Ink, Color.Black),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TvBrand()
            Spacer(Modifier.height(34.dp))
            TvLoaderMark(size = 56.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = state.loadingMessage.ifBlank { "Getting your stream ready…" },
                style = MaterialTheme.typography.headlineSmall,
                color = TvColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${request.title} · ${request.subtitle}",
                style = MaterialTheme.typography.bodyLarge,
                color = TvColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = if (state.sourceList == SourceList.SERVER_TWO) {
                    "Server List 2 can take up to 30 seconds to find a stream."
                } else {
                    "Selecting the best available player."
                },
                style = MaterialTheme.typography.labelMedium,
                color = TvColors.TextMuted.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun TvPlayerError(
    state: PlayerUiState,
    focusRequester: FocusRequester,
    onRetrySource: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSwitchList: (SourceList) -> Unit,
    onOpenSource: () -> Unit,
    onBack: () -> Unit,
) {
    val sourceError = state.sourceError
    val playbackFailure = state.playbackFailure
    val other = state.otherSource
    val both = state.bothListsFailed
    val title = sourceError?.title ?: playbackFailure?.title ?: "Playback unavailable"
    val message = buildString {
        append(sourceError?.message ?: playbackFailure?.message ?: "Something went wrong.")
        if (both) {
            append("\n\nBoth server lists could not play this title. You can retry either one.")
        }
    }

    val primaryLabel: String
    val primaryAction: () -> Unit
    val secondaryLabel: String?
    val secondaryAction: (() -> Unit)?

    if (sourceError != null) {
        if (other != null && !both) {
            primaryLabel = "Try ${other.displayName}"
            primaryAction = { onSwitchList(other) }
            secondaryLabel = "Try again"
            secondaryAction = onRetrySource
        } else {
            primaryLabel = "Try again"
            primaryAction = onRetrySource
            secondaryLabel = if (both && other != null) "Try ${other.displayName} again" else null
            secondaryAction = if (both && other != null) ({ onSwitchList(other) }) else null
        }
    } else {
        primaryLabel = "Try again"
        primaryAction = onRetryPlayback
        secondaryLabel = other?.let { if (both) "Try ${it.displayName} again" else "Try ${it.displayName}" }
        secondaryAction = other?.let { { onSwitchList(it) } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TvColors.DeepInk, Color.Black)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = TvColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TvColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (secondaryLabel != null && secondaryAction != null) {
                    TvActionButton(label = secondaryLabel, onClick = secondaryAction)
                }
                TvActionButton(
                    label = primaryLabel,
                    onClick = primaryAction,
                    primary = true,
                    focusRequester = focusRequester,
                )
                TvActionButton(label = "Sources", onClick = onOpenSource)
            }
            Spacer(Modifier.height(22.dp))
            TvActionButton(label = "Back", onClick = onBack)
        }
        Box(Modifier.fillMaxSize()) {
            TvIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(32.dp),
            )
        }
    }
}

@Composable
private fun TvPlayerSheetContent(
    sheet: TvPlayerSheet,
    state: PlayerUiState,
    snapshot: PlaybackSnapshot,
    selectedQuality: Int?,
    focusRequester: FocusRequester,
    onClose: () -> Unit,
    onSelectQuality: (Int?) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectText: (String?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectList: (SourceList) -> Unit,
    onSelectServer: (String) -> Unit,
    onRetryServers: () -> Unit,
) {
    var firstFocus = true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (sheet) {
                        TvPlayerSheet.SOURCE -> "Playback source"
                        TvPlayerSheet.QUALITY -> "Video quality"
                        TvPlayerSheet.AUDIO -> "Audio track"
                        TvPlayerSheet.SUBTITLES -> "Subtitles"
                        TvPlayerSheet.SPEED -> "Playback speed"
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = TvColors.Text,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (sheet == TvPlayerSheet.SOURCE) {
                        "Switch lists without losing your place."
                    } else {
                        "Changes apply immediately."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TvColors.TextMuted,
                )
            }
            Spacer(Modifier.width(16.dp))
            TvIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
            )
        }
        Spacer(Modifier.height(16.dp))

        when (sheet) {
            TvPlayerSheet.SOURCE -> {
                state.availableSources.forEach { source ->
                    val focus = if (firstFocus) focusRequester else null
                    firstFocus = false
                    TvSheetOption(
                        title = source.displayName,
                        subtitle = when (source) {
                            SourceList.SERVER_ONE -> "Recommended · movies and TV shows"
                            SourceList.SERVER_TWO -> "Alternative · movies only · slower"
                        },
                        selected = state.sourceList == source,
                        onClick = { onSelectList(source) },
                        focusRequester = focus,
                    )
                }
                if (state.availableSources.contains(SourceList.SERVER_ONE)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "PLAYERS ON SERVER LIST 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.TextMuted,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (state.serversLoading) {
                        Text(
                            text = "Checking available players…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TvColors.TextMuted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else if (state.servers.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Player list unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TvColors.TextMuted,
                            )
                            Spacer(Modifier.width(12.dp))
                            TvActionButton(label = "Retry", onClick = onRetryServers)
                        }
                    } else {
                        state.servers
                            .filterNot { it.name.equals("vidnest", ignoreCase = true) }
                            .forEach { server ->
                                val focus = if (firstFocus) focusRequester else null
                                firstFocus = false
                                TvSheetOption(
                                    title = server.displayName,
                                    subtitle = listOf(server.language, "Server List 1").filter { it.isNotBlank() }.joinToString(" · "),
                                    selected = state.activeServer == server.name,
                                    onClick = { onSelectServer(server.name) },
                                    focusRequester = focus,
                                )
                            }
                    }
                }
            }

            TvPlayerSheet.QUALITY -> {
                val focus = if (firstFocus) focusRequester else null
                firstFocus = false
                TvSheetOption(
                    title = "Auto",
                    subtitle = "Best available quality",
                    selected = selectedQuality == null,
                    onClick = { onSelectQuality(null) },
                    focusRequester = focus,
                )
                (state.stream?.allVariants?.filter { it.type == "mp4" && it.quality > 0 }?.distinctBy { it.quality } ?: emptyList()).forEach { variant ->
                    TvSheetOption(
                        title = variant.label,
                        subtitle = "Stream variant",
                        selected = selectedQuality == variant.quality,
                        onClick = { onSelectQuality(variant.quality) },
                    )
                }
                snapshot.videoOptions.filter { it.height != null }.forEach { option ->
                    TvSheetOption(
                        title = option.label,
                        subtitle = "Video",
                        selected = selectedQuality == option.height,
                        onClick = { onSelectQuality(option.height) },
                    )
                }
            }

            TvPlayerSheet.AUDIO -> {
                if (snapshot.audioOptions.isEmpty()) {
                    TvSheetOption(
                        title = "Default audio",
                        subtitle = "Only one track is available",
                        selected = true,
                        onClick = {},
                        focusRequester = focusRequester,
                    )
                } else {
                    snapshot.audioOptions.forEachIndexed { index, track ->
                        TvSheetOption(
                            title = track.label,
                            subtitle = track.language?.uppercase().orEmpty(),
                            selected = track.id == snapshot.activeAudioId,
                            onClick = { onSelectAudio(track.id) },
                            focusRequester = if (index == 0) focusRequester else null,
                        )
                    }
                }
            }

            TvPlayerSheet.SUBTITLES -> {
                TvSheetOption(
                    title = "Off",
                    subtitle = "Hide subtitles",
                    selected = snapshot.activeTextId == null,
                    onClick = { onSelectText(null) },
                    focusRequester = focusRequester,
                )
                if (snapshot.textOptions.isEmpty()) {
                    Text(
                        text = "No subtitle tracks are available for this stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvColors.TextMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    snapshot.textOptions.forEach { track ->
                        TvSheetOption(
                            title = track.label,
                            subtitle = track.language?.uppercase().orEmpty(),
                            selected = track.id == snapshot.activeTextId,
                            onClick = { onSelectText(track.id) },
                        )
                    }
                }
            }

            TvPlayerSheet.SPEED -> {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEachIndexed { index, speed ->
                    TvSheetOption(
                        title = if (speed == 1f) "Normal (1×)" else "${speed}×",
                        subtitle = if (speed == 1f) "Default playback speed" else "Playback speed",
                        selected = kotlin.math.abs(snapshot.speed - speed) < 0.01f,
                        onClick = { onSelectSpeed(speed) },
                        focusRequester = if (index == 0) focusRequester else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSheetOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        focusRequester = focusRequester,
        scaleOnFocus = 1.01f,
        containerColor = if (selected) PlayerColors.Accent.copy(alpha = 0.1f) else Color.Transparent,
        focusedContainerColor = TvColors.CardHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColors.Text,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TvColors.TextMuted,
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = PlayerColors.Accent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
