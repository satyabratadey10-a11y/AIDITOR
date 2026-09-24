package com.aiditor.app.data.repository

import android.content.Context
import com.aiditor.app.AiditorApp
import com.aiditor.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 100% Standalone On-Device Project Persistence Repository.
 * Serializes and deserializes the entire project state, including multi-clips, trims,
 * speed ramp curves, color grading parameters, optical flow configurations, overlays,
 * and aspect ratios to internal JSON storage (aiditor_projects.json).
 */
class ProjectRepository(
    private val context: Context? = null
) {
    private val _projects = MutableStateFlow<List<Project>>(getDefaultProjects())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val localFile: File? by lazy {
        try {
            val ctx = context ?: AiditorApp.instance
            ctx?.let { File(it.filesDir, "aiditor_projects.json") }
        } catch (_: Exception) {
            null
        }
    }

    init {
        loadFromDisk()
    }

    fun loadFromDisk() {
        try {
            val file = localFile
            if (file != null && file.exists()) {
                val jsonStr = file.readText()
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Project>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(deserializeProject(obj))
                }
                _projects.value = list
            }
        } catch (_: Exception) {
        }
    }

    private fun saveToDisk() {
        try {
            val file = localFile ?: return
            val array = JSONArray()
            _projects.value.forEach { p ->
                array.put(serializeProject(p))
            }
            file.writeText(array.toString(2))
        } catch (_: Exception) {
        }
    }

    private fun serializeProject(p: Project): JSONObject {
        return JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("videoPath", p.videoPath)
            put("thumbnailPath", p.thumbnailPath)
            put("fileSizeBytes", p.fileSizeBytes)
            put("fileSizeFormatted", p.fileSizeFormatted)
            put("durationSeconds", p.durationSeconds)
            put("width", p.width)
            put("height", p.height)
            put("fps", p.fps)
            put("createdAt", p.createdAt)
            put("modifiedAt", p.modifiedAt)
            put("aspectRatio", p.aspectRatio.name)
            put("isAudioMuted", p.isAudioMuted)
            put("trackingMode", p.trackingMode.name)

            // Serialize Clips
            val clipsArray = JSONArray()
            p.clips.forEach { clip ->
                val cObj = JSONObject().apply {
                    put("id", clip.id)
                    put("title", clip.title)
                    put("sourcePath", clip.sourcePath)
                    put("inPointSeconds", clip.inPointSeconds)
                    put("outPointSeconds", clip.outPointSeconds)
                    put("durationSeconds", clip.durationSeconds)
                    put("speedMultiplier", clip.speedMultiplier.toDouble())
                    put("isSelected", clip.isSelected)
                    put("isOpticalFlowEnabled", clip.isOpticalFlowEnabled)
                    put("opticalFlowFps", clip.opticalFlowFps)
                    put("opticalFlowMode", clip.opticalFlowMode)
                    put("opticalFlowCachedUri", clip.opticalFlowCachedUri ?: "")
                    put("isImage", clip.isImage)
                    put("timelineStartSeconds", clip.timelineStartSeconds)
                    put("trackIndex", clip.trackIndex)
                    put("scale", clip.scale.toDouble())
                    put("panX", clip.panX.toDouble())
                    put("panY", clip.panY.toDouble())
                    put("rotation", clip.rotation.toDouble())

                    // Color Grade
                    val cg = clip.colorGrade
                    val cgObj = JSONObject().apply {
                        put("filterPreset", cg.filterPreset)
                        put("lutPreset", cg.lutPreset)
                        put("contrast", cg.contrast.toDouble())
                        put("exposure", cg.exposure.toDouble())
                        put("saturation", cg.saturation.toDouble())
                        put("brightness", cg.brightness.toDouble())
                        put("gamma", cg.gamma.toDouble())
                    }
                    put("colorGrade", cgObj)

                    // Speed Curve Points
                    val curveArray = JSONArray()
                    clip.speedCurvePoints.forEach { pt ->
                        val ptObj = JSONObject().apply {
                            put("time", pt.time.toDouble())
                            put("speed", pt.speed.toDouble())
                        }
                        curveArray.put(ptObj)
                    }
                    put("speedCurvePoints", curveArray)
                }
                clipsArray.put(cObj)
            }
            put("clips", clipsArray)

            // Serialize Overlays
            val overlaysArray = JSONArray()
            p.overlays.forEach { ov ->
                val ovObj = JSONObject().apply {
                    put("id", ov.id)
                    put("type", ov.type.name)
                    put("label", ov.label)
                    put("startTimeSeconds", ov.startTimeSeconds)
                    put("durationSeconds", ov.durationSeconds)
                    put("isSelected", ov.isSelected)
                    put("isProcessing", ov.isProcessing)
                }
                overlaysArray.put(ovObj)
            }
            put("overlays", overlaysArray)

            // Serialize Markers
            val markersArray = JSONArray()
            p.timelineMarkers.forEach { m ->
                val mObj = JSONObject().apply {
                    put("timeSeconds", m.timeSeconds)
                    put("label", m.label)
                }
                markersArray.put(mObj)
            }
            put("timelineMarkers", markersArray)

            // Serialize Applied Tools
            val toolsArray = JSONArray()
            p.appliedTools.forEach { t ->
                val tObj = JSONObject().apply {
                    put("type", t.type.name)
                    put("label", t.label)
                    put("parametersSummary", t.parametersSummary)
                }
                toolsArray.put(tObj)
            }
            put("appliedTools", toolsArray)
        }
    }

    private fun deserializeProject(obj: JSONObject): Project {
        val pId = obj.getString("id")
        val pName = obj.getString("name")
        val vPath = obj.optString("videoPath", "")
        val dur = obj.optDouble("durationSeconds", 10.0)

        // Aspect Ratio
        val arName = obj.optString("aspectRatio", AspectRatioMode.ORIGINAL.name)
        val ar = try { AspectRatioMode.valueOf(arName) } catch (_: Exception) { AspectRatioMode.ORIGINAL }

        // Tracking Mode
        val trName = obj.optString("trackingMode", ActiveTrackingMode.NONE.name)
        val tr = try { ActiveTrackingMode.valueOf(trName) } catch (_: Exception) { ActiveTrackingMode.NONE }

        // Clips
        val clipsList = mutableListOf<TimelineClip>()
        val clipsArray = obj.optJSONArray("clips")
        if (clipsArray != null && clipsArray.length() > 0) {
            for (i in 0 until clipsArray.length()) {
                val cObj = clipsArray.getJSONObject(i)
                val cgObj = cObj.optJSONObject("colorGrade")
                val colorGrade = if (cgObj != null) {
                    MiddleParameters.ColorGrade(
                        filterPreset = cgObj.optString("filterPreset", "original"),
                        lutPreset = cgObj.optString("lutPreset", "monochrome_cinema"),
                        contrast = cgObj.optDouble("contrast", 1.0).toFloat(),
                        exposure = cgObj.optDouble("exposure", 0.0).toFloat(),
                        saturation = cgObj.optDouble("saturation", 1.0).toFloat(),
                        brightness = cgObj.optDouble("brightness", 0.0).toFloat(),
                        gamma = cgObj.optDouble("gamma", 1.0).toFloat()
                    )
                } else {
                    MiddleParameters.ColorGrade()
                }

                val curvePoints = mutableListOf<CurveControlPoint>()
                val curveArray = cObj.optJSONArray("speedCurvePoints")
                if (curveArray != null) {
                    for (c in 0 until curveArray.length()) {
                        val ptObj = curveArray.getJSONObject(c)
                        curvePoints.add(
                            CurveControlPoint(
                                time = ptObj.optDouble("time", 0.0).toFloat(),
                                speed = ptObj.optDouble("speed", 1.0).toFloat()
                            )
                        )
                    }
                }

                val cachedUri = cObj.optString("opticalFlowCachedUri", "").takeIf { it.isNotEmpty() }

                val inSec = cObj.optDouble("inPointSeconds", 0.0)
                val outSec = cObj.optDouble("outPointSeconds", dur)
                val clipDur = cObj.optDouble("durationSeconds", (outSec - inSec).coerceAtLeast(0.1))

                clipsList.add(
                    TimelineClip(
                        id = cObj.optString("id", "clip_${pId}_$i"),
                        title = cObj.optString("title", pName),
                        sourcePath = cObj.optString("sourcePath", vPath),
                        inPointSeconds = inSec,
                        outPointSeconds = outSec,
                        durationSeconds = clipDur,
                        speedMultiplier = cObj.optDouble("speedMultiplier", 1.0).toFloat(),
                        isSelected = cObj.optBoolean("isSelected", i == 0),
                        speedCurvePoints = curvePoints,
                        colorGrade = colorGrade,
                        isOpticalFlowEnabled = cObj.optBoolean("isOpticalFlowEnabled", false),
                        opticalFlowFps = cObj.optInt("opticalFlowFps", 60),
                        opticalFlowMode = cObj.optString("opticalFlowMode", "mci"),
                        opticalFlowCachedUri = cachedUri,
                        isImage = cObj.optBoolean("isImage", false),
                        timelineStartSeconds = cObj.optDouble("timelineStartSeconds", 0.0),
                        trackIndex = cObj.optInt("trackIndex", 0),
                        scale = cObj.optDouble("scale", 1.0).toFloat(),
                        panX = cObj.optDouble("panX", 0.0).toFloat(),
                        panY = cObj.optDouble("panY", 0.0).toFloat(),
                        rotation = cObj.optDouble("rotation", 0.0).toFloat()
                    )
                )
            }
        } else if (vPath.isNotEmpty()) {
            clipsList.add(
                TimelineClip(
                    id = "clip_$pId",
                    title = pName,
                    sourcePath = vPath,
                    inPointSeconds = 0.0,
                    outPointSeconds = dur,
                    durationSeconds = dur,
                    isSelected = true
                )
            )
        }

        // Overlays
        val overlaysList = mutableListOf<TimelineOverlay>()
        val overlaysArray = obj.optJSONArray("overlays")
        if (overlaysArray != null) {
            for (i in 0 until overlaysArray.length()) {
                val ovObj = overlaysArray.getJSONObject(i)
                val typeStr = ovObj.optString("type", OverlayType.SKULL_STICKER.name)
                val type = try { OverlayType.valueOf(typeStr) } catch (_: Exception) { OverlayType.SKULL_STICKER }
                overlaysList.add(
                    TimelineOverlay(
                        id = ovObj.optString("id", "ov_$i"),
                        type = type,
                        label = ovObj.optString("label", ""),
                        startTimeSeconds = ovObj.optDouble("startTimeSeconds", 0.0),
                        durationSeconds = ovObj.optDouble("durationSeconds", 4.0),
                        isSelected = ovObj.optBoolean("isSelected", false),
                        isProcessing = ovObj.optBoolean("isProcessing", false)
                    )
                )
            }
        }

        // Markers
        val markersList = mutableListOf<TimelineMarker>()
        val markersArray = obj.optJSONArray("timelineMarkers")
        if (markersArray != null) {
            for (i in 0 until markersArray.length()) {
                val mObj = markersArray.getJSONObject(i)
                markersList.add(
                    TimelineMarker(
                        timeSeconds = mObj.optDouble("timeSeconds", 0.0),
                        label = mObj.optString("label", "")
                    )
                )
            }
        }

        // Applied Tools
        val appliedToolsList = mutableListOf<AppliedTool>()
        val toolsArray = obj.optJSONArray("appliedTools")
        if (toolsArray != null) {
            for (i in 0 until toolsArray.length()) {
                val tObj = toolsArray.getJSONObject(i)
                val toolStr = tObj.optString("type", ToolType.COLOR_GRADE.name)
                val tType = try { ToolType.valueOf(toolStr) } catch (_: Exception) { ToolType.COLOR_GRADE }
                appliedToolsList.add(
                    AppliedTool(
                        type = tType,
                        label = tObj.optString("label", ""),
                        parametersSummary = tObj.optString("parametersSummary", "")
                    )
                )
            }
        }

        return Project(
            id = pId,
            name = pName,
            videoPath = vPath,
            thumbnailPath = obj.optString("thumbnailPath", ""),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
            fileSizeFormatted = obj.optString("fileSizeFormatted", "0.0 MB"),
            durationSeconds = dur,
            width = obj.optInt("width", 1920),
            height = obj.optInt("height", 1080),
            fps = obj.optDouble("fps", 30.0),
            createdAt = obj.optString("createdAt", ""),
            modifiedAt = obj.optString("modifiedAt", ""),
            appliedTools = appliedToolsList,
            timelineMarkers = markersList,
            clips = clipsList,
            overlays = overlaysList,
            aspectRatio = ar,
            isAudioMuted = obj.optBoolean("isAudioMuted", false),
            trackingMode = tr
        )
    }

    suspend fun refreshProjects() = withContext(Dispatchers.IO) {
        loadFromDisk()
    }

    suspend fun createProject(
        name: String,
        videoPath: String = "",
        fileSizeBytes: Long = 0L,
        fileSizeFormatted: String = "",
        durationSeconds: Double = 10.0,
        width: Int = 1920,
        height: Int = 1080
    ): Project = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedSize = if (fileSizeFormatted.isNotEmpty()) {
            fileSizeFormatted
        } else if (fileSizeBytes > 0) {
            String.format(Locale.US, "%.1f MB", fileSizeBytes / (1024.0 * 1024.0))
        } else {
            "0.0 MB"
        }

        val initialClips: List<TimelineClip> = if (videoPath.isNotEmpty()) {
            listOf(
                TimelineClip(
                    id = "clip_${System.currentTimeMillis()}",
                    title = name,
                    sourcePath = videoPath,
                    inPointSeconds = 0.0,
                    outPointSeconds = durationSeconds,
                    durationSeconds = durationSeconds,
                    isSelected = true
                )
            )
        } else emptyList()

        val newProj = Project(
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
            timelineMarkers = emptyList(),
            clips = initialClips
        )

        val current = _projects.value.toMutableList()
        current.add(0, newProj)
        _projects.value = current
        saveToDisk()
        newProj
    }

    fun updateProject(project: Project) {
        val current = _projects.value.toMutableList()
        val index = current.indexOfFirst { it.id == project.id }
        if (index != -1) {
            current[index] = project
        } else {
            current.add(0, project)
        }
        _projects.value = current
        saveToDisk()
    }

    fun getProject(id: String): Project? {
        return _projects.value.find { it.id == id }
    }

    fun deleteProject(id: String) {
        _projects.value = _projects.value.filter { it.id != id }
        saveToDisk()
    }

    companion object {
        fun getDefaultProjects(): List<Project> = emptyList()
    }
}
