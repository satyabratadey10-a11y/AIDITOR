package com.aiditor.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Project(
    val id: String,
    val name: String,
    val videoPath: String,
    val thumbnailPath: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "0.0 MB",
    val durationSeconds: Double = 10.0,
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Double = 30.0,
    val createdAt: String = "",
    val modifiedAt: String = "",
    val appliedTools: List<AppliedTool> = emptyList(),
    val timelineMarkers: List<TimelineMarker> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    val overlays: List<TimelineOverlay> = emptyList(),
    val aspectRatio: AspectRatioMode = AspectRatioMode.ORIGINAL,
    val isAudioMuted: Boolean = false,
    val trackingMode: ActiveTrackingMode = ActiveTrackingMode.NONE
)

@Immutable
data class TimelineClip(
    val id: String,
    val title: String,
    val sourcePath: String,
    val inPointSeconds: Double = 0.0,
    val outPointSeconds: Double = 10.0,
    val durationSeconds: Double = (outPointSeconds - inPointSeconds).coerceAtLeast(0.1),
    val speedMultiplier: Float = 1.0f,
    val isSelected: Boolean = false
)

enum class OverlayType {
    SKULL_STICKER,
    OK_STICKER,
    TRACKING_EFFECT,
    STABILIZATION_EFFECT,
    FACE_TRACK_EFFECT,
    TEXT_OVERLAY
}

@Immutable
data class TimelineOverlay(
    val id: String,
    val type: OverlayType,
    val label: String,
    val startTimeSeconds: Double = 0.0,
    val durationSeconds: Double = 5.0,
    val isSelected: Boolean = false,
    val isProcessing: Boolean = false
)

enum class AspectRatioMode(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    RATIO_1_1("1:1", 1.0f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_5("4:5", 4f / 5f)
}

enum class ActiveTrackingMode {
    NONE,
    MOTION_TRACKING,
    MOTION_STABILIZATION,
    FACE_TRACKING
}

@Immutable
data class AppliedTool(
    val type: ToolType,
    val label: String,
    val parametersSummary: String
)

@Immutable
data class TimelineMarker(
    val timeSeconds: Double,
    val label: String
)

@Immutable
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

@Immutable
data class ClipSegment(
    val id: String,
    val title: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val isSelected: Boolean = false
)
