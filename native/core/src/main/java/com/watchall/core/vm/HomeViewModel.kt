package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.HomeFeed
import com.watchall.core.data.ResumeEntry
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val feed: HomeFeed? = null,
    val continueWatching: List<ResumeEntry> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repository: WatchAllRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        bootstrap()
    }

    /**
     * Show the saved feed first, then refresh it quietly.
     *
     * Loading the real feed needs about ten TMDB calls, so on a cold start the
     * previous copy is painted in the first frame and replaced when the network
     * answers. Without a saved copy this behaves exactly as before.
     */
    private fun bootstrap() {
        loadJob = viewModelScope.launch {
            val cached = runCatching { repository.cachedHome() }.getOrNull()
            if (cached != null && _state.value.feed == null) {
                _state.value = _state.value.copy(
                    feed = cached,
                    loading = false,
                    continueWatching = repository.continueWatching(),
                    error = null,
                )
            }
            load(showRefreshIndicator = false)
        }
    }

    fun refresh(showRefreshIndicator: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            load(showRefreshIndicator)
        }
    }

    private suspend fun load(showRefreshIndicator: Boolean) {
        _state.value = _state.value.copy(
            loading = _state.value.feed == null,
            refreshing = showRefreshIndicator,
            error = null,
        )
        try {
            val feed = repository.home()
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                feed = feed,
                continueWatching = repository.continueWatching(),
                error = null,
            )
        } catch (error: Exception) {
            // Keep whatever feed is already on screen; only report the failure.
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                error = UiText.friendlyError(error),
            )
        }
    }

    fun refreshContinueWatching() {
        _state.value = _state.value.copy(
            continueWatching = repository.continueWatching(),
        )
    }

    fun removeContinueWatching(mediaKey: String) {
        repository.clearProgress(mediaKey)
        refreshContinueWatching()
    }
}
