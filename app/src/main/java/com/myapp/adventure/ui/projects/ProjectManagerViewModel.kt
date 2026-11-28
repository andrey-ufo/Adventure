package com.myapp.adventure.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.adventure.R
import com.myapp.adventure.projects.ProjectRef
import com.myapp.adventure.projects.SafProjectRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ProjectManagerUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectRef> = emptyList(),
)

sealed interface ProjectManagerEvent {
    data class Open(val project: ProjectRef) : ProjectManagerEvent
    data class Deleted(val project: ProjectRef) : ProjectManagerEvent
    data class Message(val text: String, val long: Boolean = false) : ProjectManagerEvent
}

class ProjectManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SafProjectRepository(application)
    private val _state = MutableStateFlow(ProjectManagerUiState())
    val state: StateFlow<ProjectManagerUiState> = _state.asStateFlow()
    private val _events = Channel<ProjectManagerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            _state.value = ProjectManagerUiState(
                isLoading = false,
                projects = repository.list(),
            )
        }
    }

    fun createProject() {
        viewModelScope.launch {
            val project = repository.create("Новый квест")
            if (project == null) {
                _events.send(
                    ProjectManagerEvent.Message(
                        getApplication<Application>().getString(R.string.projects_create_error),
                        long = true,
                    )
                )
                return@launch
            }
            reload()
            _events.send(ProjectManagerEvent.Open(project))
        }
    }

    fun duplicate(project: ProjectRef) {
        viewModelScope.launch {
            val copy = repository.duplicate(project)
            if (copy == null) {
                _events.send(
                    ProjectManagerEvent.Message(
                        getApplication<Application>().getString(R.string.projects_duplicate_error),
                        long = true,
                    )
                )
                return@launch
            }
            reload()
            _events.send(ProjectManagerEvent.Open(copy))
        }
    }

    fun delete(project: ProjectRef) {
        viewModelScope.launch {
            if (!repository.delete(project)) {
                _events.send(
                    ProjectManagerEvent.Message(
                        getApplication<Application>().getString(R.string.projects_delete_error),
                        long = true,
                    )
                )
                return@launch
            }
            reload()
            _events.send(ProjectManagerEvent.Deleted(project))
        }
    }
}
