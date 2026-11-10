package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.MediaDetails
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.data.SeasonDetails
import com.watchall.core.data.ServerInfo
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsUiState(
    val loading: Boolean = true,
    val details: MediaDetails? = null,
    val error: String? = null,
    val selectedSeason: Int? = null,
    val season: SeasonDetails? = null,
    val seasonLoading: Boolean = false,
    val seasonError: String? = null,
    val selectedEpisode: Int? = null,
    val servers: List<ServerInfo> = emptyList(),
    val serversLoading: Boolean = true,
    val watchlisted: Boolean = false,
) {
    val currentEpisode get() = season?.episodes?.firstOrNull { it.episodeNumber == selectedEpisode }
}

class DetailsViewModel(
    private val repository: WatchAllRepository,
    private val mediaId: Int,
    private val mediaType: MediaType,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    private var seasonJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // Only the main details request is awaited before the screen is
                // allowed to paint.
                val details = repository.details(mediaId, mediaType)
                val resume = if (mediaType == MediaType.TV) {
                    repository.continueWatching()
                        .firstOrNull { it.mediaType == MediaType.TV && it.mediaKey.startsWith("tv-$mediaId-s") }
                } else {
                    null
                }
                _state.value = _state.value.copy(
                    loading = false,
                    details = details,
                    error = null,
                    watchlisted = repository.isWatchlisted(details.toSummary()),
                )
                if (mediaType == MediaType.TV) {
                    val seasonNumber = resume?.seasonNumber
                        ?: details.seasons.firstOrNull()?.seasonNumber
                        ?: 1
                    loadSeason(seasonNumber, resume?.episodeNumber)
                }

                // "More like this" costs several extra discover calls. Fill it
                // in afterwards so it never delays the first frame.
                val related = try {
                    repository.related(details)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (!related.isNullOrEmpty()) {
                    val current = _state.value.details
                    if (current != null && current.mediaKey == details.mediaKey) {
                        _state.value = _state.value.copy(
                            details = current.copy(recommendations = related),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = UiText.friendlyError(error),
                )
            }
        }

        viewModelScope.launch {
            runCatching { repository.servers() }
                .onSuccess { servers ->
                    _state.value = _state.value.copy(servers = servers, serversLoading = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(serversLoading = false)
                }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        if (_state.value.selectedSeason == seasonNumber) return
        loadSeason(seasonNumber, null)
    }

    fun selectEpisode(episodeNumber: Int) {
        _state.value = _state.value.copy(selectedEpisode = episodeNumber)
    }

    private fun loadSeason(seasonNumber: Int, preferredEpisode: Int?) {
        seasonJob?.cancel()
        _state.value = _state.value.copy(
            selectedSeason = seasonNumber,
            season = null,
            seasonLoading = true,
            seasonError = null,
            selectedEpisode = preferredEpisode,
        )
        seasonJob = viewModelScope.launch {
            try {
                val season = repository.season(mediaId, seasonNumber)
                val episode = season.episodes.firstOrNull { it.episodeNumber == preferredEpisode }
                    ?: season.episodes.firstOrNull()
                _state.value = _state.value.copy(
                    season = season,
                    seasonLoading = false,
                    selectedEpisode = episode?.episodeNumber,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    seasonLoading = false,
                    seasonError = UiText.friendlyError(error),
                )
            }
        }
    }

    fun toggleWatchlist() {
        val details = _state.value.details ?: return
        val next = repository.toggleWatchlist(details.toSummary())
        _state.value = _state.value.copy(watchlisted = next)
    }

    private fun MediaDetails.toSummary() = MediaSummary(
        id = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        rating = rating,
        year = year,
        mediaType = mediaType,
        originalLanguage = originalLanguage,
        genreIds = genres.map { it.id },
    )
}
