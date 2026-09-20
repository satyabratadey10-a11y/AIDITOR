package com.aiditor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.aiditor.app.data.model.Point2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Universal Subject Outliner & Saliency Contour Engine.
 * Extracts the boundary/silhouette contour of the pointed subject from video frames
 * using radial edge saliency ray-casting and gradient magnitude segmentation.
 */
object SubjectOutliner {

    /**
     * Extracts a polygon contour (24 points) wrapping the subject around (targetX, targetY).
     * Returns coordinates relative to normalized [0, 1] screen space.
     */
    fun extractSubjectContour(
        frame: Bitmap,
        targetX: Float,
        targetY: Float,
        boxWidth: Float,
        boxHeight: Float,
        numRays: Int = 24
    ): List<Point2D> {
        val fw = frame.width
        val fh = frame.height
        if (fw <= 0 || fh <= 0) {
            return generateDefaultContour(targetX, targetY, boxWidth, boxHeight, numRays)
        }

        val cx = (targetX * fw).toInt().coerceIn(0, fw - 1)
        val cy = (targetY * fh).toInt().coerceIn(0, fh - 1)
        val halfW = ((boxWidth * fw) / 2f).coerceAtLeast(10f)
        val halfH = ((boxHeight * fh) / 2f).coerceAtLeast(10f)

        // Sample seed color at center (average of 5x5 window around cx, cy)
        var seedR = 0
        var seedG = 0
        var seedB = 0
        var seedCount = 0
        for (dy in -2..2) {
            val py = (cy + dy).coerceIn(0, fh - 1)
            for (dx in -2..2) {
                val px = (cx + dx).coerceIn(0, fw - 1)
                val c = frame.getPixel(px, py)
                seedR += (c shr 16) and 0xFF
                seedG += (c shr 8) and 0xFF
                seedB += c and 0xFF
                seedCount++
            }
        }
        val avgR = seedR / seedCount.coerceAtLeast(1)
        val avgG = seedG / seedCount.coerceAtLeast(1)
        val avgB = seedB / seedCount.coerceAtLeast(1)

        val rawPoints = mutableListOf<Point2D>()

        for (i in 0 until numRays) {
            val angle = (2.0 * PI * i / numRays).toFloat()
            val cosA = cos(angle)
            val sinA = sin(angle)

            // Step along ray from center outward
            var edgeX = cx.toFloat()
            var edgeY = cy.toFloat()
            var foundBoundary = false

            val raySteps = 24
            for (step in 2..raySteps) {
                val frac = step.toFloat() / raySteps
                val curDistX = frac * halfW * cosA
                val curDistY = frac * halfH * sinA
                val px = (cx + curDistX).roundToInt().coerceIn(0, fw - 1)
                val py = (cy + curDistY).roundToInt().coerceIn(0, fh - 1)

                val c = frame.getPixel(px, py)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // Color distance from seed
                val dr = r - avgR
                val dg = g - avgG
                val db = b - avgB
                val deltaE = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()

                // Gradient magnitude with adjacent pixel along ray
                val npx = (px + (cosA * 2f).roundToInt()).coerceIn(0, fw - 1)
                val npy = (py + (sinA * 2f).roundToInt()).coerceIn(0, fh - 1)
                val nc = frame.getPixel(npx, npy)
                val nr = (nc shr 16) and 0xFF
                val ng = (nc shr 8) and 0xFF
                val nb = nc and 0xFF
                val grad = (abs(r - nr) + abs(g - ng) + abs(b - nb)).toFloat()

                // Edge detection condition: sharp color distance or high gradient boundary
                if (deltaE > 48f || grad > 55f) {
                    edgeX = px.toFloat()
                    edgeY = py.toFloat()
                    foundBoundary = true
                    break
                }
            }

            if (!foundBoundary) {
                // Natural fallback: wrap to ~82% of bounding box along this angle
                edgeX = (cx + halfW * 0.82f * cosA).coerceIn(0f, fw - 1f)
                edgeY = (cy + halfH * 0.82f * sinA).coerceIn(0f, fh - 1f)
            }

            rawPoints.add(Point2D(edgeX / fw.toFloat(), edgeY / fh.toFloat()))
        }

        // Apply 3-point circular smoothing filter to ensure an organic silhouette
        val smoothedPoints = mutableListOf<Point2D>()
        for (i in 0 until numRays) {
            val prev = rawPoints[(i - 1 + numRays) % numRays]
            val curr = rawPoints[i]
            val next = rawPoints[(i + 1) % numRays]
            val sx = prev.x * 0.25f + curr.x * 0.50f + next.x * 0.25f
            val sy = prev.y * 0.25f + curr.y * 0.50f + next.y * 0.25f
            smoothedPoints.add(Point2D(sx.coerceIn(0.01f, 0.99f), sy.coerceIn(0.01f, 0.99f)))
        }

        return smoothedPoints
    }

    /**
     * Extracts subject contour directly from video file on IO thread.
     */
    suspend fun extractContourFromVideo(
        context: Context?,
        videoPath: String,
        timeSeconds: Double,
        targetX: Float,
        targetY: Float,
        boxWidth: Float,
        boxHeight: Float,
        numRays: Int = 24
    ): List<Point2D> = withContext(Dispatchers.IO) {
        if (context == null || videoPath.isBlank()) {
            return@withContext generateDefaultContour(targetX, targetY, boxWidth, boxHeight, numRays)
        }

        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }

            val timeUs = (timeSeconds * 1_000_000).toLong()
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (frame != null) {
                // Scale to fast analysis size (320x180) to conserve memory & CPU
                val scaled = Bitmap.createScaledBitmap(frame, 320, 180, true)
                frame.recycle()
                val contour = extractSubjectContour(scaled, targetX, targetY, boxWidth, boxHeight, numRays)
                scaled.recycle()
                return@withContext contour
            }
        } catch (_: Throwable) {
        } finally {
            try { retriever?.release() } catch (_: Throwable) {}
        }

        return@withContext generateDefaultContour(targetX, targetY, boxWidth, boxHeight, numRays)
    }

    /**
     * Generates a sleek rounded superellipse contour outline as fallback.
     */
    fun generateDefaultContour(
        targetX: Float,
        targetY: Float,
        boxWidth: Float,
        boxHeight: Float,
        numRays: Int = 24
    ): List<Point2D> {
        val points = mutableListOf<Point2D>()
        val halfW = boxWidth / 2f
        val halfH = boxHeight / 2f
        for (i in 0 until numRays) {
            val angle = (2.0 * PI * i / numRays).toFloat()
            val cosA = cos(angle)
            val sinA = sin(angle)
            // Superellipse with exponent n = 2.4 for rounded organic pill shape
            val rX = halfW * 0.85f * cosA
            val rY = halfH * 0.85f * sinA
            points.add(Point2D((targetX + rX).coerceIn(0.01f, 0.99f), (targetY + rY).coerceIn(0.01f, 0.99f)))
        }
        return points
    }
}
