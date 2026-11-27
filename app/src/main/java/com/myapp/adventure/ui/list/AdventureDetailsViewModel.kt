package com.myapp.adventure.ui.list

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.data.AdventureLoadResult
import com.myapp.adventure.data.AdventureLoads
import com.myapp.adventure.engine.ir.Adventure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdventureDetailsUiState(
    val isLoading: Boolean = true,
    val adventure: Adventure? = null,
    val error: Boolean = false,
)

class AdventureDetailsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val fileUri = savedStateHandle.get<String>("fileUri").orEmpty()
    private val _state = MutableStateFlow(AdventureDetailsUiState())
    val state: StateFlow<AdventureDetailsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (fileUri.isBlank()) {
            _state.value = AdventureDetailsUiState(isLoading = false, error = true)
            return
        }
        viewModelScope.launch {
            _state.value = AdventureDetailsUiState(isLoading = true)
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(fileUri.toUri())
                        ?.bufferedReader()
                        ?.use { it.readText() }
                }.getOrNull()
            }
            if (json == null) {
                _state.value = AdventureDetailsUiState(isLoading = false, error = true)
                return@launch
            }
            _state.value = when (val result = AdventureLoads.load(json)) {
                is AdventureLoadResult.Success ->
                    AdventureDetailsUiState(
                        isLoading = false,
                        adventure = result.loaded.adventure,
                    )
                else -> AdventureDetailsUiState(isLoading = false, error = true)
            }
        }
    }
}
