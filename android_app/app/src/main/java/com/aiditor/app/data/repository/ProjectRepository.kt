package com.aiditor.app.data.repository

import com.aiditor.app.bridge.BackendApiClient
import com.aiditor.app.data.model.AppliedTool
import com.aiditor.app.data.model.Project
import com.aiditor.app.data.model.TimelineMarker
import com.aiditor.app.data.model.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    suspend fun createProject(name: String, videoPath: String = ""): Project {
        val remote = apiClient.createProject(name, videoPath)
        val newProj = remote ?: Project(
            id = "proj_${System.currentTimeMillis()}",
            name = name,
            videoPath = videoPath.ifEmpty { "/storage/emulated/0/Movies/raw_cut.mp4" },
            thumbnailPath = "",
            fileSizeBytes = 54525952L,
            fileSizeFormatted = "52.0 MB",
            durationSeconds = 18.5,
            width = 1920,
            height = 1080,
            fps = 60.0,
            createdAt = "2026-09-05 08:30:00",
            modifiedAt = "2026-09-05 08:30:00",
            appliedTools = listOf(
                AppliedTool(ToolType.OPTICAL_FLOW, "Optical Flow", "60 FPS (MCI)"),
                AppliedTool(ToolType.COLOR_GRADE, "Color Grade", "Monochrome Cinema")
            ),
            timelineMarkers = listOf(
                TimelineMarker(3.2, "Intro Cut"),
                TimelineMarker(8.5, "Beat Drop")
            )
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
        fun getDefaultProjects(): List<Project> = listOf(
            Project(
                id = "proj_tokyo_drift_01",
                name = "Tokyo Midnight Drift",
                videoPath = "/storage/emulated/0/Movies/tokyo_drift.mp4",
                thumbnailPath = "",
                fileSizeBytes = 104857600L,
                fileSizeFormatted = "100.0 MB",
                durationSeconds = 32.5,
                width = 1080,
                height = 1920,
                fps = 60.0,
                createdAt = "2026-08-28 14:22:00",
                modifiedAt = "2026-09-04 18:45:12",
                appliedTools = listOf(
                    AppliedTool(ToolType.OPTICAL_FLOW, "Optical Flow", "60 FPS (MCI)"),
                    AppliedTool(ToolType.BEAT_SYNC, "Beat Sync", "Aggressive Drift")
                ),
                timelineMarkers = listOf(
                    TimelineMarker(4.2, "Beat Drop 1"),
                    TimelineMarker(12.8, "Speed Ramp"),
                    TimelineMarker(24.0, "HUD Callout")
                )
            ),
            Project(
                id = "proj_cyber_speed_02",
                name = "Cyberpunk Neon Track",
                videoPath = "/storage/emulated/0/Movies/cyber_track.mp4",
                thumbnailPath = "",
                fileSizeBytes = 48234496L,
                fileSizeFormatted = "46.0 MB",
                durationSeconds = 15.0,
                width = 1920,
                height = 1080,
                fps = 60.0,
                createdAt = "2026-09-01 09:15:30",
                modifiedAt = "2026-09-05 06:10:04",
                appliedTools = listOf(
                    AppliedTool(ToolType.MOTION_TRACKING, "Motion Track", "Target Locked"),
                    AppliedTool(ToolType.COLOR_GRADE, "Color Grade", "Monochrome")
                ),
                timelineMarkers = listOf(
                    TimelineMarker(2.5, "Lock On"),
                    TimelineMarker(8.0, "Neon Outline")
                )
            ),
            Project(
                id = "proj_roto_saber_03",
                name = "Stealth Rotoscope Cut",
                videoPath = "/storage/emulated/0/Movies/stealth_roto.mp4",
                thumbnailPath = "",
                fileSizeBytes = 73400320L,
                fileSizeFormatted = "70.0 MB",
                durationSeconds = 22.0,
                width = 1920,
                height = 1080,
                fps = 30.0,
                createdAt = "2026-09-03 11:00:00",
                modifiedAt = "2026-09-05 07:30:20",
                appliedTools = listOf(
                    AppliedTool(ToolType.ROTOSCOPE, "Rotoscope", "AIDITOR CORE"),
                    AppliedTool(ToolType.SPEED_RAMP, "Speed Ramp", "Flash Impact")
                ),
                timelineMarkers = listOf(
                    TimelineMarker(5.0, "Behind Text")
                )
            )
        )
    }
}
