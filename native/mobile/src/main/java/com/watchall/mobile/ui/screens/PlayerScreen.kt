package com.watchall.mobile.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.watchall.core.data.Formatters
import com.watchall.core.data.PlaybackFailure
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ServerInfo
import com.watchall.core.data.SourceList
import com.watchall.core.data.TrackChoice
import com.watchall.core.playback.PlaybackController
import com.watchall.core.vm.NoticeTone
import com.watchall.core.vm.PlayerUiState
import com.watchall.core.vm.PlayerViewModel
import com.watchall.core.vm.SourceFailure
import com.watchall.mobile.ui.components.BrandLockup
import com.watchall.mobile.ui.components.ErrorPanel
import com.watchall.mobile.ui.components.WatchLoaderMark
import com.watchall.mobile.ui.theme.WatchColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

private object PlayerColors {
    val Accent = Color(0xFF5B4DFF)
    val Accent2 = Color(0xFF2EC4B6)
    val Focus = Color(0xFF72F1E6)
    val Danger = Color(0xFFFF6B81)
}

private enum class PlayerSheet {
    SOURCE,
    QUALITY,
    AUDIO,
    SUBTITLES,
    SPEED,
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PlayerScreen(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var controlsVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var selectedQuality by remember { mutableStateOf<Int?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
    ) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gestureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun exitPlayer() {
        viewModel.saveProgressAtExit(controller.currentPositionMs(), snapshot.durationMs)
        onBack()
    }

    BackHandler(enabled = sheet == null) { exitPlayer() }

    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            viewModel.saveProgressAtExit(controller.currentPositionMs(), controller.snapshot.value.durationMs)
            controller.release()
            if (activity != null) {
                activity.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(state.stream?.activeVariant?.playbackUrl) {
        val stream = state.stream ?: return@LaunchedEffect
        controller.load(
            stream = stream,
            startPositionMs = state.startPositionMs,
            title = viewModel.request.title,
        )
    }

    LaunchedEffect(state.loading) {
        if (state.loading) controller.pause()
    }

    LaunchedEffect(state.stream?.activeVariant?.playbackUrl) {
        if (state.stream == null) return@LaunchedEffect
        while (isActive) {
            // Fifteen seconds keeps resume points fresh without rewriting the
            // stored list every few seconds on slow hardware.
            delay(15_000)
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
        controlsVisible = true
        snackbarHost.showSnackbar(notice.text)
        viewModel.clearNotice()
    }

    LaunchedEffect(controlsVisible, snapshot.isPlaying, sheet) {
        if (controlsVisible && snapshot.isPlaying && sheet == null) {
            delay(4_200)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = false
                    resizeMode = resizeMode
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.stream != null && !state.hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        var dragStartX = 0f
                        var totalDrag = 0f
                        var dragStartBrightness = brightness
                        var dragStartVolume = volume
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                totalDrag = 0f
                                dragStartBrightness = brightness
                                dragStartVolume = volume
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                val delta = -totalDrag / h
                                if (dragStartX < w / 2f) {
                                    val next = (dragStartBrightness + delta).coerceIn(0.1f, 1f)
                                    brightness = next
                                    activity?.window?.attributes =
                                        activity?.window?.attributes?.apply {
                                            screenBrightness = next
                                        }
                                    gestureLabel = "Brightness ${(next * 100).toInt()}%"
                                } else {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val nextVol = ((dragStartVolume + delta) * maxVol).toInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                    volume = nextVol.toFloat() / maxVol
                                    gestureLabel = "Volume ${(volume * 100).toInt()}%"
                                }
                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    kotlinx.coroutines.delay(900)
                                    gestureLabel = null
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { controlsVisible = !controlsVisible }
                    },
            )

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
                                    0f to Color.Black.copy(alpha = 0.62f),
                                    0.3f to Color.Transparent,
                                    0.65f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.82f),
                                ),
                            ),
                    )
                    PlayerTopBar(
                        request = viewModel.request,
                        state = state,
                        onBack = { exitPlayer() },
                        onOpenSource = { sheet = PlayerSheet.SOURCE },
                        onCycleResize = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        resizeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Crop"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                            else -> "Fit"
                        },
                    )
                    CenterControls(
                        isPlaying = snapshot.isPlaying,
                        isEnded = snapshot.isEnded,
                        onTogglePlay = {
                            controlsVisible = true
                            controller.togglePlay()
                        },
                        onRewind = {
                            controlsVisible = true
                            controller.seekBy(-10_000)
                        },
                        onForward = {
                            controlsVisible = true
                            controller.seekBy(10_000)
                        },
                    )
                    PlayerBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        snapshot = snapshot,
                        state = state,
                        scrubbing = scrubbing,
                        scrubPosition = scrubPosition,
                        onScrubStart = {
                            scrubbing = true
                            scrubPosition = snapshot.positionMs
                            controlsVisible = true
                        },
                        onScrub = { scrubPosition = it },
                        onScrubFinished = {
                            controller.seekTo(scrubPosition)
                            scrubbing = false
                            controlsVisible = true
                        },
                        onOpenSource = {
                            controlsVisible = true
                            sheet = PlayerSheet.SOURCE
                        },
                        onOpenQuality = { sheet = PlayerSheet.QUALITY },
                        onOpenAudio = { sheet = PlayerSheet.AUDIO },
                        onOpenSubtitles = { sheet = PlayerSheet.SUBTITLES },
                        onOpenSpeed = { sheet = PlayerSheet.SPEED },
                        onCycleResize = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        resizeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Crop"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                            else -> "Fit"
                        },
                    )
                }
            }

            gestureLabel?.let { label ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(text = label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }

            if (snapshot.isBuffering && !state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp),
                    color = PlayerColors.Accent,
                    strokeWidth = 3.dp,
                )
            }
        }

        if (state.loading) {
            PlayerLoadingOverlay(state = state, request = viewModel.request)
        }

        if (state.hasError) {
            PlayerErrorOverlay(
                state = state,
                onRetrySource = viewModel::retrySource,
                onRetryPlayback = viewModel::retryPlayback,
                onOtherSource = viewModel::tryOtherSource,
                onSwitchList = viewModel::switchList,
                onOpenSource = { sheet = PlayerSheet.SOURCE },
                onBack = { exitPlayer() },
            )
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 94.dp),
        )
    }

    sheet?.let { activeSheet ->
        ModalBottomSheet(
            onDismissRequest = {
                sheet = null
                controlsVisible = true
            },
            sheetState = sheetState,
            containerColor = WatchColors.Surface,
            dragHandle = null,
        ) {
            PlayerSheetContent(
                sheet = activeSheet,
                state = state,
                snapshot = snapshot,
                selectedQuality = selectedQuality,
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
                onDismiss = {
                    sheet = null
                    controlsVisible = true
                },
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    request: PlayerRequest,
    state: PlayerUiState,
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
    onCycleResize: () -> Unit = {},
    resizeLabel: String = "Fit",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.36f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = request.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onOpenSource,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = PlayerColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.sourceList.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CenterControls(
    isPlaying: Boolean,
    isEnded: Boolean,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LargeRoundButton(
            icon = Icons.Filled.Replay10,
            contentDescription = "Rewind 10 seconds",
            onClick = onRewind,
            size = 54,
        )
        Spacer(Modifier.width(34.dp))
        LargeRoundButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else if (isEnded) "Replay" else "Play",
            onClick = onTogglePlay,
            size = 72,
            filled = true,
        )
        Spacer(Modifier.width(34.dp))
        LargeRoundButton(
            icon = Icons.Filled.Forward10,
            contentDescription = "Forward 10 seconds",
            onClick = onForward,
            size = 54,
        )
    }
}

@Composable
private fun LargeRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int,
    filled: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) PlayerColors.Accent else Color.Black.copy(alpha = 0.34f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) WatchColors.Ink else Color.White,
            modifier = Modifier.size((size * 0.48f).dp),
        )
    }
}

@Composable
private fun PlayerBottomBar(
    modifier: Modifier = Modifier,
    snapshot: com.watchall.core.playback.PlaybackSnapshot,
    state: PlayerUiState,
    scrubbing: Boolean,
    scrubPosition: Long,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSpeed: () -> Unit,
    onCycleResize: () -> Unit = {},
    resizeLabel: String = "Fit",
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val displayedPosition = if (scrubbing) scrubPosition else snapshot.positionMs
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Slider(
            value = displayedPosition.coerceIn(0L, duration).toFloat(),
            onValueChange = {
                if (!scrubbing) onScrubStart()
                onScrub(it.toLong())
            },
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = PlayerColors.Accent,
                activeTrackColor = PlayerColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = Formatters.time(displayedPosition),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Formatters.time(snapshot.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlayerAction(
                icon = Icons.Outlined.Layers,
                label = "Source",
                onClick = onOpenSource,
            )
            PlayerAction(
                icon = Icons.Outlined.Subtitles,
                label = if (snapshot.activeTextId != null) "Subtitles on" else "Subtitles",
                onClick = onOpenSubtitles,
                active = snapshot.activeTextId != null,
            )
            PlayerAction(
                icon = Icons.Outlined.Layers,
                label = resizeLabel,
                onClick = onCycleResize,
            )
            PlayerAction(
                icon = Icons.Outlined.Audiotrack,
                label = "Audio",
                onClick = onOpenAudio,
            )
            PlayerAction(
                icon = Icons.Outlined.HighQuality,
                label = "Quality",
                onClick = onOpenQuality,
            )
            PlayerAction(
                icon = Icons.Outlined.Speed,
                label = "${snapshot.speed}×",
                onClick = onOpenSpeed,
            )
        }
    }
}

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (active) PlayerColors.Accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.065f))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) PlayerColors.Accent else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) PlayerColors.Accent else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerLoadingOverlay(
    state: PlayerUiState,
    request: PlayerRequest,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(WatchColors.Card, WatchColors.Ink, Color.Black),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            BrandLockup()
            Spacer(Modifier.height(30.dp))
            WatchLoaderMark(size = 46.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = state.loadingMessage.ifBlank { "Getting your stream ready…" },
                style = MaterialTheme.typography.titleMedium,
                color = WatchColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "${request.title} · ${request.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = WatchColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (state.sourceList == SourceList.SERVER_TWO) {
                    "Server List 2 can take up to 30 seconds to find a stream."
                } else {
                    "Choosing the best available player. This usually takes a few seconds."
                },
                style = MaterialTheme.typography.labelSmall,
                color = WatchColors.TextMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    state: PlayerUiState,
    onRetrySource: () -> Unit,
    onRetryPlayback: () -> Unit,
    onOtherSource: () -> Unit,
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
            if (both && other != null) {
                secondaryLabel = "Try ${other.displayName} again"
                secondaryAction = { onSwitchList(other) }
            } else {
                secondaryLabel = null
                secondaryAction = null
            }
        }
    } else {
        primaryLabel = "Try again"
        primaryAction = onRetryPlayback
        if (other != null) {
            secondaryLabel = if (both) "Try ${other.displayName} again" else "Try ${other.displayName}"
            secondaryAction = { onSwitchList(other) }
        } else {
            secondaryLabel = null
            secondaryAction = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WatchColors.DeepInk, Color.Black),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        ErrorPanel(
            title = title,
            message = message,
            primaryLabel = primaryLabel,
            onPrimary = primaryAction,
            secondaryLabel = secondaryLabel,
            onSecondary = secondaryAction,
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Choose another source",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSource)
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                color = WatchColors.TextMuted,
            )
        }
    }
}

@Composable
private fun PlayerSheetContent(
    sheet: PlayerSheet,
    state: PlayerUiState,
    snapshot: com.watchall.core.playback.PlaybackSnapshot,
    selectedQuality: Int?,
    onSelectQuality: (Int?) -> Unit,
    onSelectAudio: (String) -> Unit,
    onSelectText: (String?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectList: (SourceList) -> Unit,
    onSelectServer: (String) -> Unit,
    onRetryServers: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Text(
            text = when (sheet) {
                PlayerSheet.SOURCE -> "Playback source"
                PlayerSheet.QUALITY -> "Video quality"
                PlayerSheet.AUDIO -> "Audio track"
                PlayerSheet.SUBTITLES -> "Subtitles"
                PlayerSheet.SPEED -> "Playback speed"
            },
            style = MaterialTheme.typography.titleLarge,
            color = WatchColors.Text,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when (sheet) {
                PlayerSheet.SOURCE -> "Switch lists without losing your place."
                else -> "Changes apply immediately."
            },
            style = MaterialTheme.typography.bodySmall,
            color = WatchColors.TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        when (sheet) {
            PlayerSheet.SOURCE -> {
                state.availableSources.forEach { source ->
                    OptionRow(
                        title = source.displayName,
                        subtitle = when (source) {
                            SourceList.SERVER_ONE -> "Recommended · movies and TV shows"
                            SourceList.SERVER_TWO -> "Alternative"
                        },
                        selected = state.sourceList == source,
                        enabled = true,
                        onClick = {
                            if (state.sourceList != source) onSelectList(source) else onDismiss()
                        },
                    )
                }
                if (state.availableSources.contains(SourceList.SERVER_ONE)) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "PLAYERS ON SERVER LIST 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = WatchColors.TextMuted,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.serversLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PlayerColors.Accent,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Checking available players…",
                                style = MaterialTheme.typography.bodySmall,
                                color = WatchColors.TextMuted,
                            )
                        }
                    } else if (state.servers.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Player list unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WatchColors.TextMuted,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Retry",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onRetryServers)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = PlayerColors.Accent,
                            )
                        }
                    } else {
                        state.servers
                            .filterNot { it.name.equals("vidnest", ignoreCase = true) }
                            .forEach { server ->
                                OptionRow(
                                    title = server.displayName,
                                    subtitle = listOf(server.language, "Server List 1").filter { it.isNotBlank() }.joinToString(" · "),
                                    selected = state.activeServer == server.name,
                                    enabled = true,
                                    onClick = { onSelectServer(server.name) },
                                )
                            }
                    }
                }
            }

            PlayerSheet.QUALITY -> {
                OptionRow(
                    title = "Auto",
                    subtitle = "Let WatchAll choose the best quality",
                    selected = selectedQuality == null,
                    enabled = true,
                    onClick = { onSelectQuality(null) },
                )
                (state.stream?.allVariants?.filter { it.type == "mp4" && it.quality > 0 }?.distinctBy { it.quality } ?: emptyList()).forEach { variant ->
                    OptionRow(
                        title = variant.label,
                        subtitle = "Stream variant",
                        selected = selectedQuality == variant.quality,
                        enabled = true,
                        onClick = { onSelectQuality(variant.quality) },
                    )
                }
                if (snapshot.videoOptions.size <= 1 && (state.stream?.allVariants?.size ?: 0) <= 1) {
                    Text(
                        text = "Quality options appear once the stream is ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WatchColors.TextMuted,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                snapshot.videoOptions
                    .filter { it.height != null }
                    .forEach { option ->
                        OptionRow(
                            title = option.label,
                            subtitle = "Video",
                            selected = selectedQuality == option.height,
                            enabled = true,
                            onClick = { onSelectQuality(option.height) },
                        )
                    }
            }

            PlayerSheet.AUDIO -> {
                if (snapshot.audioOptions.isEmpty()) {
                    OptionRow(
                        title = "Default audio",
                        subtitle = "Only one track is available",
                        selected = true,
                        enabled = false,
                        onClick = {},
                    )
                } else {
                    snapshot.audioOptions.forEach { track ->
                        OptionRow(
                            title = track.label,
                            subtitle = track.language?.uppercase().orEmpty(),
                            selected = track.id == snapshot.activeAudioId,
                            enabled = true,
                            onClick = { onSelectAudio(track.id) },
                        )
                    }
                }
            }

            PlayerSheet.SUBTITLES -> {
                OptionRow(
                    title = "Off",
                    subtitle = "Hide subtitles",
                    selected = snapshot.activeTextId == null,
                    enabled = true,
                    onClick = { onSelectText(null) },
                )
                if (snapshot.textOptions.isEmpty()) {
                    Text(
                        text = "No subtitle tracks are available for this stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WatchColors.TextMuted,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    snapshot.textOptions.forEach { track ->
                        OptionRow(
                            title = track.label,
                            subtitle = track.language?.uppercase().orEmpty(),
                            selected = track.id == snapshot.activeTextId,
                            enabled = true,
                            onClick = { onSelectText(track.id) },
                        )
                    }
                }
            }

            PlayerSheet.SPEED -> {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    OptionRow(
                        title = if (speed == 1f) "Normal (1×)" else "${speed}×",
                        subtitle = if (speed == 1f) "Default playback speed" else "Playback speed",
                        selected = kotlin.math.abs(snapshot.speed - speed) < 0.01f,
                        enabled = true,
                        onClick = { onSelectSpeed(speed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(if (selected) PlayerColors.Accent.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) WatchColors.Text else WatchColors.TextMuted.copy(alpha = 0.55f),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = WatchColors.TextMuted.copy(alpha = if (enabled) 1f else 0.5f),
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = PlayerColors.Accent,
                modifier = Modifier.size(20.dp),
            )
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
