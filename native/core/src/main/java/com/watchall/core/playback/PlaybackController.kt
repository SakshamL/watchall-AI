package com.watchall.core.playback

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.watchall.core.data.PlaybackFailure
import com.watchall.core.data.StreamResult
import com.watchall.core.data.TrackChoice
import com.watchall.core.util.DeviceProfile
import com.watchall.core.util.DeviceTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val failure: PlaybackFailure? = null,
    val videoOptions: List<TrackChoice> = emptyList(),
    val audioOptions: List<TrackChoice> = emptyList(),
    val textOptions: List<TrackChoice> = emptyList(),
    val activeVideoHeight: Int? = null,
    val activeAudioId: String? = null,
    val activeTextId: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val streamType: String = "",
)

class PlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tag = "WatchAllPlayer"
    private val tier = DeviceProfile.tier(appContext)
    private var ticker: Job? = null

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("WatchAll/1.0 (Android)")
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(true)

    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(buildLoadControl())
        .build()

    /**
     * Buffer budget per device class.
     *
     * One 60 second buffer for every device meant a 1 GB stick could hold over
     * 100 MB of video in RAM and get killed while a phone sat far below its
     * limits. Low-tier devices now buffer about half as much and cap the buffer
     * in bytes, which trades a rare rebuffer for staying alive during playback.
     */
    private fun buildLoadControl(): DefaultLoadControl {
        val builder = when (tier) {
            DeviceTier.LOW -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    30_000,
                    3_000,
                    6_000,
                )
                .setTargetBufferBytes(16 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)

            DeviceTier.MID -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    20_000,
                    45_000,
                    2_500,
                    5_000,
                )
                .setTargetBufferBytes(32 * 1024 * 1024)

            DeviceTier.HIGH -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    20_000,
                    60_000,
                    2_500,
                    5_000,
                )
        }
        return builder.build()
    }

    val player: Player get() = exoPlayer

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val audioTargets = LinkedHashMap<String, Pair<Int, Int>>()
    private val textTargets = LinkedHashMap<String, Pair<Int, Int>>()

    var onVariantRequested: ((Int) -> Unit)? = null
    private var currentStream: StreamResult? = null
    private var currentTitle: String = ""
    private var upgradeJob: Job? = null
    private var manualQuality: Boolean = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _snapshot.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _snapshot.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED,
                )
            }
            if (playbackState == Player.STATE_READY) {
                _snapshot.update { it.copy(failure = null) }
                ensureVideoTrackSelected()
                updateTracks()
            }
            if (playbackState == Player.STATE_ENDED) {
                _snapshot.update { it.copy(isPlaying = false) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(
                tag,
                "Playback error ${error.errorCodeName} (${error.errorCode}) url=${currentStream?.activeVariant?.originalUrl}",
                error,
            )
            _snapshot.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    failure = mapFailure(error),
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateTracks()
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            _snapshot.update {
                it.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
        ticker = scope.launch {
            while (isActive) {
                refreshProgress()
                delay(500)
            }
        }
    }

    fun load(stream: StreamResult, startPositionMs: Long = 0L, title: String = "") {
        currentStream = stream
        currentTitle = title
        manualQuality = false
        upgradeJob?.cancel()
        audioTargets.clear()
        textTargets.clear()
        _snapshot.update { it.copy(failure = null) }
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setMaxVideoSize(1920, 720)
        if (deviceSupportsHevc()) {
            builder.setPreferredVideoMimeTypes(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264)
        } else {
            builder.setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
        }
        exoPlayer.trackSelectionParameters = builder.build()

        val active = stream.activeVariant
        val mediaBuilder = MediaItem.Builder()
            .setUri(active.playbackUrl)
            .setMimeType(active.type.mimeType())

        // Attach every caption track the server found so the subtitles menu
        // can flip them on without a second load.
        val subtitles = stream.captions.mapNotNull { caption ->
            val url = caption.url.trim()
            if (url.isBlank()) return@mapNotNull null
            val mime = when (caption.format.lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                else -> MimeTypes.TEXT_VTT
            }
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                .setMimeType(mime)
                .setLanguage(caption.language.takeIf { it.isNotBlank() })
                .setLabel(caption.label)
                .setSelectionFlags(if (caption.language.isBlank()) 0 else C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        if (subtitles.isNotEmpty()) {
            mediaBuilder.setSubtitleConfigurations(subtitles)
        }

        val item = mediaBuilder
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()

        val paramsBuilder = builder
        if (subtitles.isNotEmpty()) {
            paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        }
        exoPlayer.trackSelectionParameters = paramsBuilder.build()
        exoPlayer.setMediaItem(item, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        scheduleQualityUpgrade(startPositionMs)
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    fun seekBy(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        seekTo((exoPlayer.currentPosition + deltaMs).coerceIn(0L, max(0L, duration - 500L)))
    }

    fun retry() {
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
        _snapshot.update { it.copy(speed = speed) }
    }

    fun setVideoQuality(height: Int?) {
        if (height != null) {
            val variant = currentStream?.allVariants?.firstOrNull {
                it.type == "mp4" && it.quality == height && currentStream!!.allVariants.size > 1
            }
            if (variant != null) {
                val index = currentStream!!.allVariants.indexOf(variant)
                if (index >= 0) {
                    manualQuality = true
                    onVariantRequested?.invoke(index)
                    return
                }
            }
        }
        manualQuality = height != null
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (height == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            builder.setMaxVideoSize(1920, autoMaxHeight())
            upgradeJob?.cancel()
            scheduleQualityUpgrade(exoPlayer.currentPosition)
        } else {
            val tracks = exoPlayer.currentTracks
            var override: TrackSelectionOverride? = null
            tracks.groups.forEach { group ->
                if (group.type != C.TRACK_TYPE_VIDEO || override != null) return@forEach
                for (index in 0 until group.length) {
                    if (group.getTrackFormat(index).height == height) {
                        override = TrackSelectionOverride(group.mediaTrackGroup, index)
                        break
                    }
                }
            }
            override?.let { chosen ->
                builder.setOverrideForType(chosen)
                builder.setMaxVideoSize(Int.MAX_VALUE, height)
            }
        }
        exoPlayer.trackSelectionParameters = builder.build()
        updateTracks()
    }

    fun setAudioTrack(id: String) {
        val target = audioTargets[id] ?: return
        val group = exoPlayer.currentTracks.groups.getOrNull(target.first) ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, target.second))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        exoPlayer.trackSelectionParameters = builder.build()
        updateTracks()
    }

    fun setTextTrack(id: String?) {
        if (id == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            updateTracks()
            return
        }
        val target = textTargets[id] ?: return
        val group = exoPlayer.currentTracks.groups.getOrNull(target.first) ?: return
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, target.second))
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(group.getTrackFormat(target.second).language)
        exoPlayer.trackSelectionParameters = builder.build()
        updateTracks()
    }

    fun currentPositionMs(): Long = exoPlayer.currentPosition

    fun release() {
        upgradeJob?.cancel()
        ticker?.cancel()
        scope.cancel()
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    /**
     * The LOW-tier 720p cap can hide every video track a stream actually offers
     * (some sources only ship 1080p). That leaves a black screen with audio, or
     * no playback at all on an emulator. If nothing got selected, drop the cap
     * and let the device try what it has.
     */
    private fun ensureVideoTrackSelected() {
        val hasVideoGroup = exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
        if (!hasVideoGroup) return
        val selected = exoPlayer.currentTracks.groups.any { group ->
            group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { group.isTrackSelected(it) }
        }
        if (selected) return
        Log.w(tag, "no video track selected after READY; relaxing maxVideoSize")
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .build()
        updateTracks()
    }

    private fun refreshProgress() {
        val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val buffered = exoPlayer.bufferedPosition.coerceAtLeast(0L)
        _snapshot.update {
            it.copy(
                positionMs = position,
                durationMs = duration,
                bufferedMs = buffered,
                isPlaying = exoPlayer.isPlaying,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                isEnded = exoPlayer.playbackState == Player.STATE_ENDED,
                speed = exoPlayer.playbackParameters.speed,
                activeVideoHeight = exoPlayer.videoFormat?.height,
            )
        }
    }

    private fun updateTracks() {
        val tracks = exoPlayer.currentTracks
        val video = linkedSetOf<Int>()
        val audio = mutableListOf<TrackChoice>()
        val text = mutableListOf<TrackChoice>()
        audioTargets.clear()
        textTargets.clear()
        var activeAudioId: String? = null
        var activeTextId: String? = null

        tracks.groups.forEachIndexed { groupIndex, group ->
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (index in 0 until group.length) {
                        val height = group.getTrackFormat(index).height
                        if (height > 0) video += height
                    }
                }

                C.TRACK_TYPE_AUDIO -> {
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val id = "a:$groupIndex:$index"
                        val label = format.label
                            ?: format.language?.uppercase()
                            ?: "Audio ${audio.size + 1}"
                        audioTargets[id] = groupIndex to index
                        audio += TrackChoice(id, label, format.language)
                        if (group.isTrackSelected(index)) activeAudioId = id
                    }
                }

                C.TRACK_TYPE_TEXT -> {
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        val id = "t:$groupIndex:$index"
                        val label = format.label
                            ?: format.language?.uppercase()
                            ?: "Subtitles ${text.size + 1}"
                        textTargets[id] = groupIndex to index
                        text += TrackChoice(id, label, format.language)
                        if (group.isTrackSelected(index)) activeTextId = id
                    }
                }
            }
        }

        val videoOptions = listOf(TrackChoice("auto", "Auto", height = null)) +
            video.sortedDescending().map {
                TrackChoice(id = "v:$it", label = "${it}p", height = it)
            }

        _snapshot.update {
            it.copy(
                videoOptions = videoOptions,
                audioOptions = audio,
                textOptions = text,
                activeAudioId = activeAudioId,
                activeTextId = activeTextId,
                activeVideoHeight = exoPlayer.videoFormat?.height,
            )
        }
    }

    private fun StreamResult.mimeType(): String = when (type.lowercase()) {
        "hls" -> MimeTypes.APPLICATION_M3U8
        "dash" -> MimeTypes.APPLICATION_MPD
        "mp4" -> MimeTypes.VIDEO_MP4
        else -> MimeTypes.APPLICATION_M3U8
    }

private fun deviceSupportsHevc(): Boolean {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Highest resolution the "Auto" setting may pick. Low-tier devices stop at
     * 720p: 1080p decode on those boxes costs more than the extra detail is
     * worth and is a common cause of dropped frames.
     */
    private fun autoMaxHeight(): Int =
        if (tier == DeviceTier.LOW) 720 else 1080

    private fun scheduleQualityUpgrade(startPositionMs: Long) {
        upgradeJob?.cancel()
        if (tier == DeviceTier.LOW) return
        upgradeJob = scope.launch {
            delay(10_000)
            if (manualQuality) return@launch
            if (_snapshot.value.failure != null) return@launch
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1920, autoMaxHeight())
                .build()
            updateTracks()
        }
    }

    private fun String.mimeType(): String = when (lowercase()) {
        "hls" -> MimeTypes.APPLICATION_M3U8
        "dash" -> MimeTypes.APPLICATION_MPD
        "mp4" -> MimeTypes.VIDEO_MP4
        else -> MimeTypes.APPLICATION_M3U8
    }

    private fun mapFailure(error: PlaybackException): PlaybackFailure {
        val network = error.errorCodeName.contains("IO", ignoreCase = true) ||
            error.errorCodeName.contains("NETWORK", ignoreCase = true) ||
            error.errorCodeName.contains("TIMEOUT", ignoreCase = true)
        val decode = error.errorCodeName.contains("DECOD", ignoreCase = true)
        val unsupported = error.errorCodeName.contains("UNSUPPORTED", ignoreCase = true) ||
            error.errorCodeName.contains("PARSING", ignoreCase = true)
        return when {
            decode -> PlaybackFailure(
                title = "This device cannot decode the stream",
                message = "Try another server or a lower quality stream.",
                code = error.errorCodeName,
            )
            unsupported -> PlaybackFailure(
                title = "This video format is not supported",
                message = "Try another server. Some TVs cannot play every codec.",
                code = error.errorCodeName,
            )
            network -> PlaybackFailure(
                title = "Playback interrupted",
                message = "The stream stopped responding. Try again or choose another server.",
                code = error.errorCodeName,
            )
            else -> PlaybackFailure(
                title = "Playback failed",
                message = "Something went wrong while playing this stream. Try again.",
                code = error.errorCodeName,
            )
        }
    }
}
