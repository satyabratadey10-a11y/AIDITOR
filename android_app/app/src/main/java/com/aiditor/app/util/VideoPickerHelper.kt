package com.aiditor.app.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

data class PickedVideoDetails(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val durationSeconds: Double,
    val width: Int,
    val height: Int
)

object VideoPickerHelper {

    fun extractVideoDetails(context: Context, uri: Uri): PickedVideoDetails {
        var name = "Video_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        // 1. Query ContentResolver for Display Name and File Size
        try {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val n = cursor.getString(nameIndex)
                        if (!n.isNullOrBlank()) {
                            name = n
                        }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Extract Duration, Width, Height using MediaMetadataRetriever
        var durationSec = 10.0
        var width = 1920
        var height = 1080

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                val durMs = durStr.toLongOrNull() ?: 10000L
                durationSec = (durMs / 1000.0).coerceAtLeast(0.5)
            }

            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (wStr != null && hStr != null) {
                width = wStr.toIntOrNull() ?: 1920
                height = hStr.toIntOrNull() ?: 1080
            }

            retriever.release()
        } catch (_: Exception) {
        }

        val sizeFormatted = when {
            sizeBytes > 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
            sizeBytes > 1024L * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
            sizeBytes > 1024L -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
            sizeBytes > 0 -> "$sizeBytes B"
            else -> "0.0 MB"
        }

        return PickedVideoDetails(
            uri = uri,
            displayName = name,
            sizeBytes = sizeBytes,
            sizeFormatted = sizeFormatted,
            durationSeconds = durationSec,
            width = width,
            height = height
        )
    }
}
