package com.aiditor.app.ui.screens.mainmenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiditor.app.data.model.Project
import com.aiditor.app.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainMenuUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val errorMessage: String? = null
)

class MainMenuViewModel(
    private val repository: ProjectRepository = ProjectRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainMenuUiState(isLoading = true))
    val uiState: StateFlow<MainMenuUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.refreshProjects()
            repository.projects.collect { list ->
                _uiState.value = _uiState.value.copy(
                    projects = list,
                    isLoading = false
                )
            }
        }
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateDialog = show)
    }

    fun createProject(
        name: String,
        videoPath: String,
        fileSizeBytes: Long = 0L,
        fileSizeFormatted: String = "",
        durationSeconds: Double = 10.0,
        width: Int = 1920,
        height: Int = 1080,
        onCreated: (Project) -> Unit
    ) {
        viewModelScope.launch {
            val newProj = repository.createProject(
                name = name,
                videoPath = videoPath,
                fileSizeBytes = fileSizeBytes,
                fileSizeFormatted = fileSizeFormatted,
                durationSeconds = durationSeconds,
                width = width,
                height = height
            )
            showCreateDialog(false)
            onCreated(newProj)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
        }
    }
}
