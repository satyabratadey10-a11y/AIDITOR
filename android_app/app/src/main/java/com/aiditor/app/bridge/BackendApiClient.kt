package com.aiditor.app.bridge

import com.aiditor.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendApiClient(
    private val baseUrl: String = "http://127.0.0.1:8080"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchProjects(): List<Project> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/projects")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val array = json.optJSONArray("projects") ?: JSONArray()
                val list = mutableListOf<Project>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(parseProject(obj))
                }
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createProject(name: String, videoPath: String): Project? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", name)
                put("video_path", videoPath)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/projects")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseProject(JSONObject(body))
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchVisualizerData(
        toolType: ToolType,
        inputPart: InputParameters,
        middlePart: MiddleParameters
    ): ToolVisualizerData? = withContext(Dispatchers.IO) {
        try {
            val toolStr = when (toolType) {
                ToolType.OPTICAL_FLOW -> "optical_flow"
                ToolType.BEAT_SYNC -> "beat_sync"
                ToolType.MOTION_TRACKING -> "motion_tracking"
                ToolType.SPEED_RAMP -> "speed_ramp"
                ToolType.COLOR_GRADE -> "color_grade"
                ToolType.ROTOSCOPE -> "rotoscope"
            }

            val inputJson = JSONObject().apply {
                put("source_path", inputPart.sourcePath)
                put("in_point_seconds", inputPart.inPointSeconds)
                inputPart.outPointSeconds?.let { put("out_point_seconds", it) }
            }

            val middleJson = JSONObject().apply {
                when (middlePart) {
                    is MiddleParameters.OpticalFlow -> {
                        put("target_fps", middlePart.targetFps)
                        put("flow_mode", middlePart.flowMode)
                        put("scd_threshold", middlePart.scdThreshold)
                    }
                    is MiddleParameters.BeatSync -> {
                        put("vibe", middlePart.vibe)
                        put("beat_sensitivity", middlePart.beatSensitivity)
                    }
                    is MiddleParameters.MotionTracking -> {
                        put("target_x", middlePart.targetX.toDouble())
                        put("target_y", middlePart.targetY.toDouble())
                        put("hud_title", middlePart.hudTitle)
                    }
                    is MiddleParameters.SpeedRamp -> {
                        put("ramp_preset", middlePart.preset)
                        put("duration_seconds", middlePart.durationSeconds)
                    }
                    is MiddleParameters.ColorGrade -> {
                        put("contrast", middlePart.contrast.toDouble())
                        put("exposure", middlePart.exposure.toDouble())
                        put("saturation", middlePart.saturation.toDouble())
                    }
                    is MiddleParameters.Rotoscope -> {
                        put("roto_preset", middlePart.preset)
                        put("text_content", middlePart.textContent)
                    }
                }
            }

            val requestJson = JSONObject().apply {
                put("tool_type", toolStr)
                put("input", inputJson)
                put("middle", middleJson)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/tools/visualize")
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val visObj = json.optJSONObject("visualizer") ?: return@withContext null
                parseVisualizerData(toolType, visObj)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun triggerExport(
        toolType: ToolType,
        inputPart: InputParameters,
        middlePart: MiddleParameters,
        outputPart: OutputParameters
    ): ExportJob? = withContext(Dispatchers.IO) {
        try {
            val toolStr = when (toolType) {
                ToolType.OPTICAL_FLOW -> "optical_flow"
                ToolType.BEAT_SYNC -> "beat_sync"
                ToolType.MOTION_TRACKING -> "motion_tracking"
                ToolType.SPEED_RAMP -> "speed_ramp"
                ToolType.COLOR_GRADE -> "color_grade"
                ToolType.ROTOSCOPE -> "rotoscope"
            }

            val json = JSONObject().apply {
                put("tool_type", toolStr)
                put("input", JSONObject().apply {
                    put("source_path", inputPart.sourcePath)
                    put("in_point_seconds", inputPart.inPointSeconds)
                    inputPart.outPointSeconds?.let { put("out_point_seconds", it) }
                })
                put("output", JSONObject().apply {
                    put("resolution", outputPart.resolution)
                    put("fps", outputPart.fps)
                    put("codec", outputPart.codec)
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/api/render/export")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val resObj = JSONObject(body)
                ExportJob(
                    jobId = resObj.optString("job_id", "job_1"),
                    status = ExportStatus.PROCESSING,
                    progressPercentage = resObj.optDouble("progress_percentage", 0.0).toFloat(),
                    message = resObj.optString("message", "Processing..."),
                    outputPath = resObj.optString("output_path", ""),
                    startedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseProject(obj: JSONObject): Project {
        return Project(
            id = obj.optString("id", "proj_0"),
            name = obj.optString("name", "Untitled"),
            videoPath = obj.optString("video_path", ""),
            thumbnailPath = obj.optString("thumbnail_path", ""),
            fileSizeBytes = obj.optLong("file_size_bytes", 1024 * 1024 * 10),
            fileSizeFormatted = obj.optString("file_size_formatted", "10.0 MB"),
            durationSeconds = obj.optDouble("duration_seconds", 10.0),
            width = obj.optInt("width", 1920),
            height = obj.optInt("height", 1080),
            fps = obj.optDouble("fps", 30.0),
            createdAt = obj.optString("created_at", "2026-09-01"),
            modifiedAt = obj.optString("modified_at", "2026-09-05")
        )
    }

    private fun parseVisualizerData(toolType: ToolType, obj: JSONObject): ToolVisualizerData? {
        return when (toolType) {
            ToolType.OPTICAL_FLOW -> {
                val vecArray = obj.optJSONArray("vectors") ?: JSONArray()
                val vectors = mutableListOf<FlowVector>()
                for (i in 0 until vecArray.length()) {
                    val v = vecArray.getJSONObject(i)
                    vectors.add(
                        FlowVector(
                            x = v.optDouble("x", 0.0).toFloat(),
                            y = v.optDouble("y", 0.0).toFloat(),
                            dx = v.optDouble("dx", 0.0).toFloat(),
                            dy = v.optDouble("dy", 0.0).toFloat(),
                            magnitude = v.optDouble("magnitude", 0.0).toFloat(),
                            angleDeg = v.optDouble("angle_deg", 0.0).toFloat()
                        )
                    )
                }
                ToolVisualizerData.OpticalFlow(
                    sourceFps = obj.optDouble("source_fps", 30.0),
                    targetFps = obj.optInt("target_fps", 60),
                    mode = obj.optString("mode", "mci"),
                    vectors = vectors,
                    flowMultiplier = obj.optDouble("flow_multiplier", 2.0)
                )
            }
            ToolType.BEAT_SYNC -> {
                val waveArray = obj.optJSONArray("waveform") ?: JSONArray()
                val waveform = mutableListOf<Float>()
                for (i in 0 until waveArray.length()) {
                    waveform.add(waveArray.optDouble(i, 0.0).toFloat())
                }
                val beatArray = obj.optJSONArray("beats") ?: JSONArray()
                val beats = mutableListOf<BeatMarker>()
                for (i in 0 until beatArray.length()) {
                    val b = beatArray.getJSONObject(i)
                    beats.add(
                        BeatMarker(
                            timeSeconds = b.optDouble("time_seconds", 0.0),
                            energy = b.optDouble("energy", 1.0).toFloat(),
                            isDrop = b.optBoolean("is_drop", false),
                            recommendedCut = b.optBoolean("recommended_cut", false)
                        )
                    )
                }
                ToolVisualizerData.BeatSync(
                    vibe = obj.optString("vibe", "aggressive_drift"),
                    bpm = obj.optInt("bpm", 140),
                    waveform = waveform,
                    beats = beats,
                    dropCount = obj.optInt("drop_count", 0)
                )
            }
            ToolType.MOTION_TRACKING -> {
                val kfArray = obj.optJSONArray("keyframes") ?: JSONArray()
                val keyframes = mutableListOf<TrackingKeyframe>()
                for (i in 0 until kfArray.length()) {
                    val kf = kfArray.getJSONObject(i)
                    keyframes.add(
                        TrackingKeyframe(
                            frame = kf.optInt("frame", i),
                            timeSeconds = kf.optDouble("time_seconds", 0.0),
                            x = kf.optDouble("x", 0.5).toFloat(),
                            y = kf.optDouble("y", 0.5).toFloat(),
                            width = kf.optDouble("width", 0.15).toFloat(),
                            height = kf.optDouble("height", 0.15).toFloat(),
                            confidence = kf.optDouble("confidence", 0.9).toFloat(),
                            status = kf.optString("status", "LOCKED")
                        )
                    )
                }
                ToolVisualizerData.MotionTracking(
                    trackingMode = obj.optString("tracking_mode", "hud_callout"),
                    keyframes = keyframes,
                    averageConfidence = obj.optDouble("average_confidence", 0.95)
                )
            }
            ToolType.SPEED_RAMP -> {
                val sampleArray = obj.optJSONArray("samples") ?: JSONArray()
                val samples = mutableListOf<CurveSample>()
                for (i in 0 until sampleArray.length()) {
                    val s = sampleArray.getJSONObject(i)
                    samples.add(
                        CurveSample(
                            time = s.optDouble("time", 0.0).toFloat(),
                            value = s.optDouble("value", 1.0).toFloat(),
                            velocity = s.optDouble("velocity", 0.0).toFloat()
                        )
                    )
                }
                ToolVisualizerData.SpeedRamp(
                    preset = obj.optString("preset", "flash_impact_ramp"),
                    peakSpeed = obj.optDouble("peak_speed", 2.5).toFloat(),
                    samples = samples,
                    controlPoints = emptyList()
                )
            }
            ToolType.COLOR_GRADE -> {
                val toneArray = obj.optJSONArray("tone_curve") ?: JSONArray()
                val toneCurve = mutableListOf<Int>()
                for (i in 0 until toneArray.length()) {
                    toneCurve.add(toneArray.optInt(i, i))
                }
                val histObj = obj.optJSONObject("histogram")
                val lumArray = histObj?.optJSONArray("luminance") ?: JSONArray()
                val lumHist = mutableListOf<Int>()
                for (i in 0 until lumArray.length()) {
                    lumHist.add(lumArray.optInt(i, 0))
                }
                ToolVisualizerData.ColorGrade(
                    contrast = obj.optDouble("contrast", 1.2).toFloat(),
                    exposure = obj.optDouble("exposure", 0.0).toFloat(),
                    saturation = obj.optDouble("saturation", 0.0).toFloat(),
                    toneCurve = toneCurve,
                    luminanceHistogram = lumHist
                )
            }
            ToolType.ROTOSCOPE -> {
                val ptArray = obj.optJSONArray("contour_points") ?: JSONArray()
                val points = mutableListOf<Point2D>()
                for (i in 0 until ptArray.length()) {
                    val pt = ptArray.getJSONObject(i)
                    points.add(Point2D(pt.optDouble("x", 0.5).toFloat(), pt.optDouble("y", 0.5).toFloat()))
                }
                ToolVisualizerData.Rotoscope(
                    preset = obj.optString("preset", "behind_text"),
                    textContent = obj.optString("text_content", "AIDITOR"),
                    neonColor = obj.optString("neon_color", "white"),
                    contourPoints = points
                )
            }
        }
    }
}
