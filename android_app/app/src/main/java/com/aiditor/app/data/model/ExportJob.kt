package com.aiditor.app.data.model

data class ExportJob(
    val jobId: String,
    val status: ExportStatus,
    val progressPercentage: Float,
    val message: String,
    val outputPath: String,
    val startedAt: Long,
    val completedAt: Long? = null
)

enum class ExportStatus {
    IDLE,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class ExportSettings(
    val resolution: String = "1080p", // 720p, 1080p, 4k
    val fps: Int = 60,
    val format: String = "mp4",
    val qualityCrf: Int = 18,
    val codec: String = "libx264"
)
