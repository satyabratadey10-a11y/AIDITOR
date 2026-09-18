package com.aiditor.app.remote

import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Embedded Lightweight HTTP Automation Server for AIDITOR MCP Bridge.
 * Binds to 127.0.0.1:8765 to service local JSON-RPC / REST automation requests from the MCP server.
 * Uses zero third-party dependencies, strictly standard Java/Kotlin networking.
 */
object McpBridgeServer {

    private const val PORT = 8765
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    fun start() {
        if (serverJob != null && serverJob?.isActive == true) {
            McpBridge.log("McpBridgeServer", "Server already running on port $PORT")
            return
        }

        serverJob = serverScope.launch {
            try {
                val localhost = InetAddress.getByName("127.0.0.1")
                serverSocket = ServerSocket(PORT, 50, localhost)
                McpBridge.log("McpBridgeServer", "True MCP Remote Server started on 127.0.0.1:$PORT")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClientConnection(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                McpBridge.log("McpBridgeServer", "Server startup failed on port $PORT: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverJob?.cancel()
            serverJob = null
            serverSocket?.close()
            serverSocket = null
            McpBridge.log("McpBridgeServer", "True MCP Remote Server stopped")
        } catch (_: Exception) {}
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = if (rawPath.contains("?")) rawPath.substringBefore("?") else rawPath
            val queryString = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""
            val queryParams = parseQueryParams(queryString)

            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val lower = line!!.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(chars, read, contentLength - read)
                    if (count < 0) break
                    read += count
                }
                String(chars, 0, read)
            } else ""

            val jsonBody = try {
                if (body.isNotEmpty()) JSONObject(body) else JSONObject()
            } catch (_: Exception) {
                JSONObject()
            }

            val responseJson = when {
                path == "/health" || path == "/" -> {
                    JSONObject()
                        .put("status", "online")
                        .put("service", "AIDITOR True MCP Bridge")
                        .put("port", PORT)
                        .put("timestamp", System.currentTimeMillis())
                }

                path == "/screenshot" -> {
                    val result = McpBridge.takeScreenshot()
                    JSONObject(result)
                }

                path == "/click" -> {
                    val normX = jsonBody.optDouble("normX", queryParams["normX"]?.toDoubleOrNull() ?: 0.5).toFloat()
                    val normY = jsonBody.optDouble("normY", queryParams["normY"]?.toDoubleOrNull() ?: 0.5).toFloat()
                    val pxX = if (jsonBody.has("x")) jsonBody.optDouble("x").toFloat() else queryParams["x"]?.toFloatOrNull()
                    val pxY = if (jsonBody.has("y")) jsonBody.optDouble("y").toFloat() else queryParams["y"]?.toFloatOrNull()

                    val res = McpBridge.dispatchTap(normX, normY, pxX, pxY)
                    JSONObject(res)
                }

                path == "/drag" -> {
                    val startX = jsonBody.optDouble("startX", queryParams["startX"]?.toDoubleOrNull() ?: 0.2).toFloat()
                    val startY = jsonBody.optDouble("startY", queryParams["startY"]?.toDoubleOrNull() ?: 0.5).toFloat()
                    val endX = jsonBody.optDouble("endX", queryParams["endX"]?.toDoubleOrNull() ?: 0.8).toFloat()
                    val endY = jsonBody.optDouble("endY", queryParams["endY"]?.toDoubleOrNull() ?: 0.5).toFloat()
                    val durationMs = jsonBody.optLong("durationMs", queryParams["durationMs"]?.toLongOrNull() ?: 350L)

                    val res = McpBridge.dispatchDrag(startX, startY, endX, endY, durationMs)
                    JSONObject(res)
                }

                path == "/ui" || path == "/ui_state" -> {
                    McpBridge.getUiStateSnapshot()
                }

                path == "/action" -> {
                    val actionName = jsonBody.optString("action", queryParams["action"] ?: "")
                    val params = mutableMapOf<String, Any>()
                    jsonBody.keys().forEach { k ->
                        params[k] = jsonBody.get(k)
                    }
                    queryParams.forEach { (k, v) ->
                        params[k] = v
                    }
                    val res = McpBridge.dispatchAction(actionName, params)
                    JSONObject(res)
                }

                path == "/logs" -> {
                    val linesCount = queryParams["lines"]?.toIntOrNull() ?: 50
                    val logs = McpBridge.getRecentLogs(linesCount)
                    val array = org.json.JSONArray(logs)
                    JSONObject().put("status", "ok").put("logs", array)
                }

                else -> {
                    JSONObject().put("status", "error").put("message", "Unknown endpoint: $path")
                }
            }

            sendHttpResponse(output, 200, responseJson.toString())
        } catch (e: Exception) {
            McpBridge.log("McpBridgeServer", "Error handling client request: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                result[key] = value
            }
        }
        return result
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, jsonPayload: String) {
        val payloadBytes = jsonPayload.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${payloadBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(payloadBytes)
        output.flush()
    }
}
