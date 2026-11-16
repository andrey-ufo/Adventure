package com.myapp.adventure.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.utils.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State exposed to [LogViewerScreen]. Simple enough that a sealed-class
 * UiState would be overkill; a single data class with [isLoading] suffices.
 */
data class LogViewerUiState(
    val content: String = "",
    val isLoading: Boolean = true,
)

/**
 * One-shot events that the screen reacts to but that are not part of the
 * persistent UI state. Toast is the classic case: re-showing it on every
 * recomposition (or after a config change) would be wrong.
 */
sealed interface LogViewerEvent {
    data object LogCleared : LogViewerEvent
}

class LogViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<LogViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { loadLog() }

    fun loadLog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val text = Logger.getLogContent(getApplication())
            _uiState.update { it.copy(content = text, isLoading = false) }
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            Logger.clearLog(getApplication())
            val text = Logger.getLogContent(getApplication())
            _uiState.update { it.copy(content = text) }
            _events.send(LogViewerEvent.LogCleared)
        }
    }
}