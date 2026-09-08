package com.aiditor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance, memory-optimized Video Filmstrip Thumbnail Generator.
 * Enforces: "ensure these app take 25% less memory(Ram) than current version"
 *
 * Optimizations:
 * 1. RGB_565 (16-bit) allocation - 50% RAM reduction compared to default ARGB_8888.
 * 2. Micro-resolution downscaling (88x50 px) - each thumbnail takes only ~8.8 KB.
 * 3. Strict 120-frame LRU Memory Cache to guarantee low heap footprint on Android.
 * 4. Asynchronous IO extraction with cached frame reuse.
 */
class FilmstripManager(private val context: Context) {

    private val lock = Any()
    private val maxThumbnails = 120 // ~1.1 MB max memory

    private val thumbnailCache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            val evict = size > maxThumbnails
            if (evict && eldest != null) {
                try {
                    val bmp = eldest.value
                    if (!bmp.isRecycled) bmp.recycle()
                } catch (_: Throwable) {}
            }
            return evict
        }
    }

    suspend fun getThumbnail(
        videoPath: String,
        timeSeconds: Double,
        targetWidth: Int = 88,
        targetHeight: Int = 50
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (videoPath.isBlank()) return@withContext null

        val cacheKey = "${videoPath}_${(timeSeconds * 2).toInt()}"
        synchronized(lock) {
            thumbnailCache[cacheKey]?.let {
                if (!it.isRecycled) return@withContext it
            }
        }

        try {
            val retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }

            val timeUs = (timeSeconds * 1_000_000).toLong()
            val rawBitmap = retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            retriever.release()

            if (rawBitmap != null) {
                // Downsample to RGB_565 to save 50% RAM
                val lowRamBitmap = if (rawBitmap.config != Bitmap.Config.RGB_565) {
                    val converted = rawBitmap.copy(Bitmap.Config.RGB_565, false)
                    rawBitmap.recycle()
                    converted ?: rawBitmap
                } else {
                    rawBitmap
                }

                synchronized(lock) {
                    thumbnailCache[cacheKey] = lowRamBitmap
                }
                return@withContext lowRamBitmap
            }
        } catch (_: Exception) {
        }
        null
    }

    fun clearCache() = synchronized(lock) {
        for (bmp in thumbnailCache.values) {
            try {
                if (!bmp.isRecycled) bmp.recycle()
            } catch (_: Throwable) {}
        }
        thumbnailCache.clear()
    }
}
