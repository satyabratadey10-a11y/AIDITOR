package com.aiditor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance, low-memory thumbnail cache for multi-track timeline filmstrip.
 * Uses RGB_565 bitmaps downsampled to tiny 96x54 previews and an LRU cache limited to 6 MB,
 * reducing memory consumption by over 70% compared to standard full-frame decoding.
 */
object LowMemoryThumbnailCache {

    private const val MAX_CACHE_BYTES = 6 * 1024 * 1024 // 6 MB max heap limit

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled && oldValue != newValue) {
                try {
                    oldValue.recycle()
                } catch (_: Exception) {}
            }
        }
    }

    fun getFromCache(videoPath: String, timeSec: Double): Bitmap? {
        val key = cacheKey(videoPath, timeSec)
        return memoryCache.get(key)
    }

    suspend fun getThumbnail(
        context: Context,
        videoPath: String,
        timeSec: Double,
        targetWidth: Int = 96,
        targetHeight: Int = 54
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = cacheKey(videoPath, timeSec)
        val cached = memoryCache.get(key)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        if (videoPath.isBlank()) return@withContext null

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }

            val timeUs = (timeSec * 1_000_000).toLong().coerceAtLeast(0L)

            val rawBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    targetWidth,
                    targetHeight
                )
            } else {
                val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (full != null) {
                    val scaled = Bitmap.createScaledBitmap(full, targetWidth, targetHeight, true)
                    if (scaled != full) full.recycle()
                    scaled
                } else null
            }

            if (rawBitmap != null) {
                // Convert to RGB_565 to save 50% memory if it's ARGB_8888
                val lowMemBitmap = if (rawBitmap.config != Bitmap.Config.RGB_565) {
                    val rgb565 = rawBitmap.copy(Bitmap.Config.RGB_565, false)
                    if (rgb565 != null) {
                        rawBitmap.recycle()
                        rgb565
                    } else {
                        rawBitmap
                    }
                } else {
                    rawBitmap
                }

                memoryCache.put(key, lowMemBitmap)
                return@withContext lowMemBitmap
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever?.release()
            } catch (_: Exception) {}
        }
        return@withContext null
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    private fun cacheKey(videoPath: String, timeSec: Double): String {
        val roundedSec = (timeSec * 2.0).toInt() / 2.0 // 0.5s resolution
        return "$videoPath@$roundedSec"
    }
}
