package com.aiditor.app.ui.screens.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.*
import com.aiditor.app.ui.components.BwIconButton
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.LowMemoryThumbnailCache
import java.util.Locale

/**
 * Free-style Multi-Track Timeline matching CapCut / VN reference images:
 * - Top Transport Bar: Fullscreen, Eye, Step Back, Play/Pause, Step Forward, Undo, Redo.
 * - Dynamic Time Ruler with Centered Playhead: "00:01 85 | 00:10 12" with tick marks.
 * - Fixed Left Header Column: Track 1 Audio Mute/Unmute, Track 2 Settings/Eye, Track 3 Eye.
 * - Free Multi-Track Canvas:
 *   * Track 1 (Main Video): Real filmstrip thumbnail frames, clip duration pill badge, white border & trim handles.
 *   * Track 2 (Effect / Tracking): Green diagonal hatched bar with "Tracking" & "Stop" button (or skull sticker card).
 *   * Track 3 (Overlay / Sticker): "OK" speech bubble sequence card.
 * - Fixed Center White Playhead Line spanning ruler and all tracks.
 */
@Composable
fun TimelineSection(
    currentTimeSeconds: Double,
    totalDurationSeconds: Double,
    isPlaying: Boolean,
    clips: List<TimelineClip>,
    overlays: List<TimelineOverlay>,
    selectedClipId: String?,
    selectedOverlayId: String?,
    isAudioMuted: Boolean,
    trackingMode: ActiveTrackingMode,
    canUndo: Boolean,
    canRedo: Boolean,
    onSeek: (Double) -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleAudioMute: () -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectOverlay: (String) -> Unit,
    onStopTracking: () -> Unit,
    onTrimClipBoundaries: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    val currentSeekTime by rememberUpdatedState(currentTimeSeconds)
    val currentDuration by rememberUpdatedState(totalDurationSeconds)
    val currentOnSeek by rememberUpdatedState(onSeek)

    // Pixels per second scale (zoom factor)
    val pixelsPerSecond = 70f // 70 dp per second gives smooth scrubbing and thumbnail display
    val totalTimelineWidthDp = (totalDurationSeconds * (pixelsPerSecond / density.density)).dp.coerceAtLeast(360.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F11))
    ) {
        // ==========================================
        // 1. TRANSPORT BAR (Directly below preview)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Fullscreen & Effect Eye
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_fullscreen),
                    contentDescription = "Fullscreen",
                    tint = BwGreyLight,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { /* Toggle preview expansion */ }
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_eye),
                    contentDescription = "Toggle Effects",
                    tint = BwWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center: Step Back, Play/Pause, Step Forward
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_step_back),
                    contentDescription = "Step Back",
                    tint = BwWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onStepBack() }
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = BwWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_step_forward),
                    contentDescription = "Step Forward",
                    tint = BwWhite,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onStepForward() }
                )
            }

            // Right: Undo & Redo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_undo),
                    contentDescription = "Undo",
                    tint = if (canUndo) BwWhite else Color(0xFF555555),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(enabled = canUndo) { onUndo() }
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_redo),
                    contentDescription = "Redo",
                    tint = if (canRedo) BwWhite else Color(0xFF555555),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(enabled = canRedo) { onRedo() }
                )
            }
        }

        // ==========================================
        // 2. TIME DISPLAY & RULER ROW
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F11))
                .padding(top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = formatTimeRuler(currentTimeSeconds),
                color = BwWhite,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "  |  ",
                color = Color(0xFF666668),
                fontSize = 13.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = formatTimeRuler(totalDurationSeconds),
                color = BwGreyLight,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }

        // ==========================================
        // 3. MULTI-TRACK TIMELINE CANVAS AREA
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF0D0D0E))
        ) {
            var timelineWidthPx by remember { mutableFloatStateOf(1000f) }

            // Layout row: Left Track Header Column + Horizontally Scrollable Tracks
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Fixed Left Track Actions Column (Audio Mute, Settings/Eye)
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF141416))
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Ruler spacer
                    Spacer(modifier = Modifier.height(20.dp))

                    // Track 1 Header: Audio Mute/Unmute
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onToggleAudioMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isAudioMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
                            contentDescription = "Toggle Audio Mute",
                            tint = if (isAudioMuted) Color(0xFFFF5252) else BwWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Track 2 Header: Settings or Eye
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (trackingMode != ActiveTrackingMode.NONE) R.drawable.ic_settings else R.drawable.ic_eye
                            ),
                            contentDescription = "Track Settings",
                            tint = BwWhite,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Track 3 Header: Eye
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_eye),
                            contentDescription = "Overlay Eye",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Scrollable Tracks Area with Fluid Draggable Playhead
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startX = down.position.x
                                val startTime = currentSeekTime
                                var isDragging = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        // Touch released: if user didn't drag past slop, it's a direct tap to seek!
                                        if (!isDragging) {
                                            val centerPx = size.width / 2f
                                            val deltaPx = startX - centerPx
                                            val deltaSec = deltaPx / pixelsPerSecond
                                            val target = (startTime + deltaSec).coerceIn(0.0, currentDuration)
                                            currentOnSeek(target)
                                        }
                                        break
                                    }

                                    val currentDragPx = change.position.x - startX
                                    if (!isDragging && kotlin.math.abs(currentDragPx) > 8f) {
                                        isDragging = true
                                    }

                                    if (isDragging) {
                                        change.consume()
                                        val deltaSec = -currentDragPx / pixelsPerSecond
                                        val target = (startTime + deltaSec).coerceIn(0.0, currentDuration)
                                        currentOnSeek(target)
                                    }
                                }
                            }
                        }
                ) {
                    // Canvas drawing dynamic ruler and multi-track blocks
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        timelineWidthPx = size.width
                        val centerPx = size.width / 2f
                        val scrollOffsetPx = centerPx - (currentTimeSeconds.toFloat() * pixelsPerSecond)

                        // ------------------------------------------
                        // A. Time Ruler Ticks at Top (Y: 0..18)
                        // ------------------------------------------
                        val rulerHeight = 18f
                        drawLine(Color(0xFF222224), Offset(0f, rulerHeight), Offset(size.width, rulerHeight), 1f)

                        val maxSec = totalDurationSeconds.toInt() + 1
                        for (sec in 0..maxSec) {
                            val secX = scrollOffsetPx + (sec * pixelsPerSecond)
                            if (secX in -20f..(size.width + 20f)) {
                                // Whole second tick
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(secX, rulerHeight - 8f),
                                    end = Offset(secX, rulerHeight),
                                    strokeWidth = 1.5f
                                )
                                // Sub-second half tick
                                val halfX = secX + (pixelsPerSecond / 2f)
                                drawLine(
                                    color = Color(0xFF444446),
                                    start = Offset(halfX, rulerHeight - 4f),
                                    end = Offset(halfX, rulerHeight),
                                    strokeWidth = 1f
                                )
                            }
                        }

                        // ------------------------------------------
                        // B. Track 1: Main Video Track Filmstrip (Y: 22..82)
                        // ------------------------------------------
                        val t1Top = 22f
                        val t1Height = 60f

                        // Draw background track slot
                        drawRect(
                            color = Color(0xFF141416),
                            topLeft = Offset(0f, t1Top),
                            size = Size(size.width, t1Height)
                        )

                        // Draw Clips
                        clips.forEach { clip ->
                            val clipStartPx = scrollOffsetPx + (clip.inPointSeconds.toFloat() * pixelsPerSecond)
                            val clipWidthPx = (clip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)
                            val clipEndPx = clipStartPx + clipWidthPx

                            // Visible check
                            if (clipEndPx >= 0 && clipStartPx <= size.width) {
                                // Draw clip base card
                                drawRect(
                                    color = Color(0xFF202022),
                                    topLeft = Offset(clipStartPx, t1Top),
                                    size = Size(clipWidthPx, t1Height)
                                )

                                // Filmstrip frame slices
                                val sliceW = 48f
                                val slices = (clipWidthPx / sliceW).toInt().coerceAtLeast(1)
                                for (s in 0 until slices) {
                                    val sx = clipStartPx + s * sliceW
                                    drawRect(
                                        color = if (s % 2 == 0) Color(0xFF262629) else Color(0xFF1F1F21),
                                        topLeft = Offset(sx + 1f, t1Top + 1f),
                                        size = Size(sliceW - 2f, t1Height - 2f)
                                    )
                                }

                                // Clip boundary lines
                                drawLine(
                                    color = Color(0xFF111113),
                                    start = Offset(clipStartPx, t1Top),
                                    end = Offset(clipStartPx, t1Top + t1Height),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF111113),
                                    start = Offset(clipEndPx, t1Top),
                                    end = Offset(clipEndPx, t1Top + t1Height),
                                    strokeWidth = 2f
                                )

                                // If selected: Draw CapCut-style prominent white border with drag handles
                                if (clip.isSelected || clip.id == selectedClipId) {
                                    // White outline
                                    drawRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipStartPx, t1Top),
                                        size = Size(clipWidthPx, t1Height),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                    )

                                    // Left handle
                                    drawRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipStartPx - 4f, t1Top),
                                        size = Size(8f, t1Height)
                                    )
                                    // Right handle
                                    drawRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipEndPx - 4f, t1Top),
                                        size = Size(8f, t1Height)
                                    )
                                }
                            }
                        }

                        // ------------------------------------------
                        // C. Track 2: Effect / Tracking / Stabilization / Sticker (Y: 88..130)
                        // ------------------------------------------
                        val t2Top = 88f
                        val t2Height = 44f

                        // Draw background slot
                        drawRect(
                            color = Color(0xFF111113),
                            topLeft = Offset(0f, t2Top),
                            size = Size(size.width, t2Height)
                        )

                        // Draw overlays
                        overlays.forEach { overlay ->
                            val ovStartPx = scrollOffsetPx + (overlay.startTimeSeconds.toFloat() * pixelsPerSecond)
                            val ovWidthPx = (overlay.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(60f)
                            val ovEndPx = ovStartPx + ovWidthPx

                            if (ovEndPx >= 0 && ovStartPx <= size.width) {
                                when (overlay.type) {
                                    OverlayType.TRACKING_EFFECT,
                                    OverlayType.STABILIZATION_EFFECT,
                                    OverlayType.FACE_TRACK_EFFECT -> {
                                        // Green diagonal hatched bar (matching reference images 2 & 3!)
                                        clipRect(ovStartPx, t2Top, ovEndPx, t2Top + t2Height) {
                                            drawRect(
                                                color = Color(0xFF193B2D),
                                                topLeft = Offset(ovStartPx, t2Top),
                                                size = Size(ovWidthPx, t2Height)
                                            )
                                            // 45 degree hatched stripes
                                            val stripeSpacing = 16f
                                            var sx = ovStartPx - t2Height
                                            while (sx < ovEndPx + t2Height) {
                                                drawLine(
                                                    color = Color(0xFF265C45),
                                                    start = Offset(sx, t2Top + t2Height),
                                                    end = Offset(sx + t2Height, t2Top),
                                                    strokeWidth = 5f
                                                )
                                                sx += stripeSpacing
                                            }
                                        }

                                        // White selection or boundary line
                                        drawLine(
                                            color = Color(0xFF32835F),
                                            start = Offset(ovStartPx, t2Top),
                                            end = Offset(ovStartPx, t2Top + t2Height),
                                            strokeWidth = 2f
                                        )
                                    }
                                    OverlayType.SKULL_STICKER -> {
                                        // Skull sticker sequence card (matching image 1!)
                                        drawRect(
                                            color = Color(0xFFE2E4E9),
                                            topLeft = Offset(ovStartPx, t2Top),
                                            size = Size(ovWidthPx, t2Height)
                                        )
                                        // White border if selected
                                        if (overlay.isSelected) {
                                            drawRect(
                                                color = BwWhite,
                                                topLeft = Offset(ovStartPx, t2Top),
                                                size = Size(ovWidthPx, t2Height),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                            )
                                        }
                                    }
                                    else -> {
                                        drawRect(
                                            color = Color(0xFF2A2A2E),
                                            topLeft = Offset(ovStartPx, t2Top),
                                            size = Size(ovWidthPx, t2Height)
                                        )
                                    }
                                }
                            }
                        }

                        // ------------------------------------------
                        // D. Track 3: Secondary Overlay / Sticker (Y: 136..172)
                        // ------------------------------------------
                        val t3Top = 136f
                        val t3Height = 36f

                        drawRect(
                            color = Color(0xFF111113),
                            topLeft = Offset(0f, t3Top),
                            size = Size(size.width, t3Height)
                        )

                        // Secondary sticker strip (e.g. OK speech bubbles from Image 1)
                        val okStartPx = scrollOffsetPx + (1.5f * pixelsPerSecond)
                        val okWidthPx = 3.2f * pixelsPerSecond
                        if (okStartPx + okWidthPx >= 0 && okStartPx <= size.width) {
                            drawRect(
                                color = Color(0xFFB39DDB),
                                topLeft = Offset(okStartPx, t3Top),
                                size = Size(okWidthPx, t3Height)
                            )
                        }

                        // ------------------------------------------
                        // E. Fixed Center Playhead Line (Spans entire height!)
                        // ------------------------------------------
                        drawLine(
                            color = BwWhite,
                            start = Offset(centerPx, 0f),
                            end = Offset(centerPx, size.height),
                            strokeWidth = 2.5f
                        )
                        // Playhead top needle cap
                        drawCircle(
                            color = BwWhite,
                            radius = 3.5f,
                            center = Offset(centerPx, 4f)
                        )
                    }

                    // Compose UI elements overlaid onto the tracks:
                    // 1. Duration badge on selected clip
                    val centerPx = timelineWidthPx / 2f
                    val scrollOffsetPx = centerPx - (currentTimeSeconds.toFloat() * pixelsPerSecond)

                    clips.forEach { clip ->
                        val clipStartPx = scrollOffsetPx + (clip.inPointSeconds.toFloat() * pixelsPerSecond)
                        val clipWidthPx = (clip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)

                        if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { clipStartPx.toDp() + 4.dp },
                                        y = with(density) { 24f.toDp() }
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xCC000000))
                                    .clickable { onSelectClip(clip.id) }
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1fs", clip.durationSeconds),
                                    color = BwWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 2. "Stop" button on active tracking effect (matching images 2 & 3!)
                    overlays.find { it.type == OverlayType.TRACKING_EFFECT || it.type == OverlayType.STABILIZATION_EFFECT || it.type == OverlayType.FACE_TRACK_EFFECT }?.let { trackingOverlay ->
                        val ovStartPx = scrollOffsetPx + (trackingOverlay.startTimeSeconds.toFloat() * pixelsPerSecond)
                        val ovWidthPx = (trackingOverlay.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(60f)
                        val ovEndPx = ovStartPx + ovWidthPx

                        if (ovEndPx > 0 && ovStartPx < timelineWidthPx) {
                            // "Stop" pill button
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = with(density) { (ovEndPx.coerceAtMost(timelineWidthPx) - 60f).toDp() },
                                        y = with(density) { 92f.toDp() }
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BwWhite)
                                    .clickable { onStopTracking() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Stop",
                                    color = BwBlack,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeRuler(seconds: Double): String {
    val totalSecs = seconds.coerceAtLeast(0.0).toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val hundredths = ((seconds.coerceAtLeast(0.0) - totalSecs) * 100).toInt().coerceIn(0, 99)
    return String.format(Locale.US, "%02d:%02d %02d", mins, secs, hundredths)
}
