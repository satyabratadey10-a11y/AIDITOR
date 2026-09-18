package com.aiditor.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast Receiver interface for triggering AIDITOR MCP commands via Android shell ('am broadcast').
 * Action: com.aiditor.app.MCP_ACTION
 * Extras:
 *   --es action [screenshot|click|drag|play_pause|seek|select_tool|start_motion_tracking|export]
 *   --ef normX [0.0..1.0] --ef normY [0.0..1.0]
 *   --ef startX [0.0..1.0] --ef startY [0.0..1.0] --ef endX [0.0..1.0] --ef endY [0.0..1.0]
 *   --es tool [OPTICAL_FLOW|COLOR_GRADE|SPEED_RAMP|MOTION_TRACKING|ROTOSCOPE]
 *   --ed time [seconds]
 */
class McpCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.aiditor.app.MCP_ACTION") return

        val action = intent.getStringExtra("action") ?: return
        McpBridge.log("McpCommandReceiver", "Received broadcast action: $action")

        when (action.lowercase()) {
            "screenshot" -> {
                McpBridge.takeScreenshot()
            }
            "click", "tap" -> {
                val normX = if (intent.hasExtra("normX")) intent.getFloatExtra("normX", 0.5f) else 0.5f
                val normY = if (intent.hasExtra("normY")) intent.getFloatExtra("normY", 0.5f) else 0.5f
                val pxX = if (intent.hasExtra("x")) intent.getFloatExtra("x", 0f) else null
                val pxY = if (intent.hasExtra("y")) intent.getFloatExtra("y", 0f) else null
                McpBridge.dispatchTap(normX, normY, pxX, pxY)
            }
            "drag" -> {
                val startX = intent.getFloatExtra("startX", 0.2f)
                val startY = intent.getFloatExtra("startY", 0.5f)
                val endX = intent.getFloatExtra("endX", 0.8f)
                val endY = intent.getFloatExtra("endY", 0.5f)
                val duration = intent.getLongExtra("duration", 350L)
                McpBridge.dispatchDrag(startX, startY, endX, endY, duration)
            }
            else -> {
                val params = mutableMapOf<String, Any>()
                val extras = intent.extras
                if (extras != null) {
                    for (key in extras.keySet()) {
                        extras.get(key)?.let { params[key] = it }
                    }
                }
                McpBridge.dispatchAction(action, params)
            }
        }
    }
}
