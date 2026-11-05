package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.MediaSummary
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonUiState(
    val credits: List<MediaSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class PersonViewModel(
    private val repository: WatchAllRepository,
    private val personId: Int,
    val name: String,
    val profilePath: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(PersonUiState())
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val credits = repository.personCredits(personId)
                _state.value = PersonUiState(
                    credits = credits,
                    loading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = UiText.friendlyError(error),
                )
            }
        }
    }
}
