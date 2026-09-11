package com.aiditor.app.ui.screens.workspace

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
import androidx.compose.foundation.clickable
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
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.LowMemoryExoPlayerHelper
import kotlinx.coroutines.delay

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
    onTimeUpdate: (Double) -> Unit = {},
    videoPath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoPath) {
        if (!videoPath.isNullOrBlank()) {
            try {
                val player = LowMemoryExoPlayerHelper.createLowMemoryPlayer(context).apply {
                    val mediaItem = LowMemoryExoPlayerHelper.buildMediaItem(videoPath)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = if (isAudioMuted) 0f else 1f
                    prepare()
                }
                exoPlayer = player
            } catch (_: Exception) {
                exoPlayer = null
            }
        }
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Audio Mute Synchronization
    LaunchedEffect(isAudioMuted) {
        exoPlayer?.volume = if (isAudioMuted) 0f else 1f
    }

    // Playback Speed & Optical Flow Slow-Mo Synchronization when paused
    LaunchedEffect(middleParams) {
        val baseSpeed = when (middleParams) {
            is MiddleParameters.SpeedRamp -> middleParams.maxSpeedMultiplier.coerceIn(0.1f, 8.0f)
            is MiddleParameters.OpticalFlow -> if (middleParams.isEnabled && middleParams.slowMoFactor < 1.0f) {
                middleParams.slowMoFactor.coerceIn(0.1f, 1.0f)
            } else 1.0f
            else -> 1.0f
        }
        try {
            exoPlayer?.setPlaybackSpeed(baseSpeed)
        } catch (_: Exception) {}
    }

    // Master Clock Synchronization & Dynamic Bézier Speed Ramp (When Playing)
    LaunchedEffect(isPlaying, exoPlayer, middleParams, totalDurationSeconds) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (isPlaying) {
            val startMs = (currentTimeSeconds * 1000).toLong()
            if (kotlin.math.abs(player.currentPosition - startMs) > 200) {
                player.seekTo(startMs)
            }
            player.play()
            while (isPlaying && player.isPlaying) {
                val currentSec = player.currentPosition / 1000.0
                onTimeUpdate(currentSec)

                // Dynamic speed ramp along the Bézier curve during playback
                if (middleParams is MiddleParameters.SpeedRamp && middleParams.curveControlPoints.isNotEmpty()) {
                    val normTime = (currentSec / totalDurationSeconds.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
                    val dynamicSpeed = evaluateCurveSpeed(middleParams.curveControlPoints, normTime)
                    try {
                        if (kotlin.math.abs(player.playbackParameters.speed - dynamicSpeed) > 0.05f) {
                            player.setPlaybackSpeed(dynamicSpeed)
                        }
                    } catch (_: Exception) {}
                }

                delay(16) // ~60 FPS smooth timeline sync
            }
        } else {
            player.pause()
        }
    }

    // Timeline Scrubbing / Seeking: Timeline -> ExoPlayer (ONLY When Paused)
    LaunchedEffect(currentTimeSeconds) {
        if (!isPlaying) {
            exoPlayer?.let { player ->
                val targetMs = (currentTimeSeconds * 1000).toLong()
                player.seekTo(targetMs)
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(ratioModifier)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(
                    width = if (trackingMode != ActiveTrackingMode.NONE) 1.5.dp else 1.dp,
                    color = if (trackingMode != ActiveTrackingMode.NONE) Color(0xFF2E7D32) else BwCardStroke,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            // Actual video surface via ExoPlayer TextureView
            if (exoPlayer != null) {
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
                    ActiveTrackingMode.MOTION_STABILIZATION -> {
                        val cx = w * 0.52f
                        val cy = h * 0.48f
                        val radius = w * 0.26f

                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = 6f
                            pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(cx, cy, radius, paint)

                        drawCircle(
                            color = Color.White,
                            radius = 10f,
                            center = Offset(cx, cy)
                        )

                        val points = listOf(
                            Offset(cx - radius * 0.6f, cy - radius * 0.3f),
                            Offset(cx + radius * 0.5f, cy - radius * 0.5f),
                            Offset(cx - radius * 0.2f, cy + radius * 0.4f),
                            Offset(cx + radius * 0.4f, cy + radius * 0.2f),
                            Offset(cx + radius * 0.1f, cy - radius * 0.7f),
                            Offset(cx - radius * 0.7f, cy + radius * 0.1f),
                            Offset(cx + radius * 0.6f, cy + radius * 0.6f)
                        )
                        points.forEach { pt ->
                            drawCircle(
                                color = Color(0xFF00E676),
                                radius = 7f,
                                center = pt
                            )
                        }
                    }

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

                    ActiveTrackingMode.MOTION_TRACKING -> {
                        val cx = w * 0.5f
                        val cy = h * 0.55f
                        val boxSize = 80f

                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cx - boxSize / 2, cy - boxSize / 2),
                            size = Size(boxSize, boxSize),
                            style = Stroke(width = 3.5f)
                        )

                        val inner = 24f
                        val greenColor = Color(0xFF00E676)
                        drawLine(greenColor, Offset(cx - inner, cy), Offset(cx + inner, cy), 3f)
                        drawLine(greenColor, Offset(cx, cy - inner), Offset(cx, cy + inner), 3f)

                        drawCircle(color = greenColor, radius = 4f, center = Offset(cx, cy))
                    }

                    ActiveTrackingMode.NONE -> {}
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
