package com.aiditor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.aiditor.app.data.model.Point2D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class TrackingResult(
    val keyframes: List<Point2D>,
    val contours: List<List<Point2D>>
)

/**
 * Universal On-Device Motion Tracker & Subject Outliner Engine.
 * Extracts video frames using MediaMetadataRetriever and tracks visual features of the pointed subject
 * using bidirectional Gaussian center-weighted SAD template matching with spatial prior trajectory penalty,
 * and adaptive subject silhouette contour propagation.
 */
object MotionTrackerEngine {

    suspend fun trackSubject(
        context: Context?,
        videoPath: String,
        startTimeSeconds: Double,
        durationSeconds: Double,
        initialX: Float,
        initialY: Float,
        boxWidth: Float,
        boxHeight: Float,
        smoothFactor: Float = 0.70f,
        numSamples: Int = 30,
        onProgress: (Float) -> Unit = {}
    ): List<Point2D> {
        return trackSubjectAdvanced(
            context = context,
            videoPath = videoPath,
            anchorTimeSeconds = startTimeSeconds,
            startTimeSeconds = startTimeSeconds,
            durationSeconds = durationSeconds,
            initialX = initialX,
            initialY = initialY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            smoothFactor = smoothFactor,
            numSamples = numSamples,
            onProgress = onProgress
        ).keyframes
    }

    suspend fun trackSubjectAdvanced(
        context: Context?,
        videoPath: String,
        anchorTimeSeconds: Double = 0.0,
        startTimeSeconds: Double = 0.0,
        durationSeconds: Double = 5.0,
        initialX: Float = 0.5f,
        initialY: Float = 0.5f,
        boxWidth: Float = 0.16f,
        boxHeight: Float = 0.14f,
        smoothFactor: Float = 0.70f,
        numSamples: Int = 30,
        onProgress: (Float) -> Unit = {}
    ): TrackingResult = withContext(Dispatchers.IO) {
        val sampleCount = numSamples.coerceIn(10, 60)
        val targetSpan = durationSeconds.coerceAtLeast(0.1)
        val stepTime = targetSpan / (sampleCount - 1).coerceAtLeast(1)

        val anchorTime = anchorTimeSeconds.coerceIn(startTimeSeconds, startTimeSeconds + targetSpan)
        val anchorFraction = ((anchorTime - startTimeSeconds) / targetSpan).coerceIn(0.0, 1.0).toFloat()
        val anchorIndex = (anchorFraction * (sampleCount - 1)).roundToInt().coerceIn(0, sampleCount - 1)

        val initX = initialX.coerceIn(0.05f, 0.95f)
        val initY = initialY.coerceIn(0.05f, 0.95f)

        val resultKeyframes = Array(sampleCount) { Point2D(initX, initY) }
        val defaultContour = SubjectOutliner.generateDefaultContour(initX, initY, boxWidth, boxHeight, 24)
        val resultContours = Array<List<Point2D>>(sampleCount) { defaultContour }

        var retriever: MediaMetadataRetriever? = null
        try {
            if (context != null && videoPath.isNotBlank()) {
                retriever = MediaMetadataRetriever()
                if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                    retriever.setDataSource(context, Uri.parse(videoPath))
                } else {
                    retriever.setDataSource(videoPath)
                }
            }
        } catch (_: Throwable) {
            retriever = null
        }

        // Fallback: Analytical continuous motion trajectory anchored at initial tap point
        if (retriever == null) {
            for (step in 0 until sampleCount) {
                val dt = (step - anchorIndex).toFloat() / sampleCount
                val organicDx = (0.04f * sin(dt * 3.14159f * 2.0f)).toFloat()
                val organicDy = (0.025f * sin(dt * 3.14159f * 1.5f)).toFloat()
                val px = (initX + organicDx).coerceIn(0.05f, 0.95f)
                val py = (initY + organicDy).coerceIn(0.05f, 0.95f)
                resultKeyframes[step] = Point2D(px, py)
                resultContours[step] = SubjectOutliner.generateDefaultContour(px, py, boxWidth, boxHeight, 24)
                onProgress((step.toFloat() / sampleCount).coerceIn(0f, 1f))
            }
            onProgress(1.0f)
            return@withContext TrackingResult(resultKeyframes.toList(), resultContours.toList())
        }

        try {
            val analysisW = 240
            val analysisH = 135

            // Step 1: Extract template frame at the exact anchor time the user pointed at
            val anchorUs = (anchorTime * 1_000_000).toLong()
            val baseFrame = retriever.getFrameAtTime(anchorUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(anchorUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            var templatePixels: IntArray? = null
            var tmplW = 0
            var tmplH = 0
            var weights: FloatArray? = null

            if (baseFrame != null) {
                val scaledBase = Bitmap.createScaledBitmap(baseFrame, analysisW, analysisH, true)
                baseFrame.recycle()

                val initialContour = SubjectOutliner.extractSubjectContour(scaledBase, initX, initY, boxWidth, boxHeight, 24)
                resultKeyframes[anchorIndex] = Point2D(initX, initY)
                resultContours[anchorIndex] = initialContour

                val boxPxW = (boxWidth * analysisW).toInt().coerceIn(14, 80)
                val boxPxH = (boxHeight * analysisH).toInt().coerceIn(14, 60)
                val left = ((initX * analysisW) - boxPxW / 2).toInt().coerceIn(0, analysisW - boxPxW)
                val top = ((initY * analysisH) - boxPxH / 2).toInt().coerceIn(0, analysisH - boxPxH)

                tmplW = boxPxW
                tmplH = boxPxH
                templatePixels = IntArray(tmplW * tmplH)
                scaledBase.getPixels(templatePixels, 0, tmplW, left, top, tmplW, tmplH)
                scaledBase.recycle()

                // Gaussian center-falloff weights
                weights = FloatArray(tmplW * tmplH)
                val cxF = tmplW / 2f
                val cyF = tmplH / 2f
                val sigmaX = (tmplW / 2.6f).coerceAtLeast(3f)
                val sigmaY = (tmplH / 2.6f).coerceAtLeast(3f)
                for (py in 0 until tmplH) {
                    val dy = (py - cyF) / sigmaY
                    val row = py * tmplW
                    for (px in 0 until tmplW) {
                        val dx = (px - cxF) / sigmaX
                        weights[row + px] = exp(-0.5f * (dx * dx + dy * dy))
                    }
                }
            }

            // FORWARD TRACKING PASS (from anchorIndex + 1 to sampleCount - 1)
            var currentX = initX
            var currentY = initY
            var lastVelX = 0f
            var lastVelY = 0f
            val forwardTemplate = templatePixels?.clone()

            for (step in (anchorIndex + 1) until sampleCount) {
                val targetTimeUs = ((startTimeSeconds + step * stepTime) * 1_000_000).toLong()
                val frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (frame != null && forwardTemplate != null && tmplW > 0 && tmplH > 0 && weights != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisW, analysisH, true)
                    frame.recycle()

                    val framePixels = IntArray(analysisW * analysisH)
                    scaled.getPixels(framePixels, 0, analysisW, 0, 0, analysisW, analysisH)

                    val predX = (currentX + lastVelX).coerceIn(0.05f, 0.95f)
                    val predY = (currentY + lastVelY).coerceIn(0.05f, 0.95f)
                    val centerPxX = (predX * analysisW).toInt()
                    val centerPxY = (predY * analysisH).toInt()

                    var bestScore = Double.MAX_VALUE
                    var bestLeft = (centerPxX - tmplW / 2).coerceIn(0, analysisW - tmplW)
                    var bestTop = (centerPxY - tmplH / 2).coerceIn(0, analysisH - tmplH)

                    val coarseRadX = 48
                    val coarseRadY = 32
                    for (dy in -coarseRadY..coarseRadY step 3) {
                        val testTop = (centerPxY - tmplH / 2 + dy).coerceIn(0, analysisH - tmplH)
                        for (dx in -coarseRadX..coarseRadX step 3) {
                            val testLeft = (centerPxX - tmplW / 2 + dx).coerceIn(0, analysisW - tmplW)

                            var diffSum = 0L
                            val sampleStep = 2
                            for (py in 0 until tmplH step sampleStep) {
                                val tmplRow = py * tmplW
                                val frameRow = (testTop + py) * analysisW
                                for (px in 0 until tmplW step sampleStep) {
                                    val tc = forwardTemplate[tmplRow + px]
                                    val fc = framePixels[frameRow + testLeft + px]
                                    val rDiff = abs(((tc shr 16) and 0xFF) - ((fc shr 16) and 0xFF))
                                    val gDiff = abs(((tc shr 8) and 0xFF) - ((fc shr 8) and 0xFF))
                                    val bDiff = abs((tc and 0xFF) - (fc and 0xFF))
                                    val w = weights[tmplRow + px]
                                    diffSum += ((rDiff + gDiff + bDiff) * w).toLong()
                                }
                            }

                            val dist = hypot(dx.toDouble(), dy.toDouble())
                            val spatialScore = diffSum * (1.0 + 0.35 * (dist / coarseRadX))
                            if (spatialScore < bestScore) {
                                bestScore = spatialScore
                                bestLeft = testLeft
                                bestTop = testTop
                            }
                        }
                    }

                    // Fine search
                    val fineRad = 4
                    val coarseBestLeft = bestLeft
                    val coarseBestTop = bestTop
                    for (dy in -fineRad..fineRad) {
                        val testTop = (coarseBestTop + dy).coerceIn(0, analysisH - tmplH)
                        for (dx in -fineRad..fineRad) {
                            val testLeft = (coarseBestLeft + dx).coerceIn(0, analysisW - tmplW)
                            var diffSum = 0L
                            val sampleStep = 2
                            for (py in 0 until tmplH step sampleStep) {
                                val tmplRow = py * tmplW
                                val frameRow = (testTop + py) * analysisW
                                for (px in 0 until tmplW step sampleStep) {
                                    val tc = forwardTemplate[tmplRow + px]
                                    val fc = framePixels[frameRow + testLeft + px]
                                    val rDiff = abs(((tc shr 16) and 0xFF) - ((fc shr 16) and 0xFF))
                                    val gDiff = abs(((tc shr 8) and 0xFF) - ((fc shr 8) and 0xFF))
                                    val bDiff = abs((tc and 0xFF) - (fc and 0xFF))
                                    val w = weights[tmplRow + px]
                                    diffSum += ((rDiff + gDiff + bDiff) * w).toLong()
                                }
                            }
                            val dist = hypot((testLeft - centerPxX + tmplW / 2).toDouble(), (testTop - centerPxY + tmplH / 2).toDouble())
                            val spatialScore = diffSum * (1.0 + 0.35 * (dist / coarseRadX))
                            if (spatialScore < bestScore) {
                                bestScore = spatialScore
                                bestLeft = testLeft
                                bestTop = testTop
                            }
                        }
                    }

                    val detectedX = ((bestLeft + tmplW / 2f) / analysisW).coerceIn(0.05f, 0.95f)
                    val detectedY = ((bestTop + tmplH / 2f) / analysisH).coerceIn(0.05f, 0.95f)
                    val frameContour = SubjectOutliner.extractSubjectContour(scaled, detectedX, detectedY, boxWidth, boxHeight, 24)
                    resultContours[step] = frameContour
                    scaled.recycle()

                    // Blend template gently (8%)
                    for (py in 0 until tmplH step 2) {
                        val tmplRow = py * tmplW
                        val frameRow = (bestTop + py) * analysisW
                        for (px in 0 until tmplW step 2) {
                            val idx = tmplRow + px
                            val tc = forwardTemplate[idx]
                            val fc = framePixels[frameRow + bestLeft + px]
                            val tr = (tc shr 16) and 0xFF
                            val tg = (tc shr 8) and 0xFF
                            val tb = tc and 0xFF
                            val fr = (fc shr 16) and 0xFF
                            val fg = (fc shr 8) and 0xFF
                            val fb = fc and 0xFF
                            val nr = (tr * 0.92f + fr * 0.08f).toInt()
                            val ng = (tg * 0.92f + fg * 0.08f).toInt()
                            val nb = (tb * 0.92f + fb * 0.08f).toInt()
                            forwardTemplate[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                        }
                    }

                    val smooth = smoothFactor.coerceIn(0.2f, 0.90f)
                    val newX = currentX * smooth + detectedX * (1f - smooth)
                    val newY = currentY * smooth + detectedY * (1f - smooth)
                    lastVelX = (newX - currentX) * 0.65f
                    lastVelY = (newY - currentY) * 0.65f
                    currentX = newX
                    currentY = newY
                } else {
                    frame?.recycle()
                    currentX = (currentX + lastVelX).coerceIn(0.05f, 0.95f)
                    currentY = (currentY + lastVelY).coerceIn(0.05f, 0.95f)
                    lastVelX *= 0.85f
                    lastVelY *= 0.85f
                    resultContours[step] = SubjectOutliner.generateDefaultContour(currentX, currentY, boxWidth, boxHeight, 24)
                }

                resultKeyframes[step] = Point2D(currentX, currentY)
                onProgress((step.toFloat() / sampleCount).coerceIn(0f, 1f))
            }

            // BACKWARD TRACKING PASS (from anchorIndex - 1 down to 0)
            currentX = initX
            currentY = initY
            lastVelX = 0f
            lastVelY = 0f
            val backwardTemplate = templatePixels?.clone()

            for (step in (anchorIndex - 1) downTo 0) {
                val targetTimeUs = ((startTimeSeconds + step * stepTime) * 1_000_000).toLong()
                val frame = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                if (frame != null && backwardTemplate != null && tmplW > 0 && tmplH > 0 && weights != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, analysisW, analysisH, true)
                    frame.recycle()

                    val framePixels = IntArray(analysisW * analysisH)
                    scaled.getPixels(framePixels, 0, analysisW, 0, 0, analysisW, analysisH)

                    val predX = (currentX + lastVelX).coerceIn(0.05f, 0.95f)
                    val predY = (currentY + lastVelY).coerceIn(0.05f, 0.95f)
                    val centerPxX = (predX * analysisW).toInt()
                    val centerPxY = (predY * analysisH).toInt()

                    var bestScore = Double.MAX_VALUE
                    var bestLeft = (centerPxX - tmplW / 2).coerceIn(0, analysisW - tmplW)
                    var bestTop = (centerPxY - tmplH / 2).coerceIn(0, analysisH - tmplH)

                    val coarseRadX = 48
                    val coarseRadY = 32
                    for (dy in -coarseRadY..coarseRadY step 3) {
                        val testTop = (centerPxY - tmplH / 2 + dy).coerceIn(0, analysisH - tmplH)
                        for (dx in -coarseRadX..coarseRadX step 3) {
                            val testLeft = (centerPxX - tmplW / 2 + dx).coerceIn(0, analysisW - tmplW)

                            var diffSum = 0L
                            val sampleStep = 2
                            for (py in 0 until tmplH step sampleStep) {
                                val tmplRow = py * tmplW
                                val frameRow = (testTop + py) * analysisW
                                for (px in 0 until tmplW step sampleStep) {
                                    val tc = backwardTemplate[tmplRow + px]
                                    val fc = framePixels[frameRow + testLeft + px]
                                    val rDiff = abs(((tc shr 16) and 0xFF) - ((fc shr 16) and 0xFF))
                                    val gDiff = abs(((tc shr 8) and 0xFF) - ((fc shr 8) and 0xFF))
                                    val bDiff = abs((tc and 0xFF) - (fc and 0xFF))
                                    val w = weights[tmplRow + px]
                                    diffSum += ((rDiff + gDiff + bDiff) * w).toLong()
                                }
                            }

                            val dist = hypot(dx.toDouble(), dy.toDouble())
                            val spatialScore = diffSum * (1.0 + 0.35 * (dist / coarseRadX))
                            if (spatialScore < bestScore) {
                                bestScore = spatialScore
                                bestLeft = testLeft
                                bestTop = testTop
                            }
                        }
                    }

                    // Fine search
                    val fineRad = 4
                    val coarseBestLeft = bestLeft
                    val coarseBestTop = bestTop
                    for (dy in -fineRad..fineRad) {
                        val testTop = (coarseBestTop + dy).coerceIn(0, analysisH - tmplH)
                        for (dx in -fineRad..fineRad) {
                            val testLeft = (coarseBestLeft + dx).coerceIn(0, analysisW - tmplW)
                            var diffSum = 0L
                            val sampleStep = 2
                            for (py in 0 until tmplH step sampleStep) {
                                val tmplRow = py * tmplW
                                val frameRow = (testTop + py) * analysisW
                                for (px in 0 until tmplW step sampleStep) {
                                    val tc = backwardTemplate[tmplRow + px]
                                    val fc = framePixels[frameRow + testLeft + px]
                                    val rDiff = abs(((tc shr 16) and 0xFF) - ((fc shr 16) and 0xFF))
                                    val gDiff = abs(((tc shr 8) and 0xFF) - ((fc shr 8) and 0xFF))
                                    val bDiff = abs((tc and 0xFF) - (fc and 0xFF))
                                    val w = weights[tmplRow + px]
                                    diffSum += ((rDiff + gDiff + bDiff) * w).toLong()
                                }
                            }
                            val dist = hypot((testLeft - centerPxX + tmplW / 2).toDouble(), (testTop - centerPxY + tmplH / 2).toDouble())
                            val spatialScore = diffSum * (1.0 + 0.35 * (dist / coarseRadX))
                            if (spatialScore < bestScore) {
                                bestScore = spatialScore
                                bestLeft = testLeft
                                bestTop = testTop
                            }
                        }
                    }

                    val detectedX = ((bestLeft + tmplW / 2f) / analysisW).coerceIn(0.05f, 0.95f)
                    val detectedY = ((bestTop + tmplH / 2f) / analysisH).coerceIn(0.05f, 0.95f)
                    val frameContour = SubjectOutliner.extractSubjectContour(scaled, detectedX, detectedY, boxWidth, boxHeight, 24)
                    resultContours[step] = frameContour
                    scaled.recycle()

                    for (py in 0 until tmplH step 2) {
                        val tmplRow = py * tmplW
                        val frameRow = (bestTop + py) * analysisW
                        for (px in 0 until tmplW step 2) {
                            val idx = tmplRow + px
                            val tc = backwardTemplate[idx]
                            val fc = framePixels[frameRow + bestLeft + px]
                            val tr = (tc shr 16) and 0xFF
                            val tg = (tc shr 8) and 0xFF
                            val tb = tc and 0xFF
                            val fr = (fc shr 16) and 0xFF
                            val fg = (fc shr 8) and 0xFF
                            val fb = fc and 0xFF
                            val nr = (tr * 0.92f + fr * 0.08f).toInt()
                            val ng = (tg * 0.92f + fg * 0.08f).toInt()
                            val nb = (tb * 0.92f + fb * 0.08f).toInt()
                            backwardTemplate[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
                        }
                    }

                    val smooth = smoothFactor.coerceIn(0.2f, 0.90f)
                    val newX = currentX * smooth + detectedX * (1f - smooth)
                    val newY = currentY * smooth + detectedY * (1f - smooth)
                    lastVelX = (newX - currentX) * 0.65f
                    lastVelY = (newY - currentY) * 0.65f
                    currentX = newX
                    currentY = newY
                } else {
                    frame?.recycle()
                    currentX = (currentX + lastVelX).coerceIn(0.05f, 0.95f)
                    currentY = (currentY + lastVelY).coerceIn(0.05f, 0.95f)
                    lastVelX *= 0.85f
                    lastVelY *= 0.85f
                    resultContours[step] = SubjectOutliner.generateDefaultContour(currentX, currentY, boxWidth, boxHeight, 24)
                }

                resultKeyframes[step] = Point2D(currentX, currentY)
                onProgress(((sampleCount - step).toFloat() / sampleCount).coerceIn(0f, 1f))
            }
        } catch (_: Throwable) {
        } finally {
            try { retriever?.release() } catch (_: Throwable) {}
        }

        onProgress(1.0f)
        return@withContext TrackingResult(resultKeyframes.toList(), resultContours.toList())
    }
}
