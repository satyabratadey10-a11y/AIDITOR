package com.aiditor.app.ui.screens.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.net.Uri
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aiditor.app.R
import com.aiditor.app.data.model.ActiveTrackingMode
import com.aiditor.app.data.model.AspectRatioMode
import com.aiditor.app.data.model.CurveControlPoint
import com.aiditor.app.data.model.MiddleParameters
import com.aiditor.app.data.model.OverlayType
import com.aiditor.app.data.model.TimelineClip
import com.aiditor.app.data.model.TimelineOverlay
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.LowMemoryExoPlayerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * High-performance, Low-Memory Video Preview Section.
 * Configured with TextureView for real-time hardware-accelerated ColorFilter & LUT grading.
 * Features dynamic Bézier speed ramp interpolation during playback,
 * and HUD overlays matching reference images:
 * 1. Motion Tracking: Skull & Ok stickers with white bounding box and green reticle.
 * 2. Motion Stabilization: Dashed white circle with green feature tracking points.
 * 3. Face Tracking: Green corner brackets [ ] with dashed circle and crosshair.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewSection(
    currentTimeSeconds: Double,
    totalDurationSeconds: Double,
    isPlaying: Boolean,
    isAudioMuted: Boolean,
    aspectRatio: AspectRatioMode,
    trackingMode: ActiveTrackingMode,
    activeTool: ToolType?,
    middleParams: MiddleParameters = MiddleParameters.OpticalFlow(),
    onPlayPauseToggle: () -> Unit,
    onPlaybackEnded: () -> Unit = {},
    onTimeUpdate: (Double) -> Unit = {},
    onUpdateTrackingTarget: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    videoPath: String? = null,
    clips: List<TimelineClip> = emptyList(),
    overlays: List<TimelineOverlay> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)

    // Active Clip determination based on currentTimeSeconds across the entire timeline
    val activeClip = remember(clips, currentTimeSeconds) {
        clips.firstOrNull { clip ->
            val start = clip.inPointSeconds
            val end = clip.inPointSeconds + clip.durationSeconds
            currentTimeSeconds >= start && currentTimeSeconds < end
        }
    }

    // Blank space detection: If clips exist on timeline, but current playhead is in an empty gap or before/after clips
    val isBlankSpace = clips.isNotEmpty() && activeClip == null

    // Determine effective media source path
    val effectiveSourcePath = when {
        activeClip != null -> {
            if (activeClip.isOpticalFlowEnabled && !activeClip.opticalFlowCachedUri.isNullOrBlank()) {
                activeClip.opticalFlowCachedUri
            } else {
                activeClip.sourcePath
            }
        }
        clips.isEmpty() -> videoPath
        else -> null
    }

    val isImage = remember(activeClip, effectiveSourcePath) {
        if (activeClip != null && activeClip.isImage) {
            true
        } else {
            val p = effectiveSourcePath?.lowercase() ?: ""
            p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp") ||
            (p.startsWith("content://") && (p.contains("image") || p.contains("media/external/images")))
        }
    }

    var staticImageBitmap by remember(effectiveSourcePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(effectiveSourcePath, isImage) {
        if (isImage && !effectiveSourcePath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val bm = if (effectiveSourcePath.startsWith("content://")) {
                        context.contentResolver.openInputStream(Uri.parse(effectiveSourcePath))?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } else {
                        BitmapFactory.decodeFile(effectiveSourcePath)
                    }
                    staticImageBitmap = bm
                } catch (_: Exception) {
                    staticImageBitmap = null
                }
            }
        } else {
            staticImageBitmap = null
        }
    }

    val exoPlayer = remember {
        try {
            LowMemoryExoPlayerHelper.createLowMemoryPlayer(context).apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (isAudioMuted) 0f else 1f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            currentOnPlaybackEnded()
                        }
                    }
                })
            }
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    // Dynamic Media Switching when entering a different video clip or blank space
    var currentLoadedPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(effectiveSourcePath, isImage, isBlankSpace) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (!effectiveSourcePath.isNullOrBlank() && !isImage && !isBlankSpace) {
            if (currentLoadedPath != effectiveSourcePath) {
                currentLoadedPath = effectiveSourcePath
                try {
                    val mediaItem = LowMemoryExoPlayerHelper.buildMediaItem(effectiveSourcePath)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                } catch (_: Exception) {}
            }
        } else {
            currentLoadedPath = null
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {}
        }
    }

    // Audio Mute Synchronization: Muted if user muted, or blank space, or image clip
    LaunchedEffect(isAudioMuted, isBlankSpace, isImage) {
        exoPlayer?.volume = if (isAudioMuted || isBlankSpace || isImage) 0f else 1f
    }

    // Local Clip Time Synchronization:
    val clipLocalSeconds = remember(currentTimeSeconds, activeClip) {
        if (activeClip != null) {
            ((currentTimeSeconds - activeClip.inPointSeconds).coerceAtLeast(0.0) * activeClip.speedMultiplier)
        } else {
            currentTimeSeconds
        }
    }

    // Master Clock Synchronization (Driven by WorkspaceViewModel master clock)
    LaunchedEffect(isPlaying, isBlankSpace, isImage, effectiveSourcePath) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (isPlaying && !isBlankSpace && !isImage && !effectiveSourcePath.isNullOrBlank()) {
            val targetMs = (clipLocalSeconds * 1000).toLong()
            if (kotlin.math.abs(player.currentPosition - targetMs) > 250) {
                player.seekTo(targetMs)
            }
            val clipSpeed = (activeClip?.speedMultiplier?.toFloat() ?: 1.0f).coerceIn(0.1f, 8.0f)
            player.setPlaybackSpeed(clipSpeed)
            player.play()
        } else {
            player.pause()
        }
    }

    // Position sync while paused or seeking
    LaunchedEffect(clipLocalSeconds) {
        if (!isPlaying && !isBlankSpace && !isImage && !effectiveSourcePath.isNullOrBlank()) {
            exoPlayer?.let { player ->
                val targetMs = (clipLocalSeconds * 1000).toLong()
                if (kotlin.math.abs(player.currentPosition - targetMs) > 100) {
                    player.seekTo(targetMs)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Container with selected aspect ratio
        val ratioModifier = when (aspectRatio) {
            AspectRatioMode.RATIO_1_1 -> Modifier.aspectRatio(1.0f)
            AspectRatioMode.RATIO_9_16 -> Modifier.aspectRatio(9f / 16f)
            AspectRatioMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f)
            AspectRatioMode.RATIO_4_5 -> Modifier.aspectRatio(4f / 5f)
            AspectRatioMode.ORIGINAL -> Modifier.aspectRatio(16f / 9f)
        }

        val isTrackingActive = activeTool == ToolType.MOTION_TRACKING ||
            trackingMode == ActiveTrackingMode.MOTION_TRACKING ||
            trackingMode == ActiveTrackingMode.MOTION_STABILIZATION
        val currentIsTrackingActive by rememberUpdatedState(isTrackingActive)
        val currentMiddleParams by rememberUpdatedState(middleParams)
        val currentOnUpdateTrackingTarget by rememberUpdatedState(onUpdateTrackingTarget)
        val currentOnPlayPauseToggle by rememberUpdatedState(onPlayPauseToggle)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(ratioModifier)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(
                    width = if (trackingMode != ActiveTrackingMode.NONE || isTrackingActive) 1.5.dp else 1.dp,
                    color = if (trackingMode != ActiveTrackingMode.NONE || isTrackingActive) Color(0xFF2E7D32) else BwCardStroke,
                    shape = RoundedCornerShape(8.dp)
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        var isTransforming = false
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()

                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) {
                                if (!isTransforming) {
                                    if (currentIsTrackingActive && w > 0 && h > 0) {
                                        val motion = currentMiddleParams as? MiddleParameters.MotionTracking
                                        val curBoxW = motion?.boxWidth ?: 0.16f
                                        val curBoxH = motion?.boxHeight ?: 0.14f
                                        val newX = (startPos.x / w).coerceIn(0.05f, 0.95f)
                                        val newY = (startPos.y / h).coerceIn(0.05f, 0.95f)
                                        currentOnUpdateTrackingTarget(newX, newY, curBoxW, curBoxH)
                                    } else {
                                        currentOnPlayPauseToggle()
                                    }
                                }
                                break
                            }

                            if (currentIsTrackingActive && w > 0 && h > 0) {
                                if (activePointers.size >= 2) {
                                    isTransforming = true
                                    val p1 = activePointers[0].position
                                    val p2 = activePointers[1].position
                                    val prev1 = activePointers[0].previousPosition
                                    val prev2 = activePointers[1].previousPosition
                                    val currentDist = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)
                                    val prevDist = kotlin.math.hypot(prev1.x - prev2.x, prev1.y - prev2.y)
                                    if (prevDist > 0f) {
                                        val zoom = currentDist / prevDist
                                        val motion = currentMiddleParams as? MiddleParameters.MotionTracking
                                        val curX = motion?.targetX ?: 0.5f
                                        val curY = motion?.targetY ?: 0.5f
                                        val curW = motion?.boxWidth ?: 0.16f
                                        val curH = motion?.boxHeight ?: 0.14f
                                        val newW = (curW * zoom).coerceIn(0.04f, 0.8f)
                                        val newH = (curH * zoom).coerceIn(0.04f, 0.8f)
                                        currentOnUpdateTrackingTarget(curX, curY, newW, newH)
                                    }
                                    activePointers.forEach { it.consume() }
                                } else if (activePointers.size == 1) {
                                    val change = activePointers[0]
                                    val dragDelta = change.position - change.previousPosition
                                    val totalMove = kotlin.math.hypot(change.position.x - startPos.x, change.position.y - startPos.y)
                                    if (!isTransforming && totalMove > 6f) {
                                        isTransforming = true
                                    }
                                    if (isTransforming) {
                                        change.consume()
                                        val motion = currentMiddleParams as? MiddleParameters.MotionTracking
                                        val curX = motion?.targetX ?: 0.5f
                                        val curY = motion?.targetY ?: 0.5f
                                        val curW = motion?.boxWidth ?: 0.16f
                                        val curH = motion?.boxHeight ?: 0.14f
                                        val newX = (curX + dragDelta.x / w).coerceIn(0.05f, 0.95f)
                                        val newY = (curY + dragDelta.y / h).coerceIn(0.05f, 0.95f)
                                        currentOnUpdateTrackingTarget(newX, newY, curW, curH)
                                    }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // 0. Blank Space (Empty gap between clips): Pure Black Screen
            if (isBlankSpace) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BLANK GAP",
                        color = Color(0xFF333333),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (staticImageBitmap != null) {
                val bm = staticImageBitmap!!
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val cm = if (middleParams is MiddleParameters.ColorGrade) buildColorMatrix(middleParams) else null
                    val paint = Paint().apply {
                        if (cm != null) {
                            colorFilter = ColorMatrixColorFilter(cm)
                        }
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    val dstRect = android.graphics.Rect(0, 0, size.width.toInt(), size.height.toInt())
                    val srcRect = android.graphics.Rect(0, 0, bm.width, bm.height)
                    drawContext.canvas.nativeCanvas.drawBitmap(bm, srcRect, dstRect, paint)
                }
            } else if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView
                        view.player = exoPlayer
                        view.useController = false
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        view
                    },
                    update = { view ->
                        view.player = exoPlayer
                        val texture = view.videoSurfaceView as? TextureView
                        if (texture != null && middleParams is MiddleParameters.ColorGrade) {
                            val cm = buildColorMatrix(middleParams)
                            val paint = Paint().apply {
                                colorFilter = ColorMatrixColorFilter(cm)
                            }
                            texture.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                        } else if (texture != null) {
                            texture.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            // Real-time Compose Color Grade fallback / tint overlay
                            if (middleParams is MiddleParameters.ColorGrade) {
                                val b = middleParams.brightness
                                if (b > 0.05f) {
                                    drawRect(
                                        Color.White.copy(alpha = b.coerceIn(0f, 0.7f)),
                                        blendMode = BlendMode.Screen
                                    )
                                } else if (b < -0.05f) {
                                    drawRect(
                                        Color.Black.copy(alpha = (-b).coerceIn(0f, 0.7f)),
                                        blendMode = BlendMode.Darken
                                    )
                                }
                                val c = middleParams.contrast
                                if (c > 1.05f) {
                                    drawRect(
                                        Color.White.copy(alpha = ((c - 1f) * 0.35f).coerceIn(0f, 0.5f)),
                                        blendMode = BlendMode.Overlay
                                    )
                                }
                                if (middleParams.saturation <= 0.05f || middleParams.filterPreset == "bw_cinema") {
                                    drawRect(Color.Black, blendMode = BlendMode.Saturation)
                                }
                            }
                        }
                )
            } else {
                // Procedural video background fallback
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF141416))
                    val step = 40f
                    var x = 0f
                    while (x < size.width) {
                        drawLine(Color(0xFF1C1C20), Offset(x, 0f), Offset(x, size.height), 1f)
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color(0xFF1C1C20), Offset(0f, y), Offset(size.width, y), 1f)
                        y += step
                    }
                }
            }

            // Real-Time Overlays matching Reference Images 1, 2, and 3
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                when (trackingMode) {
                    ActiveTrackingMode.FACE_TRACKING -> {
                        val cx = w * 0.5f
                        val cy = h * 0.46f
                        val boxW = w * 0.48f
                        val boxH = h * 0.38f
                        val cornerLen = 30f

                        val left = cx - boxW / 2
                        val right = cx + boxW / 2
                        val top = cy - boxH / 2
                        val bottom = cy + boxH / 2

                        val greenColor = Color(0xFF00E676)
                        val strokeW = 5f

                        // Top-left corner
                        drawLine(greenColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                        drawLine(greenColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)

                        // Top-right corner
                        drawLine(greenColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
                        drawLine(greenColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)

                        // Bottom-left corner
                        drawLine(greenColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
                        drawLine(greenColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)

                        // Bottom-right corner
                        drawLine(greenColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW)
                        drawLine(greenColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW)

                        val radius = boxW * 0.32f
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = 4f
                            pathEffect = DashPathEffect(floatArrayOf(15f, 12f), 0f)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(cx, cy, radius, paint)

                        drawLine(Color.White.copy(alpha = 0.8f), Offset(cx - 15f, cy), Offset(cx + 15f, cy), 2f)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(cx, cy - 15f), Offset(cx, cy + 15f), 2f)
                    }

                    ActiveTrackingMode.MOTION_TRACKING, ActiveTrackingMode.MOTION_STABILIZATION -> {
                        val isStabilize = (trackingMode == ActiveTrackingMode.MOTION_STABILIZATION)
                        val motionParams = middleParams as? MiddleParameters.MotionTracking
                        val trackingOverlay = overlays.find { it.type == OverlayType.TRACKING_EFFECT || it.type == OverlayType.STABILIZATION_EFFECT }
                        val trackStart = trackingOverlay?.startTimeSeconds ?: 0.0
                        val trackDur = (trackingOverlay?.durationSeconds ?: totalDurationSeconds).coerceAtLeast(0.1)
                        val normTime = ((currentTimeSeconds - trackStart) / trackDur).coerceIn(0.0, 1.0).toFloat()
                        val kfs = if (!motionParams?.trackingKeyframes.isNullOrEmpty()) {
                            motionParams!!.trackingKeyframes
                        } else if (!trackingOverlay?.trackingKeyframes.isNullOrEmpty()) {
                            trackingOverlay!!.trackingKeyframes
                        } else {
                            emptyList()
                        }
                        val initialTargetX = motionParams?.targetX ?: trackingOverlay?.targetX ?: 0.5f
                        val initialTargetY = motionParams?.targetY ?: trackingOverlay?.targetY ?: 0.55f
                        val boxWParam = motionParams?.boxWidth ?: trackingOverlay?.boxWidth ?: 0.16f
                        val boxHParam = motionParams?.boxHeight ?: trackingOverlay?.boxHeight ?: 0.14f

                        val (curX, curY) = if (kfs.isNotEmpty()) {
                            val idxF = normTime * (kfs.size - 1)
                            val idx0 = idxF.toInt().coerceIn(0, kfs.size - 1)
                            val idx1 = (idx0 + 1).coerceAtMost(kfs.size - 1)
                            val frac = idxF - idx0
                            val p0 = kfs[idx0]
                            val p1 = kfs[idx1]
                            Pair(p0.x + (p1.x - p0.x) * frac, p0.y + (p1.y - p0.y) * frac)
                        } else {
                            Pair(initialTargetX, initialTargetY)
                        }

                        val cx = w * curX
                        val cy = h * curY
                        val bw = (w * boxWParam).coerceAtLeast(40f)
                        val bh = (h * boxHParam).coerceAtLeast(40f)

                        val left = cx - bw / 2
                        val right = cx + bw / 2
                        val top = cy - bh / 2
                        val bottom = cy + bh / 2
                        val cornerLen = (bw * 0.25f).coerceIn(12f, 30f)
                        val themeColor = if (isStabilize) Color(0xFFFFD54F) else Color(0xFF00E676)
                        val themeHex = if (isStabilize) "#FFD54F" else "#00E676"
                        val strokeW = 4f

                        // Corner Reticle Brackets [ ]
                        drawLine(themeColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                        drawLine(themeColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
                        drawLine(themeColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
                        drawLine(themeColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)
                        drawLine(themeColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
                        drawLine(themeColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)
                        drawLine(themeColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW)
                        drawLine(themeColor, Offset(right, bottom), Offset(right, bottom + cornerLen), strokeW)

                        // 4 Interactive Corner Resize Handle Dots
                        val handleRadius = 6f
                        drawCircle(color = Color.White, radius = handleRadius, center = Offset(left, top))
                        drawCircle(color = themeColor, radius = handleRadius, center = Offset(left, top), style = Stroke(2f))
                        drawCircle(color = Color.White, radius = handleRadius, center = Offset(right, top))
                        drawCircle(color = themeColor, radius = handleRadius, center = Offset(right, top), style = Stroke(2f))
                        drawCircle(color = Color.White, radius = handleRadius, center = Offset(left, bottom))
                        drawCircle(color = themeColor, radius = handleRadius, center = Offset(left, bottom), style = Stroke(2f))
                        drawCircle(color = Color.White, radius = handleRadius, center = Offset(right, bottom))
                        drawCircle(color = themeColor, radius = handleRadius, center = Offset(right, bottom), style = Stroke(2f))

                        // Center Crosshair
                        val inner = 16f
                        drawLine(themeColor, Offset(cx - inner, cy), Offset(cx + inner, cy), 2.5f)
                        drawLine(themeColor, Offset(cx, cy - inner), Offset(cx, cy + inner), 2.5f)
                        drawCircle(color = Color.White, radius = 3.5f, center = Offset(cx, cy))

                        // Radar sweep if tracking is running
                        if (motionParams != null && motionParams.isTrackingRunning) {
                            val progress = (motionParams.trackingProgress * 100).toInt()
                            val sweepFrac = ((System.currentTimeMillis() % 1000) / 1000f)
                            val sweepY = top + bh * sweepFrac
                            drawLine(
                                color = themeColor,
                                start = Offset(left, sweepY),
                                end = Offset(right, sweepY),
                                strokeWidth = 3f
                            )
                            val scanPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor(themeHex)
                                textSize = 18f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                isAntiAlias = true
                            }
                            drawContext.canvas.nativeCanvas.drawText("SCANNING: $progress%", left, top - 15f, scanPaint)
                        }

                        // HUD Callout Leader Line & Title
                        val calloutTitle = if (isStabilize) "STABILIZE LOCK" else (motionParams?.hudTitle ?: "TARGET LOCKED")
                        val calloutSub = if (motionParams?.isTrackingDone == true) {
                            if (isStabilize) "STABILIZATION ACTIVE • 60 FPS" else "TRACKING ACTIVE • 60 FPS"
                        } else {
                            if (isStabilize) "DRAG TO SUBJECT TO STABILIZE" else (motionParams?.hudSubtitle ?: "DRAG TO SUBJECT")
                        }
                        val p1 = Offset(right, top)
                        val p2 = Offset(right + 20f, top - 20f)
                        val p3 = Offset(right + 85f, top - 20f)
                        drawLine(themeColor, p1, p2, 2f)
                        drawLine(themeColor, p2, p3, 2f)

                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 20f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        val subPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor(themeHex)
                            textSize = 15f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(calloutTitle, right + 24f, top - 25f, textPaint)
                        drawContext.canvas.nativeCanvas.drawText(calloutSub, right + 24f, top - 6f, subPaint)

                        // Sizing hint
                        val hint = String.format(Locale.US, "%.0f%% x %.0f%% • PINCH / DRAG", (bw / w) * 100f, (bh / h) * 100f)
                        drawContext.canvas.nativeCanvas.drawText(hint, left, bottom + 18f, subPaint)
                    }

                    ActiveTrackingMode.NONE -> {
                        if (activeTool == ToolType.MOTION_TRACKING && middleParams is MiddleParameters.MotionTracking) {
                            val trackingOverlay = overlays.find { it.type == OverlayType.TRACKING_EFFECT || it.type == OverlayType.STABILIZATION_EFFECT }
                            val trackStart = trackingOverlay?.startTimeSeconds ?: 0.0
                            val trackDur = (trackingOverlay?.durationSeconds ?: totalDurationSeconds).coerceAtLeast(0.1)
                            val normTime = ((currentTimeSeconds - trackStart) / trackDur).coerceIn(0.0, 1.0).toFloat()
                            val kfs = middleParams.trackingKeyframes
                            val (curX, curY) = if (middleParams.isTrackingDone && kfs.isNotEmpty()) {
                                val idxF = normTime * (kfs.size - 1)
                                val idx0 = idxF.toInt().coerceIn(0, kfs.size - 1)
                                val idx1 = (idx0 + 1).coerceAtMost(kfs.size - 1)
                                val frac = idxF - idx0
                                val p0 = kfs[idx0]
                                val p1 = kfs[idx1]
                                Pair(p0.x + (p1.x - p0.x) * frac, p0.y + (p1.y - p0.y) * frac)
                            } else {
                                Pair(middleParams.targetX, middleParams.targetY)
                            }

                            val cx = w * curX
                            val cy = h * curY
                            val bw = (w * middleParams.boxWidth).coerceAtLeast(40f)
                            val bh = (h * middleParams.boxHeight).coerceAtLeast(40f)
                            val left = cx - bw / 2
                            val right = cx + bw / 2
                            val top = cy - bh / 2
                            val bottom = cy + bh / 2
                            val greenColor = Color(0xFF00E676)
                            val cornerLen = (bw * 0.25f).coerceIn(12f, 30f)
                            val strokeW = 4f

                            drawLine(greenColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                            drawLine(greenColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
                            drawLine(greenColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
                            drawLine(greenColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)
                            drawLine(greenColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
                            drawLine(greenColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)
                            drawLine(greenColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW)
                            drawLine(greenColor, Offset(right, bottom), Offset(right, bottom + cornerLen), strokeW)

                            // 4 Corner resize handle dots
                            val handleRadius = 6f
                            drawCircle(color = Color.White, radius = handleRadius, center = Offset(left, top))
                            drawCircle(color = greenColor, radius = handleRadius, center = Offset(left, top), style = Stroke(2f))
                            drawCircle(color = Color.White, radius = handleRadius, center = Offset(right, top))
                            drawCircle(color = greenColor, radius = handleRadius, center = Offset(right, top), style = Stroke(2f))
                            drawCircle(color = Color.White, radius = handleRadius, center = Offset(left, bottom))
                            drawCircle(color = greenColor, radius = handleRadius, center = Offset(left, bottom), style = Stroke(2f))
                            drawCircle(color = Color.White, radius = handleRadius, center = Offset(right, bottom))
                            drawCircle(color = greenColor, radius = handleRadius, center = Offset(right, bottom), style = Stroke(2f))

                            // Center crosshair
                            val inner = 16f
                            drawLine(greenColor, Offset(cx - inner, cy), Offset(cx + inner, cy), 2.5f)
                            drawLine(greenColor, Offset(cx, cy - inner), Offset(cx, cy + inner), 2.5f)
                            drawCircle(color = Color.White, radius = 3.5f, center = Offset(cx, cy))

                            if (middleParams.isTrackingRunning) {
                                val progress = (middleParams.trackingProgress * 100).toInt()
                                val sweepFrac = ((System.currentTimeMillis() % 1000) / 1000f)
                                val sweepY = top + bh * sweepFrac
                                drawLine(
                                    color = Color(0xFF00E676),
                                    start = Offset(left, sweepY),
                                    end = Offset(right, sweepY),
                                    strokeWidth = 3f
                                )
                                val scanPaint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#00E676")
                                    textSize = 18f
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                                    isAntiAlias = true
                                }
                                drawContext.canvas.nativeCanvas.drawText("SCANNING: $progress%", left, top - 15f, scanPaint)
                            }

                            val subPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#00E676")
                                textSize = 15f
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
                                isAntiAlias = true
                            }
                            val hint = String.format(Locale.US, "%.0f%% x %.0f%% • PINCH / DRAG", (bw / w) * 100f, (bh / h) * 100f)
                            drawContext.canvas.nativeCanvas.drawText(hint, left, bottom + 18f, subPaint)
                        }
                    }
                }

                // Real-time Rotoscope Neon Glow Preview Overlay
                if (activeTool == ToolType.ROTOSCOPE || (middleParams is MiddleParameters.Rotoscope && !middleParams.cachedMaskUri.isNullOrBlank())) {
                    val roto = middleParams as? MiddleParameters.Rotoscope ?: MiddleParameters.Rotoscope()
                    val neonColor = try {
                        Color(android.graphics.Color.parseColor(roto.neonColor))
                    } catch (e: Exception) {
                        Color(0xFF00F0FF)
                    }
                    val cx = w * 0.5f
                    val cy = h * 0.5f
                    val rx = w * 0.22f
                    val ry = h * 0.30f
                    val baseW = roto.outlineWidth.coerceIn(1f, 12f)
                    val glow = roto.glowIntensity.coerceIn(0.5f, 3.0f)

                    // Outer Diffuse Bloom
                    drawOval(
                        color = neonColor.copy(alpha = 0.22f),
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2, ry * 2),
                        style = Stroke(width = baseW * 3.5f * glow)
                    )
                    // Sharp Neon Stroke
                    drawOval(
                        color = neonColor,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2, ry * 2),
                        style = Stroke(width = baseW)
                    )
                    // Core White Spine
                    drawOval(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2, ry * 2),
                        style = Stroke(width = (baseW * 0.4f).coerceAtLeast(1f))
                    )
                }
            }

            // Play / Pause central indicator (only shown when paused)
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                        .border(1.5.dp, BwWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "Play",
                        tint = BwWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Resolution Tag Overlay (Top-Left)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1080P • 60 FPS",
                    color = BwWhite,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (middleParams is MiddleParameters.OpticalFlow && middleParams.isEnabled) {
                    Text(
                        text = " • ⚡ FLOW ${middleParams.targetFps} FPS",
                        color = Color(0xFF00E676),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (middleParams is MiddleParameters.ColorGrade && middleParams.filterPreset != "original") {
                    Text(
                        text = " • ${middleParams.filterPreset.uppercase().replace("_", " ")}",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (trackingMode != ActiveTrackingMode.NONE) {
                    Text(
                        text = " • ${trackingMode.name.replace("_", " ")}",
                        color = Color(0xFF00E676),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (middleParams is MiddleParameters.Rotoscope && (!middleParams.cachedMaskUri.isNullOrBlank() || activeTool == ToolType.ROTOSCOPE)) {
                    Text(
                        text = " • ✂ ROTO ${middleParams.preset.uppercase().replace("_", " ")}",
                        color = Color(0xFF00F0FF),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (middleParams is MiddleParameters.MotionTracking && activeTool == ToolType.MOTION_TRACKING) {
                    Text(
                        text = " • 🎯 ${middleParams.trackingMode.uppercase().replace("_", " ")}",
                        color = Color(0xFF39FF14),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Builds high-precision Android ColorMatrix for live GPU filtering on TextureView.
 */
fun buildColorMatrix(params: MiddleParameters.ColorGrade): ColorMatrix {
    val cm = ColorMatrix()
    cm.setSaturation(params.saturation)

    val scale = params.contrast
    val translate = (0.5f * (1f - scale) + params.brightness) * 255f
    val contrastMatrix = ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))
    cm.postConcat(contrastMatrix)

    when (params.filterPreset) {
        "bw_cinema" -> {
            val bw = ColorMatrix()
            bw.setSaturation(0f)
            cm.postConcat(bw)
        }
        "cyberpunk_cool" -> {
            val cool = ColorMatrix(floatArrayOf(
                0.85f, 0f, 0f, 0f, -5f,
                0f, 1.05f, 0f, 0f, 10f,
                0f, 0f, 1.35f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(cool)
        }
        "warm_gold" -> {
            val warm = ColorMatrix(floatArrayOf(
                1.25f, 0f, 0f, 0f, 20f,
                0f, 1.05f, 0f, 0f, 10f,
                0f, 0f, 0.80f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(warm)
        }
        "vintage_90s" -> {
            val vintage = ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 15f,
                0f, 0.95f, 0f, 0f, 5f,
                0f, 0f, 0.75f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(vintage)
        }
        "noir_dark" -> {
            val noir = ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.5f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(noir)
        }
        else -> {}
    }

    return cm
}

/**
 * Evaluates smooth Catmull-Rom / cubic Bézier curve velocity at normalized time (0..1).
 */
fun evaluateCurveSpeed(points: List<CurveControlPoint>, normTime: Float): Float {
    if (points.isEmpty()) return 1.0f
    if (points.size == 1) return points[0].speed.coerceIn(0.1f, 8.0f)
    val sorted = points.sortedBy { it.time }
    if (normTime <= sorted.first().time) return sorted.first().speed.coerceIn(0.1f, 8.0f)
    if (normTime >= sorted.last().time) return sorted.last().speed.coerceIn(0.1f, 8.0f)
    for (i in 0 until sorted.size - 1) {
        val p0 = sorted[i]
        val p1 = sorted[i + 1]
        if (normTime in p0.time..p1.time) {
            val span = (p1.time - p0.time).coerceAtLeast(0.001f)
            val t = ((normTime - p0.time) / span).coerceIn(0f, 1f)
            val smoothT = (1.0f - kotlin.math.cos(t * Math.PI.toFloat())) * 0.5f
            return (p0.speed + (p1.speed - p0.speed) * smoothT).coerceIn(0.1f, 8.0f)
        }
    }
    return sorted.last().speed.coerceIn(0.1f, 8.0f)
}
