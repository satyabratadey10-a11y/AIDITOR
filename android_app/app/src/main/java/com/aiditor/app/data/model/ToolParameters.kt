package com.aiditor.app.data.model

/**
 * Encapsulates full access to modify:
 * 1. Input part: source video, in/out trim points, stream index, audio mute
 * 2. Middle part: tool specific processing algorithm parameters
 * 3. Output part: resolution, target fps, codec, quality/crf
 */
data class ToolConfiguration(
    val toolType: ToolType,
    val inputPart: InputParameters,
    val middlePart: MiddleParameters,
    val outputPart: OutputParameters
)

data class InputParameters(
    val sourcePath: String = "",
    val inPointSeconds: Double = 0.0,
    val outPointSeconds: Double? = null,
    val muteAudio: Boolean = false,
    val streamIndex: Int = 0
)

sealed class MiddleParameters {
    data class OpticalFlow(
        val targetFps: Int = 60,
        val flowMode: String = "mci", // "mci" or "blend"
        val scdThreshold: Double = 10.0,
        val colorGrade: Boolean = true
    ) : MiddleParameters()

    data class BeatSync(
        val vibe: String = "aggressive_drift", // "aggressive_drift", "chill_neon", "speed_ramp_chaos"
        val beatSensitivity: Double = 0.8,
        val cutFrequency: String = "medium"
    ) : MiddleParameters()

    data class MotionTracking(
        val trackingMode: String = "hud_callout", // "hud_callout", "point_track", "face_lock"
        val targetX: Float = 0.5f,
        val targetY: Float = 0.5f,
        val hudTitle: String = "TARGET LOCKED",
        val hudSubtitle: String = "TRACKING ACTIVE",
        val hudColor: String = "0xFFFFFF"
    ) : MiddleParameters()

    data class SpeedRamp(
        val preset: String = "flash_impact_ramp", // "flash_impact_ramp", "smooth_flow", "crash_zoom_in"
        val durationSeconds: Double = 2.0,
        val maxSpeedMultiplier: Float = 2.5f,
        val curveControlPoints: List<Point2D> = emptyList()
    ) : MiddleParameters()

    data class ColorGrade(
        val lutPreset: String = "monochrome_cinema",
        val contrast: Float = 1.25f,
        val exposure: Float = 0.0f,
        val saturation: Float = 0.0f, // Pure monochrome
        val brightness: Float = 0.0f,
        val gamma: Float = 1.0f
    ) : MiddleParameters()

    data class Rotoscope(
        val preset: String = "behind_text", // "behind_text", "dual_tone", "neon_saber"
        val textContent: String = "AIDITOR",
        val neonColor: String = "white",
        val maskFeather: Float = 3.0f
    ) : MiddleParameters()
}

data class Point2D(
    val x: Float,
    val y: Float
)

data class OutputParameters(
    val outputPath: String = "",
    val resolution: String = "1080p", // "480p", "720p", "1080p", "4k"
    val fps: Int = 60,
    val codec: String = "libx264",
    val crf: Int = 18,
    val isPreview: Boolean = false
)
