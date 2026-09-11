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
        val isEnabled: Boolean = false,
        val targetFps: Int = 60,
        val flowMode: String = "mci", // "mci" or "blend"
        val scdThreshold: Double = 10.0,
        val colorGrade: Boolean = true,
        val slowMoFactor: Float = 1.0f
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
        val preset: String = "hero_moment", // "hero_moment", "bullet_time", "montage", "flash_in", "flash_out", "custom", "linear"
        val durationSeconds: Double = 2.0,
        val maxSpeedMultiplier: Float = 2.5f,
        val curveControlPoints: List<CurveControlPoint> = listOf(
            CurveControlPoint(0.0f, 1.0f),
            CurveControlPoint(0.35f, 0.2f),
            CurveControlPoint(0.7f, 2.5f),
            CurveControlPoint(1.0f, 1.0f)
        )
    ) : MiddleParameters()

    data class ColorGrade(
        val filterPreset: String = "original",
        val lutPreset: String = "monochrome_cinema",
        val contrast: Float = 1.0f,
        val exposure: Float = 0.0f,
        val saturation: Float = 1.0f,
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
