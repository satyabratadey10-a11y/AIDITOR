package com.aiditor.app.data.repository

import com.aiditor.app.bridge.BackendApiClient
import com.aiditor.app.data.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectRepository(
    private val apiClient: BackendApiClient = BackendApiClient()
) {
    private val _projects = MutableStateFlow<List<Project>>(getDefaultProjects())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun refreshProjects() {
        val remote = apiClient.fetchProjects()
        if (remote.isNotEmpty()) {
            _projects.value = remote
        }
    }

    suspend fun createProject(
        name: String,
        videoPath: String = "",
        fileSizeBytes: Long = 0L,
        fileSizeFormatted: String = "",
        durationSeconds: Double = 10.0,
        width: Int = 1920,
        height: Int = 1080
    ): Project {
        val remote = apiClient.createProject(name, videoPath)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedSize = if (fileSizeFormatted.isNotEmpty()) {
            fileSizeFormatted
        } else if (fileSizeBytes > 0) {
            String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
        } else {
            "0.0 MB"
        }

        val newProj = remote ?: Project(
            id = "proj_${System.currentTimeMillis()}",
            name = name,
            videoPath = videoPath,
            thumbnailPath = "",
            fileSizeBytes = fileSizeBytes,
            fileSizeFormatted = formattedSize,
            durationSeconds = durationSeconds,
            width = width,
            height = height,
            fps = 30.0,
            createdAt = now,
            modifiedAt = now,
            appliedTools = emptyList(),
            timelineMarkers = emptyList()
        )

        val current = _projects.value.toMutableList()
        current.add(0, newProj)
        _projects.value = current
        return newProj
    }

    fun getProject(id: String): Project? {
        return _projects.value.find { it.id == id }
    }

    fun deleteProject(id: String) {
        _projects.value = _projects.value.filter { it.id != id }
    }

    companion object {
        // No placeholder projects by default - starts with clean empty state
        fun getDefaultProjects(): List<Project> = emptyList()
    }
}
