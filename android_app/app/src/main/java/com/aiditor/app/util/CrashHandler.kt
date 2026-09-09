package com.aiditor.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.aiditor.app.ui.CrashReportActivity
import java.io.File
import java.io.StringWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Global Uncaught Exception & Crash Handler.
 * Intercepts any fatal crash, records the complete stack trace and device diagnostics,
 * and launches CrashReportActivity with a one-tap "Copy Log" button.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "AiditorCrashHandler"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    var lastCrashReport: String = ""
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)

        // Read previous crash report if available
        try {
            val file = File(context.filesDir, "last_crash.txt")
            if (file.exists()) {
                lastCrashReport = file.readText()
            }
        } catch (_: Exception) {}
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val context = appContext
        val report = buildCrashReport(throwable)
        lastCrashReport = report

        try {
            Log.e(TAG, "FATAL CRASH DETECTED:\n$report")
            if (context != null) {
                val file = File(context.filesDir, "last_crash.txt")
                file.writeText(report)

                val intent = Intent(context, CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_log", report)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching crash activity: ${e.message}", e)
        }

        // Terminate dying process cleanly
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }

    fun buildCrashReport(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        return buildString {
            appendLine("==================================================")
            appendLine("AIDITOR CRASH REPORT")
            appendLine("Timestamp: $timeStamp")
            appendLine("==================================================")
            appendLine("Device Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device Model: ${Build.MODEL}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("Available Processors: ${Runtime.getRuntime().availableProcessors()}")
            val maxMemMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val freeMemMb = Runtime.getRuntime().freeMemory() / (1024 * 1024)
            appendLine("App Memory: Free: ${freeMemMb}MB / Max: ${maxMemMb}MB")
            appendLine("==================================================")
            appendLine("EXCEPTION:")
            appendLine("${throwable.javaClass.name}: ${throwable.message}")
            appendLine("==================================================")
            appendLine("COMPLETE STACK TRACE:")
            appendLine(stackTrace)
            appendLine("==================================================")
        }
    }
}
