package com.aiditor.app.remote

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import androidx.navigation.NavController
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.screens.mainmenu.MainMenuViewModel
import com.aiditor.app.ui.screens.workspace.WorkspaceViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * High-performance, Real-Time Automation & MCP Bridge for AIDITOR.
 * Enables live remote control of the Android app via the Model Context Protocol (MCP):
 * - Physical touch event injection (ACTION_DOWN, ACTION_MOVE, ACTION_UP) directly into Window decorView.
 * - Hardware PixelCopy frame capture to disk for live AI vision inspection.
 * - Comprehensive UI state serialization (screen, clips, timeline, active tools).
 * - Direct execution of workspace editing actions.
 * - Real-time in-app logcat buffering.
 */
object McpBridge {

    private var currentActivityRef: WeakReference<Activity>? = null
    private var workspaceViewModelRef: WeakReference<WorkspaceViewModel>? = null
    private var mainMenuViewModelRef: WeakReference<MainMenuViewModel>? = null
    private var navControllerRef: WeakReference<NavController>? = null

    // In-memory circular log buffer
    private val logBuffer = CopyOnWriteArrayList<String>()
    private const val MAX_LOGS = 200

    fun log(tag: String, message: String) {
        val entry = "[${System.currentTimeMillis()}] [$tag] $message"
        if (logBuffer.size >= MAX_LOGS) {
            logBuffer.removeAt(0)
        }
        logBuffer.add(entry)
        android.util.Log.i(tag, message)
    }

    fun registerActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        log("McpBridge", "Registered activity: ${activity.localClassName}")
    }

    fun unregisterActivity(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
            log("McpBridge", "Unregistered activity: ${activity.localClassName}")
        }
    }

    fun registerViewModels(
        workspaceVm: WorkspaceViewModel?,
        mainMenuVm: MainMenuViewModel?,
        navController: NavController?
    ) {
        if (workspaceVm != null) workspaceViewModelRef = WeakReference(workspaceVm)
        if (mainMenuVm != null) mainMenuViewModelRef = WeakReference(mainMenuVm)
        if (navController != null) navControllerRef = WeakReference(navController)
        log("McpBridge", "ViewModels registered in McpBridge")
    }

    /**
     * Captures a real hardware screenshot of the app's current window using PixelCopy.
     * Saves the resulting image to /sdcard/Download/aiditor_live_screen.png and the app's cache directory.
     */
    fun takeScreenshot(outputFileOverride: File? = null): Map<String, Any> {
        val activity = currentActivityRef?.get()
            ?: return mapOf("status" to "error", "message" to "No active Activity attached to McpBridge")

        val decorView = activity.window.decorView
        val width = decorView.width.coerceAtLeast(200)
        val height = decorView.height.coerceAtLeast(200)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val latch = CountDownLatch(1)
        var isCaptured = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            decorView.getLocationInWindow(location)
            val rect = Rect(location[0], location[1], location[0] + width, location[1] + height)

            try {
                PixelCopy.request(
                    activity.window,
                    rect,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            isCaptured = true
                        }
                        latch.countDown()
                    },
                    Handler(Looper.getMainLooper())
                )
                latch.await(3, TimeUnit.SECONDS)
            } catch (e: Exception) {
                log("McpBridge", "PixelCopy failed: ${e.message}")
            }
        }

        // Fallback to view drawing cache if PixelCopy failed or timed out
        if (!isCaptured) {
            val fallbackLatch = CountDownLatch(1)
            activity.runOnUiThread {
                try {
                    val canvas = Canvas(bitmap)
                    decorView.draw(canvas)
                    isCaptured = true
                } catch (e: Exception) {
                    log("McpBridge", "Fallback canvas draw failed: ${e.message}")
                } finally {
                    fallbackLatch.countDown()
                }
            }
            fallbackLatch.await(2, TimeUnit.SECONDS)
        }

        val primaryOutFile = outputFileOverride ?: File("/sdcard/Download/aiditor_live_screen.png")
        try {
            primaryOutFile.parentFile?.mkdirs()
            FileOutputStream(primaryOutFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            log("McpBridge", "Saved screenshot to ${primaryOutFile.absolutePath}")
        } catch (e: Exception) {
            log("McpBridge", "Error writing to primary screenshot file: ${e.message}")
        }

        // Always also save to app external cache directory as guaranteed fallback
        val cacheOutFile = File(activity.getExternalFilesDir(null), "aiditor_live_screen.png")
        try {
            FileOutputStream(cacheOutFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (_: Exception) {}

        return mapOf(
            "status" to "ok",
            "path" to primaryOutFile.absolutePath,
            "cachePath" to cacheOutFile.absolutePath,
            "width" to width,
            "height" to height,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Injects a genuine touch tap/click directly into the Android window decorView.
     * Supports normalized coordinates (0.0 to 1.0) or exact pixel coordinates.
     */
    fun dispatchTap(normX: Float, normY: Float, pixelX: Float? = null, pixelY: Float? = null): Map<String, Any> {
        val activity = currentActivityRef?.get()
            ?: return mapOf("status" to "error", "message" to "No active Activity attached")

        val decorView = activity.window.decorView
        val w = decorView.width.toFloat().coerceAtLeast(1f)
        val h = decorView.height.toFloat().coerceAtLeast(1f)
        val targetX = pixelX ?: (normX * w).coerceIn(0f, w)
        val targetY = pixelY ?: (normY * h).coerceIn(0f, h)

        activity.runOnUiThread {
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                targetX, targetY, 0
            )
            decorView.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            Handler(Looper.getMainLooper()).postDelayed({
                val upTime = SystemClock.uptimeMillis()
                val upEvent = MotionEvent.obtain(
                    downTime, upTime, MotionEvent.ACTION_UP,
                    targetX, targetY, 0
                )
                decorView.dispatchTouchEvent(upEvent)
                upEvent.recycle()
            }, 60)
        }

        log("McpBridge", "Dispatched tap at ($targetX, $targetY) [norm: ($normX, $normY)]")
        return mapOf(
            "status" to "ok",
            "x" to targetX,
            "y" to targetY,
            "normX" to (targetX / w),
            "normY" to (targetY / h)
        )
    }

    /**
     * Injects a real smooth drag gesture (ACTION_DOWN -> interpolated ACTION_MOVEs -> ACTION_UP).
     */
    fun dispatchDrag(
        startXNorm: Float, startYNorm: Float,
        endXNorm: Float, endYNorm: Float,
        durationMs: Long = 350,
        steps: Int = 16
    ): Map<String, Any> {
        val activity = currentActivityRef?.get()
            ?: return mapOf("status" to "error", "message" to "No active Activity attached")

        val decorView = activity.window.decorView
        val w = decorView.width.toFloat().coerceAtLeast(1f)
        val h = decorView.height.toFloat().coerceAtLeast(1f)
        val x1 = startXNorm * w
        val y1 = startYNorm * h
        val x2 = endXNorm * w
        val y2 = endYNorm * h

        activity.runOnUiThread {
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                x1, y1, 0
            )
            decorView.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            val stepDelay = durationMs / steps.coerceAtLeast(1)
            val handler = Handler(Looper.getMainLooper())

            for (i in 1..steps) {
                handler.postDelayed({
                    val t = i.toFloat() / steps
                    val curX = x1 + (x2 - x1) * t
                    val curY = y1 + (y2 - y1) * t
                    val moveTime = SystemClock.uptimeMillis()
                    val moveEvent = MotionEvent.obtain(
                        downTime, moveTime, MotionEvent.ACTION_MOVE,
                        curX, curY, 0
                    )
                    decorView.dispatchTouchEvent(moveEvent)
                    moveEvent.recycle()

                    if (i == steps) {
                        val upTime = SystemClock.uptimeMillis()
                        val upEvent = MotionEvent.obtain(
                            downTime, upTime, MotionEvent.ACTION_UP,
                            x2, y2, 0
                        )
                        decorView.dispatchTouchEvent(upEvent)
                        upEvent.recycle()
                    }
                }, i * stepDelay)
            }
        }

        log("McpBridge", "Dispatched drag from ($x1, $y1) to ($x2, $y2)")
        return mapOf(
            "status" to "ok",
            "startX" to x1,
            "startY" to y1,
            "endX" to x2,
            "endY" to y2,
            "durationMs" to durationMs
        )
    }

    /**
     * Serializes complete application UI state into a clean JSON structure.
     */
    fun getUiStateSnapshot(): JSONObject {
        val root = JSONObject()
        try {
            val activity = currentActivityRef?.get()
            root.put("isActivityRunning", activity != null)
            root.put("activityClass", activity?.localClassName ?: "None")

            val workspaceVm = workspaceViewModelRef?.get()
            if (workspaceVm != null) {
                val state = workspaceVm.uiState.value
                val wsJson = JSONObject()
                wsJson.put("active", true)
                wsJson.put("projectId", state.project?.id ?: "")
                wsJson.put("projectName", state.project?.name ?: "")
                wsJson.put("currentTimeSeconds", state.currentTimeSeconds)
                wsJson.put("totalDurationSeconds", state.totalDurationSeconds)
                wsJson.put("isPlaying", state.isPlaying)
                wsJson.put("isAudioMuted", state.isAudioMuted)
                wsJson.put("aspectRatio", state.aspectRatio.name)
                wsJson.put("activeTool", state.activeTool?.name ?: "NONE")
                wsJson.put("trackingMode", state.trackingMode.name)
                wsJson.put("selectedClipId", state.selectedClipId ?: "")
                wsJson.put("canUndo", state.canUndo)
                wsJson.put("canRedo", state.canRedo)
                wsJson.put("showExportDialog", state.showExportDialog)
                wsJson.put("showExportProgress", state.showExportProgressDialog)

                val clipsArray = JSONArray()
                state.clips.forEach { clip ->
                    val cObj = JSONObject()
                    cObj.put("id", clip.id)
                    cObj.put("title", clip.title)
                    cObj.put("inPoint", clip.inPointSeconds)
                    cObj.put("duration", clip.durationSeconds)
                    cObj.put("isImage", clip.isImage)
                    cObj.put("isSelected", clip.isSelected)
                    cObj.put("speedMultiplier", clip.speedMultiplier.toDouble())
                    cObj.put("isOpticalFlowEnabled", clip.isOpticalFlowEnabled)
                    clipsArray.put(cObj)
                }
                wsJson.put("clips", clipsArray)

                val overlaysArray = JSONArray()
                state.overlays.forEach { ov ->
                    val oObj = JSONObject()
                    oObj.put("id", ov.id)
                    oObj.put("type", ov.type.name)
                    oObj.put("label", ov.label)
                    oObj.put("startTime", ov.startTimeSeconds)
                    oObj.put("duration", ov.durationSeconds)
                    overlaysArray.put(oObj)
                }
                wsJson.put("overlays", overlaysArray)
                root.put("workspace", wsJson)
            } else {
                root.put("workspace", JSONObject().put("active", false))
            }

            val mainMenuVm = mainMenuViewModelRef?.get()
            if (mainMenuVm != null) {
                val mmState = mainMenuVm.uiState.value
                val mmJson = JSONObject()
                mmJson.put("projectsCount", mmState.projects.size)
                val projArray = JSONArray()
                mmState.projects.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("id", p.id)
                    pObj.put("name", p.name)
                    pObj.put("duration", p.durationSeconds)
                    projArray.put(pObj)
                }
                mmJson.put("projects", projArray)
                root.put("mainMenu", mmJson)
            }
        } catch (e: Throwable) {
            root.put("status", "error")
            root.put("errorMessage", e.message ?: "Unknown serialization error")
            log("McpBridge", "Error serializing UI state: ${e.message}")
        }

        return root
    }

    /**
     * Executes a direct action on the active ViewModel from an MCP call.
     */
    fun dispatchAction(actionName: String, params: Map<String, Any> = emptyMap()): Map<String, Any> {
        val workspaceVm = workspaceViewModelRef?.get()
        val activity = currentActivityRef?.get()

        if (workspaceVm == null) {
            return mapOf("status" to "error", "message" to "Workspace ViewModel not active. Launch app first.")
        }

        activity?.runOnUiThread {
            when (actionName.lowercase()) {
                "play_pause", "toggle_playback" -> workspaceVm.togglePlayPause()
                "seek" -> {
                    val sec = (params["time"] as? Number)?.toDouble() ?: 0.0
                    workspaceVm.seekTo(sec)
                }
                "step_forward" -> workspaceVm.stepFrame(1.0 / 30.0)
                "step_back" -> workspaceVm.stepFrame(-1.0 / 30.0)
                "undo" -> workspaceVm.undo()
                "redo" -> workspaceVm.redo()
                "split" -> workspaceVm.splitClipAtPlayhead()
                "delete" -> workspaceVm.deleteSelectedClip()
                "duplicate" -> workspaceVm.duplicateSelectedClip()
                "toggle_mute" -> workspaceVm.toggleAudioMute()
                "select_tool" -> {
                    val toolName = params["tool"]?.toString()?.uppercase() ?: "NONE"
                    val toolType = try { ToolType.valueOf(toolName) } catch (_: Exception) { null }
                    if (toolType != null) {
                        workspaceVm.selectTool(toolType)
                    } else {
                        workspaceVm.closeToolInspector()
                    }
                }
                "start_motion_tracking" -> workspaceVm.startMotionTracking()
                "stop_tracking" -> workspaceVm.stopTracking()
                "clear_tracking" -> workspaceVm.clearActiveTracking()
                "close_tool" -> workspaceVm.closeToolInspector()
                "apply_tool" -> workspaceVm.applyCurrentToolToTimeline()
                "show_export" -> workspaceVm.showExportDialog(true)
                "hide_export" -> workspaceVm.showExportDialog(false)
                "navigate_workspace" -> {
                    val projId = params["projectId"]?.toString() ?: "default_proj"
                    navControllerRef?.get()?.navigate(com.aiditor.app.ui.navigation.Screen.Workspace.createRoute(projId))
                }
                "navigate_main_menu" -> {
                    navControllerRef?.get()?.popBackStack()
                }
                else -> log("McpBridge", "Unknown action: $actionName")
            }
        }

        log("McpBridge", "Executed action: $actionName with params $params")
        return mapOf("status" to "ok", "action" to actionName)
    }

    /**
     * Retrieves recent application logs and diagnostic events.
     */
    fun getRecentLogs(maxLines: Int = 50): List<String> {
        val result = mutableListOf<String>()
        val recent = logBuffer.takeLast(maxLines.coerceAtMost(MAX_LOGS))
        result.addAll(recent)
        return result
    }
}
