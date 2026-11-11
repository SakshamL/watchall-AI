package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.MediaType
import com.watchall.core.data.PlaybackFailure
import com.watchall.core.data.PlayerRequest
import com.watchall.core.data.ResumeEntry
import com.watchall.core.data.ServerInfo
import com.watchall.core.data.SourceList
import com.watchall.core.data.StreamResult
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Playback at or past this fraction of the runtime counts as finished. */
private const val WATCHED_THRESHOLD = 0.90f

data class SourceFailure(
    val title: String,
    val message: String,
)

data class PlayerNotice(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val tone: NoticeTone = NoticeTone.DEFAULT,
)

enum class NoticeTone {
    DEFAULT,
    SUCCESS,
    ERROR,
}

data class PlayerUiState(
    val sourceList: SourceList = SourceList.SERVER_ONE,
    val availableSources: List<SourceList> = SourceList.entries,
    val servers: List<ServerInfo> = emptyList(),
    val serversLoading: Boolean = true,
    val stream: StreamResult? = null,
    val loading: Boolean = true,
    val loadingMessage: String = "Getting your stream ready…",
    val sourceError: SourceFailure? = null,
    val playbackFailure: PlaybackFailure? = null,
    val failedLists: Set<SourceList> = emptySet(),
    val activeServer: String? = null,
    val switchingServer: String? = null,
    val notice: PlayerNotice? = null,
    val startPositionMs: Long = 0L,
    /**
     * Bumped every time a stream finishes loading. The player screen keys its
     * "load this stream" effect on it, so retrying a title that resolves to the
     * exact same URL still restarts playback instead of leaving the old failed
     * player on screen.
     */
    val loadToken: Long = 0L,
) {
    val currentListFailed: Boolean get() = failedLists.contains(sourceList)
    val otherSource: SourceList? get() = availableSources.firstOrNull { it != sourceList }
    val bothListsFailed: Boolean
        get() = availableSources.isNotEmpty() && failedLists.containsAll(availableSources)
    val hasError: Boolean get() = sourceError != null || playbackFailure != null
}

class PlayerViewModel(
    private val repository: WatchAllRepository,
    val request: PlayerRequest,
) : ViewModel() {
    private val availableSources = SourceList.available(request.mediaType)
    private val initialSource = request.initialSource
        .takeIf { it in availableSources }
        ?: availableSources.first()

    private val initialResume = repository.resume(request.mediaKey)
    private var requestedServer: String? = request.initialServer
    private var rememberedPositionMs: Long = initialResume?.positionMs ?: 0L
    private var autoFallbackUsed = false
    private var extractionJob: Job? = null

    private val _state = MutableStateFlow(
        PlayerUiState(
            sourceList = initialSource,
            availableSources = availableSources,
            startPositionMs = rememberedPositionMs,
        ),
    )
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        loadServers()
        extractCurrent()
    }

    fun rememberPosition(positionMs: Long) {
        if (positionMs > 0) rememberedPositionMs = positionMs
    }

    fun loadServers() {
        viewModelScope.launch {
            runCatching { repository.servers() }
                .onSuccess { servers ->
                    _state.value = _state.value.copy(
                        servers = servers,
                        serversLoading = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(serversLoading = false)
                }
        }
    }

    fun retrySource() {
        autoFallbackUsed = false
        extractCurrent()
    }

    fun retryPlayback() {
        state.value.playbackFailure ?: return
        _state.value = _state.value.copy(playbackFailure = null)
        extractCurrent()
    }

    fun selectVariant(index: Int, positionMs: Long = 0L) {
        if (positionMs > 0L) rememberedPositionMs = positionMs
        val current = state.value
        val stream = current.stream ?: return
        val next = stream.withVariant(index)
        if (next.variantIndex == stream.variantIndex) return
        if (positionMs > 0L) rememberedPositionMs = positionMs
        _state.value = current.copy(
            stream = next,
            loading = false,
            playbackFailure = null,
            startPositionMs = if (positionMs > 0L) positionMs else current.startPositionMs,
            notice = PlayerNotice(text = "Switching to ${next.activeVariant.label}…"),
        )
    }

    fun switchList(next: SourceList) {
        if (next !in availableSources || next == state.value.sourceList) return
        requestedServer = null
        autoFallbackUsed = false
        extractCurrent(next)
    }

    fun tryOtherSource() {
        state.value.otherSource?.let { switchList(it) }
    }

    fun selectServer(name: String) {
        val current = state.value
        if (name.isBlank()) return
        if (name.equals("Vidnest", ignoreCase = true)) {
            switchList(SourceList.SERVER_TWO)
            return
        }
        if (name.equals("VidSrc", ignoreCase = true)) {
            switchList(SourceList.SERVER_THREE)
            return
        }
        if (name == current.activeServer && !current.loading) {
            _state.value = current.copy(
                notice = PlayerNotice(text = "Already playing from ${UiText.sourceName(name)}."),
            )
            return
        }
        requestedServer = name
        autoFallbackUsed = false
        if (current.sourceList == SourceList.SERVER_TWO) {
            extractCurrent(SourceList.SERVER_ONE)
        } else {
            extractCurrent(SourceList.SERVER_ONE)
        }
    }

    fun onPlaybackFailure(failure: PlaybackFailure, positionMs: Long = 0L) {
        if (positionMs > 0L) rememberedPositionMs = positionMs
        val current = state.value
        if (current.loading) return

        val stream = current.stream
        if (stream != null && stream.variantIndex + 1 < stream.allVariants.size) {
            val next = stream.withVariant(stream.variantIndex + 1)
            autoFallbackUsed = false
            _state.value = current.copy(
                stream = next,
                loading = false,
                playbackFailure = null,
                failedLists = current.failedLists,
                activeServer = current.activeServer,
                switchingServer = null,
                startPositionMs = if (positionMs > 0L) positionMs else current.startPositionMs,
                notice = PlayerNotice(
                    text = "${failure.title}. Trying ${next.activeVariant.label}…",
                    tone = NoticeTone.ERROR,
                ),
            )
            return
        }

        val candidate = if (
            current.sourceList == SourceList.SERVER_ONE &&
            !autoFallbackUsed &&
            current.servers.isNotEmpty() &&
            current.activeServer == null
        ) {
            val names = current.servers.map { it.name }
            val active = current.activeServer
            val activeIndex = if (active == null) -1 else names.indexOf(active)
            val nextName = if (activeIndex >= 0) {
                names.getOrNull((activeIndex + 1) % names.size)
            } else {
                names.firstOrNull()
            }
            nextName?.takeIf { it != active }
        } else {
            null
        }

        if (candidate != null) {
            autoFallbackUsed = true
            requestedServer = candidate
            _state.value = current.copy(
                notice = PlayerNotice(
                    text = "${failure.title}. Trying ${UiText.sourceName(serverLabel(candidate))}…",
                    tone = NoticeTone.ERROR,
                ),
            )
            extractCurrent(SourceList.SERVER_ONE)
            return
        }

        _state.value = current.copy(
            playbackFailure = failure,
            failedLists = current.failedLists + current.sourceList,
            loading = false,
            switchingServer = null,
        )
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        rememberPosition(positionMs)
        val entry = ResumeEntry(
            mediaKey = request.mediaKey,
            title = request.title,
            subtitle = request.subtitle,
            posterPath = request.posterPath,
            backdropPath = request.backdropPath,
            mediaType = request.mediaType,
            mediaId = request.mediaId,
            originalLanguage = request.originalLanguage,
            seasonNumber = request.seasonNumber,
            episodeNumber = request.episodeNumber,
            episodeTitle = request.episodeTitle,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        repository.saveProgress(entry)
        val finished = durationMs > 0L && positionMs.toFloat() / durationMs >= WATCHED_THRESHOLD
        if (finished) {
            repository.markWatched(entry)
            repository.clearProgress(entry.mediaKey)
        }
    }

    fun saveProgressAtExit(positionMs: Long, durationMs: Long) {
        if (positionMs > 5_000L && durationMs > 0L) {
            saveProgress(positionMs, durationMs)
        }
    }

    private fun serverLabel(name: String): String =
        state.value.servers.firstOrNull { it.name == name }?.label?.takeIf { it.isNotBlank() } ?: name

    private fun loadingMessage(source: SourceList, server: String?): String {
        return when {
            source == SourceList.SERVER_TWO -> "Trying Server List 2…"
            source == SourceList.SERVER_THREE -> "Trying Server List 3…"
            !server.isNullOrBlank() -> "Opening ${UiText.sourceName(serverLabel(server))}…"
            else -> "Finding your stream…"
        }
    }

    private fun extractCurrent(nextSource: SourceList = state.value.sourceList) {
        extractionJob?.cancel()
        val current = _state.value
        val server = requestedServer
        val startAt = rememberedPositionMs
        _state.value = current.copy(
            sourceList = nextSource,
            stream = null,
            loading = true,
            loadingMessage = loadingMessage(nextSource, server),
            sourceError = null,
            playbackFailure = null,
            switchingServer = server,
            activeServer = null,
            startPositionMs = startAt,
            notice = null,
        )

        extractionJob = viewModelScope.launch {
            try {
                val stream = repository.extract(
                    mediaId = request.mediaId,
                    mediaType = request.mediaType,
                    source = nextSource,
                    language = request.originalLanguage,
                    server = server,
                    seasonNumber = request.seasonNumber,
                    episodeNumber = request.episodeNumber,
                )
                val active = when {
                    nextSource == SourceList.SERVER_TWO -> "Vidnest"
                    nextSource == SourceList.SERVER_THREE -> "VidSrc"
                    !stream.server.isNullOrBlank() -> stream.server
                    else -> server
                }
                requestedServer = server
                _state.value = _state.value.copy(
                    stream = stream,
                    loading = false,
                    sourceError = null,
                    playbackFailure = null,
                    failedLists = _state.value.failedLists - nextSource,
                    activeServer = active,
                    switchingServer = null,
                    loadingMessage = "",
                    startPositionMs = rememberedPositionMs,
                    loadToken = _state.value.loadToken + 1,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    stream = null,
                    sourceError = SourceFailure(
                        title = "Unable to start playback",
                        message = UiText.friendlyError(error),
                    ),
                    playbackFailure = null,
                    failedLists = _state.value.failedLists + nextSource,
                    activeServer = null,
                    switchingServer = null,
                )
            }
        }
    }
}
