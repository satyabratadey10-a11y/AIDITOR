package com.aiditor.app.data.repository

import android.content.Context
import com.aiditor.app.AiditorApp
import com.aiditor.app.data.model.Project
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
 * Zero localhost network traffic or Termux server dependencies.
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

    private fun loadFromDisk() {
        try {
            val file = localFile
            if (file != null && file.exists()) {
                val jsonStr = file.readText()
                val array = JSONArray(jsonStr)
                val list = mutableListOf<Project>()
                for (i in 0 until array.length()) {
                    val pId = obj.getString("id")
                    val pName = obj.getString("name")
                    val vPath = obj.optString("videoPath", "")
                    val dur = obj.optDouble("durationSeconds", 10.0)
                    val initialClips = if (vPath.isNotEmpty()) {
                        listOf(
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
                    } else emptyList()

                    list.add(
                        Project(
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
                            appliedTools = emptyList(),
                            timelineMarkers = emptyList(),
                            clips = initialClips
                        )
                    )
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
                val obj = JSONObject().apply {
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
                }
                array.put(obj)
            }
            file.writeText(array.toString(2))
        } catch (_: Exception) {
        }
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

        val initialClips = if (videoPath.isNotEmpty()) {
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

    fun getProject(id: String): Project? {
        return _projects.value.find { it.id == id }
    }

    fun deleteProject(id: String) {
        _projects.value = _projects.value.filter { it.id != id }
        saveToDisk()
    }

    companion object {
        // Starts with 0 placeholder projects
        fun getDefaultProjects(): List<Project> = emptyList()
    }
}
