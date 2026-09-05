package com.aiditor.app.data.model

data class Project(
    val id: String,
    val name: String,
    val videoPath: String,
    val thumbnailPath: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val fps: Double,
    val createdAt: String,
    val modifiedAt: String,
    val appliedTools: List<AppliedTool> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList()
)

data class AppliedTool(
    val type: ToolType,
    val label: String,
    val parametersSummary: String
)

data class TimelineMarker(
    val timeSeconds: Double,
    val label: String
)

data class TimelineTrack(
    val id: String,
    val name: String,
    val type: TrackType,
    val clips: List<ClipSegment>
)

enum class TrackType {
    VIDEO,
    AUDIO,
    VFX_EFFECT
}

data class ClipSegment(
    val id: String,
    val title: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val isSelected: Boolean = false
)
