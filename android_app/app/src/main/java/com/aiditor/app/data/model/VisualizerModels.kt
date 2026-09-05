package com.aiditor.app.data.model

sealed class ToolVisualizerData {

    data class OpticalFlow(
        val sourceFps: Double,
        val targetFps: Int,
        val mode: String,
        val vectors: List<FlowVector>,
        val flowMultiplier: Double
    ) : ToolVisualizerData()

    data class BeatSync(
        val vibe: String,
        val bpm: Int,
        val waveform: List<Float>,
        val beats: List<BeatMarker>,
        val dropCount: Int
    ) : ToolVisualizerData()

    data class MotionTracking(
        val trackingMode: String,
        val keyframes: List<TrackingKeyframe>,
        val averageConfidence: Double
    ) : ToolVisualizerData()

    data class SpeedRamp(
        val preset: String,
        val peakSpeed: Float,
        val samples: List<CurveSample>,
        val controlPoints: List<CurveControlPoint>
    ) : ToolVisualizerData()

    data class ColorGrade(
        val contrast: Float,
        val exposure: Float,
        val saturation: Float,
        val toneCurve: List<Int>,
        val luminanceHistogram: List<Int>
    ) : ToolVisualizerData()

    data class Rotoscope(
        val preset: String,
        val textContent: String,
        val neonColor: String,
        val contourPoints: List<Point2D>
    ) : ToolVisualizerData()
}

data class FlowVector(
    val x: Float,
    val y: Float,
    val dx: Float,
    val dy: Float,
    val magnitude: Float,
    val angleDeg: Float
)

data class BeatMarker(
    val timeSeconds: Double,
    val energy: Float,
    val isDrop: Boolean,
    val recommendedCut: Boolean
)

data class TrackingKeyframe(
    val frame: Int,
    val timeSeconds: Double,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val status: String
)

data class CurveSample(
    val time: Float,
    val value: Float,
    val velocity: Float
)

data class CurveControlPoint(
    val time: Float,
    val speed: Float
)
