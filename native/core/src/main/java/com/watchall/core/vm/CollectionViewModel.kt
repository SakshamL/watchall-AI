package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.Genre
import com.watchall.core.data.HomeRow
import com.watchall.core.data.MediaSummary
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionUiState(
    val items: List<MediaSummary> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val genres: List<Genre> = emptyList(),
    val genreId: Int? = null,
    val year: Int? = null,
) {
    val hasMore: Boolean get() = page < totalPages
    val hasFilters: Boolean get() = genreId != null || year != null
}

class CollectionViewModel(
    private val repository: WatchAllRepository,
    val row: HomeRow,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        // Only discover-based rows accept genre/year filters. Trending rows
        // (query.path != null) are shown without the filter bars.
        val type = row.query?.mediaType
        if (type != null) {
            viewModelScope.launch {
                runCatching { repository.genres(type) }
                    .onSuccess { genres ->
                        _state.value = _state.value.copy(genres = genres)
                    }
            }
        }
        load(reset = true)
    }

    fun retry() = load(reset = _state.value.items.isEmpty())

    fun setGenre(genreId: Int?) {
        if (_state.value.genreId == genreId) return
        _state.value = _state.value.copy(genreId = genreId, page = 1)
        load(reset = true)
    }

    fun setYear(year: Int?) {
        if (_state.value.year == year) return
        _state.value = _state.value.copy(year = year, page = 1)
        load(reset = true)
    }

    fun clearFilters() {
        if (!_state.value.hasFilters) return
        _state.value = _state.value.copy(genreId = null, year = null, page = 1)
        load(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.page >= current.totalPages) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        val current = _state.value
        val nextPage = if (reset) 1 else current.page + 1
        _state.value = current.copy(
            loading = reset,
            loadingMore = !reset,
            error = null,
        )
        loadJob = viewModelScope.launch {
            try {
                val (items, totalPages) = repository.collection(
                    row = row,
                    page = nextPage,
                    genreId = current.genreId,
                    year = current.year,
                )
                _state.value = _state.value.copy(
                    items = if (reset) {
                        items
                    } else {
                        (current.items + items).distinctBy { it.key }
                    },
                    page = nextPage,
                    totalPages = totalPages.coerceAtLeast(1),
                    loading = false,
                    loadingMore = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    items = if (reset) emptyList() else current.items,
                    error = UiText.friendlyError(error),
                )
            }
        }
    }
}
