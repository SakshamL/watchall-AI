package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SHOWS("TV Shows"),
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val results: List<MediaSummary> = emptyList(),
    val recent: List<String> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
) {
    val filteredResults: List<MediaSummary>
        get() = when (filter) {
            SearchFilter.ALL -> results
            SearchFilter.MOVIES -> results.filter { it.mediaType == MediaType.MOVIE }
            SearchFilter.SHOWS -> results.filter { it.mediaType == MediaType.TV }
        }
}

class SearchViewModel(
    private val repository: WatchAllRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SearchUiState(recent = repository.recentSearches()),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query, error = null)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                results = emptyList(),
                searching = false,
                recent = repository.recentSearches(),
            )
            return
        }
        searchJob = viewModelScope.launch {
            delay(380)
            runSearch(query)
        }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    fun chooseRecent(query: String) {
        _state.value = _state.value.copy(query = query)
        submit()
    }

    fun setFilter(filter: SearchFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun clear() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            query = "",
            results = emptyList(),
            searching = false,
            error = null,
            recent = repository.recentSearches(),
        )
    }

    fun clearRecent() {
        repository.clearRecentSearches()
        _state.value = _state.value.copy(recent = emptyList())
    }

    fun removeRecent(query: String) {
        repository.removeRecentSearch(query)
        _state.value = _state.value.copy(recent = repository.recentSearches())
    }

    private suspend fun runSearch(query: String) {
        _state.value = _state.value.copy(searching = true, error = null)
        try {
            val results = repository.search(query)
            _state.value = _state.value.copy(
                results = results,
                searching = false,
                recent = repository.recentSearches(),
                error = null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                searching = false,
                results = emptyList(),
                error = UiText.friendlyError(error),
            )
        }
    }
}
