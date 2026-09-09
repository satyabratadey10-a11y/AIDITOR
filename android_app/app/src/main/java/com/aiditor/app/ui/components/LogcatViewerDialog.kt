package com.aiditor.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aiditor.app.R
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun LogcatViewerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var logsText by remember { mutableStateOf("Loading device and application logs...") }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshLogs() {
        isLoading = true
        coroutineScope.launch {
            val fetchedLogs = withContext(Dispatchers.IO) {
                readAppLogs(context)
            }
            logsText = fetchedLogs
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C30)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOGCAT & DIAGNOSTICS",
                        color = BwWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    BwIconButton(
                        iconRes = R.drawable.ic_close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable log text container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF08080A))
                        .border(1.dp, Color(0xFF222226), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = logsText,
                        color = Color(0xFFE2E2E6),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { refreshLogs() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25252A), contentColor = BwWhite),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("REFRESH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    BwButton(
                        text = "COPY LOG",
                        iconRes = R.drawable.ic_duplicate,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("AIDITOR Logcat", logsText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Logcat copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.3f).height(40.dp)
                    )
                }
            }
        }
    }
}

private fun readAppLogs(context: Context): String {
    val builder = StringBuilder()

    // 1. Check if there was a previous crash recorded
    if (CrashHandler.lastCrashReport.isNotBlank()) {
        builder.appendLine("=== LAST RECORDED FATAL CRASH ===")
        builder.appendLine(CrashHandler.lastCrashReport)
        builder.appendLine("=================================")
        builder.appendLine()
    }

    // 2. Read runtime logcat for current process
    try {
        val pid = android.os.Process.myPid()
        builder.appendLine("=== RUNTIME LOGCAT (PID: $pid) ===")
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val recentLines = mutableListOf<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line!!
            if (l.contains("aiditor", ignoreCase = true) || l.contains("AndroidRuntime") || l.contains("MediaMuxer") || l.contains("MediaCodec") || l.contains("ExoPlayer") || l.contains("FATAL")) {
                recentLines.add(l)
            }
        }
        if (recentLines.isEmpty()) {
            builder.appendLine("No errors or AIDITOR events found in recent buffer.")
        } else {
            recentLines.takeLast(250).forEach { builder.appendLine(it) }
        }
    } catch (e: Exception) {
        builder.appendLine("Error querying logcat: ${e.message}")
    }

    return builder.toString()
}
