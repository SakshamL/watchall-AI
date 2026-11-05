package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.Genre
import com.watchall.core.data.MediaSummary
import com.watchall.core.data.MediaType
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogUiState(
    val type: MediaType,
    val language: String? = null,
    val genreId: Int? = null,
    val year: Int? = null,
    val genres: List<Genre> = emptyList(),
    val items: List<MediaSummary> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class CatalogViewModel(
    private val repository: WatchAllRepository,
    private val type: MediaType,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState(type = type))
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repository.genres(type) }
                .onSuccess { genres ->
                    _state.value = _state.value.copy(genres = genres)
                }
        }
        load(reset = true)
    }

    fun setLanguage(language: String?) {
        if (_state.value.language == language) return
        _state.value = _state.value.copy(language = language, page = 1)
        load(reset = true)
    }

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
        _state.value = _state.value.copy(language = null, genreId = null, year = null, page = 1)
        load(reset = true)
    }

    fun retry() = load(reset = _state.value.items.isEmpty())

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
                val (items, totalPages) = repository.catalog(
                    type = type,
                    language = current.language,
                    genreId = current.genreId,
                    year = current.year,
                    page = nextPage,
                )
                val merged = if (reset) {
                    items
                } else {
                    (current.items + items).distinctBy { it.key }
                }
                _state.value = _state.value.copy(
                    items = merged,
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
                    error = UiText.friendlyError(error),
                    items = if (reset) emptyList() else current.items,
                )
            }
        }
    }
}
