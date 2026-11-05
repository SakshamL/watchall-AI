package com.watchall.core.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchall.core.data.AppSettings
import com.watchall.core.repo.WatchAllRepository
import com.watchall.core.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings,
    val testing: Boolean = false,
    val status: String? = null,
    val statusIsError: Boolean = false,
)

class SettingsViewModel(
    private val repository: WatchAllRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(settings = repository.currentSettings()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun save(raw: String) {
        val settings = repository.updateApiBaseUrl(raw)
        _state.value = _state.value.copy(
            settings = settings,
            status = "Saved. Testing connection…",
            statusIsError = false,
        )
        test()
    }

    fun reset() {
        val settings = repository.resetApiBaseUrl()
        _state.value = _state.value.copy(
            settings = settings,
            status = "Reset to the default server.",
            statusIsError = false,
        )
        test()
    }

    fun test() {
        if (_state.value.testing) return
        _state.value = _state.value.copy(testing = true, status = "Testing connection…", statusIsError = false)
        viewModelScope.launch {
            try {
                val servers = repository.testConnection()
                _state.value = _state.value.copy(
                    testing = false,
                    status = "Connected · ${servers.size} player${if (servers.size == 1) "" else "s"} available",
                    statusIsError = false,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    testing = false,
                    status = UiText.friendlyError(error),
                    statusIsError = true,
                )
            }
        }
    }
}
