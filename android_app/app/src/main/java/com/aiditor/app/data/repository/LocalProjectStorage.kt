package com.aiditor.app.data.repository

import android.content.Context
import com.aiditor.app.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local JSON persistence for standalone project management.
 * Stores projects in internal app storage (context.filesDir / "projects_data.json").
 * Completely independent of Termux and localhost API.
 */
object LocalProjectStorage {

    private const val FILE_NAME = "projects_data.json"

    fun loadProjects(context: Context?): List<Project> {
        if (context == null) return emptyList()
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val content = file.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<Project>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(deserializeProject(obj))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProjects(context: Context?, projects: List<Project>) {
        if (context == null) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            val jsonArray = JSONArray()
            for (p in projects) {
                jsonArray.put(serializeProject(p))
            }
            file.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    private fun serializeProject(p: Project): JSONObject {
        val obj = JSONObject()
        obj.put("id", p.id)
        obj.put("name", p.name)
        obj.put("video_path", p.videoPath)
        obj.put("thumbnail_path", p.thumbnailPath)
        obj.put("file_size_bytes", p.fileSizeBytes)
        obj.put("file_size_formatted", p.fileSizeFormatted)
        obj.put("duration_seconds", p.durationSeconds)
        obj.put("width", p.width)
        obj.put("height", p.height)
        obj.put("fps", p.fps)
        obj.put("created_at", p.createdAt)
        obj.put("modified_at", p.modifiedAt)
        obj.put("is_muted", p.isAudioMuted)
        obj.put("aspect_ratio", p.aspectRatio.name)
        obj.put("tracking_mode", p.trackingMode.name)

        val clipsArray = JSONArray()
        for (c in p.clips) {
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("title", c.title)
            cObj.put("source_path", c.sourcePath)
            cObj.put("in_point_seconds", c.inPointSeconds)
            cObj.put("out_point_seconds", c.outPointSeconds)
            cObj.put("speed_multiplier", c.speedMultiplier.toDouble())
            clipsArray.put(cObj)
        }
        obj.put("clips", clipsArray)

        return obj
    }

    private fun deserializeProject(obj: JSONObject): Project {
        val clipsList = mutableListOf<TimelineClip>()
        val clipsArray = obj.optJSONArray("clips")
        if (clipsArray != null) {
            for (i in 0 until clipsArray.length()) {
                val cObj = clipsArray.getJSONObject(i)
                clipsList.add(
                    TimelineClip(
                        id = cObj.optString("id", "clip_$i"),
                        title = cObj.optString("title", "Clip $i"),
                        sourcePath = cObj.optString("source_path", ""),
                        inPointSeconds = cObj.optDouble("in_point_seconds", 0.0),
                        outPointSeconds = cObj.optDouble("out_point_seconds", 10.0),
                        speedMultiplier = cObj.optDouble("speed_multiplier", 1.0).toFloat()
                    )
                )
            }
        }

        val arName = obj.optString("aspect_ratio", AspectRatioMode.ORIGINAL.name)
        val ar = try { AspectRatioMode.valueOf(arName) } catch (_: Exception) { AspectRatioMode.ORIGINAL }

        val trName = obj.optString("tracking_mode", ActiveTrackingMode.NONE.name)
        val tr = try { ActiveTrackingMode.valueOf(trName) } catch (_: Exception) { ActiveTrackingMode.NONE }

        return Project(
            id = obj.optString("id", "p_${System.currentTimeMillis()}"),
            name = obj.optString("name", "Untitled"),
            videoPath = obj.optString("video_path", ""),
            thumbnailPath = obj.optString("thumbnail_path", ""),
            fileSizeBytes = obj.optLong("file_size_bytes", 0L),
            fileSizeFormatted = obj.optString("file_size_formatted", "0.0 MB"),
            durationSeconds = obj.optDouble("duration_seconds", 10.0),
            width = obj.optInt("width", 1920),
            height = obj.optInt("height", 1080),
            fps = obj.optDouble("fps", 30.0),
            createdAt = obj.optString("created_at", ""),
            modifiedAt = obj.optString("modified_at", ""),
            isAudioMuted = obj.optBoolean("is_muted", false),
            aspectRatio = ar,
            trackingMode = tr,
            clips = clipsList
        )
    }
}
