package com.aiditor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
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
 * 3. Strict 12MB LRU Memory Cache to guarantee low heap footprint on Android.
 * 4. Asynchronous IO extraction with cached frame reuse.
 */
class FilmstripManager(private val context: Context) {

    // 12 MB memory cache cap for thumbnails
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 16).coerceIn(4096, 12288) // 4MB - 12MB limit

    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue && !oldValue.isRecycled) {
                oldValue.recycle()
            }
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
        synchronized(thumbnailCache) {
            thumbnailCache.get(cacheKey)?.let {
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

                synchronized(thumbnailCache) {
                    thumbnailCache.put(cacheKey, lowRamBitmap)
                }
                return@withContext lowRamBitmap
            }
        } catch (_: Exception) {
        }
        null
    }

    fun clearCache() {
        synchronized(thumbnailCache) {
            thumbnailCache.evictAll()
        }
    }
}
