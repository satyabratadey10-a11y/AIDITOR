package com.aiditor.app.ui.screens.workspace

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.withTimeoutOrNull
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
    onDeselectAll: () -> Unit = {},
    onSelectOverlay: (String) -> Unit = {},
    onStopTracking: () -> Unit = {},
    onTrimClipBoundaries: (String, Double, Double, Boolean) -> Unit = { _, _, _, _ -> },
    onMoveClip: (String, Double, Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    val maxClipTrack = (clips.maxOfOrNull { it.trackIndex } ?: 0).coerceAtLeast(0)
    val hasOverlays = overlays.isNotEmpty() || trackingMode != ActiveTrackingMode.NONE
    val totalTrackLanes = (if (hasOverlays) maxClipTrack + 2 else maxClipTrack + 1).coerceAtLeast(1)

    val timelineHeight = ((totalTrackLanes * 64) + 48).dp.coerceIn(110.dp, 320.dp)

    val currentSeekTime by rememberUpdatedState(currentTimeSeconds)
    val currentDuration by rememberUpdatedState(totalDurationSeconds)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentClips by rememberUpdatedState(clips)
    val currentSelectedClipId by rememberUpdatedState(selectedClipId)
    val currentOnTrimClipBoundaries by rememberUpdatedState(onTrimClipBoundaries)
    val currentOnMoveClip by rememberUpdatedState(onMoveClip)
    val currentOnSelectClip by rememberUpdatedState(onSelectClip)
    val currentOnDeselectAll by rememberUpdatedState(onDeselectAll)

    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    val triggerSnapHaptic = remember(vibrator) {
        {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(15L)
                }
            } catch (_: Exception) {}
        }
    }

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
                .height(timelineHeight)
                .background(Color(0xFF0D0D0E))
        ) {
            var timelineWidthPx by remember { mutableFloatStateOf(1000f) }

            // Layout row: Left Track Header Column + Horizontally Scrollable Tracks
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left Track Actions Column (Only active tracks show headers!)
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

                    for (t in 0 until totalTrackLanes) {
                        val isTrackAudio = currentClips.any { it.trackIndex == t && it.isAudio }
                        val isOverlayLane = hasOverlays && t == (totalTrackLanes - 1)
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isOverlayLane -> Color(0xFF193B2D)
                                        isTrackAudio -> Color(0xFF162725)
                                        else -> Color(0xFF202024)
                                    }
                                )
                                .clickable {
                                    if (t == 0) onToggleAudioMute()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (t == 0) {
                                Icon(
                                    painter = painterResource(id = if (isAudioMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
                                    contentDescription = "Toggle Audio Mute",
                                    tint = if (isAudioMuted) Color(0xFFFF5252) else BwWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (isTrackAudio) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_beat_sync),
                                    contentDescription = "Audio Track",
                                    tint = Color(0xFF1DE9B6),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (isOverlayLane) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings),
                                    contentDescription = "Overlay Track",
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "T${t + 1}",
                                    color = Color(0xFFBBBBBB),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // Scrollable Tracks Area with Fluid Draggable Playhead
                // Scrollable Tracks Area with Fluid Draggable Playhead and Press-and-Hold Dragging
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(totalTrackLanes) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startX = down.position.x
                                val startY = down.position.y
                                val startTime = currentSeekTime
                                val centerPx = size.width / 2f
                                val scrollOffsetPx = centerPx - (currentSeekTime.toFloat() * pixelsPerSecond)

                                // Detect which track lane was touched (lane height 56f + spacing 8f = 64f per lane)
                                val touchedTrack = ((startY - 22f) / 64f).toInt()
                                val touchedClip = if (startY >= 18f && touchedTrack in 0 until totalTrackLanes) {
                                    currentClips.firstOrNull { clip ->
                                        if (clip.trackIndex == touchedTrack) {
                                            val cStart = scrollOffsetPx + (clip.timelineStartSeconds.toFloat() * pixelsPerSecond)
                                            val cEnd = cStart + (clip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)
                                            startX in (cStart - 12f)..(cEnd + 12f)
                                        } else false
                                    }
                                } else null

                                val isSelClipTouched = touchedClip != null && (touchedClip.isSelected || touchedClip.id == currentSelectedClipId)
                                val handleRadiusPx = 32f

                                var dragMode = 0 // 0 = playhead scrub, 1 = trim left, 2 = trim right, 3 = move clip across time and tracks
                                var activeClip = touchedClip
                                var initialClipIn = 0.0
                                var initialClipOut = 0.0
                                var initialClipStart = 0.0
                                var initialTrack = touchedClip?.trackIndex ?: 0
                                var currentDragTrack = initialTrack
                                var lastNewIn = 0.0
                                var lastNewOut = 0.0
                                var lastNewStart = 0.0
                                var wasSnappedTimeline = false
                                var isDragging = false

                                // Trim handles check on selected clip
                                if (isSelClipTouched && touchedClip != null) {
                                    val cStart = scrollOffsetPx + (touchedClip.timelineStartSeconds.toFloat() * pixelsPerSecond)
                                    val cEnd = cStart + (touchedClip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)
                                    if (kotlin.math.abs(startX - cStart) <= handleRadiusPx) {
                                        dragMode = 1
                                        initialClipIn = touchedClip.inPointSeconds
                                        initialClipOut = touchedClip.outPointSeconds
                                        lastNewIn = initialClipIn
                                        lastNewOut = initialClipOut
                                    } else if (kotlin.math.abs(startX - cEnd) <= handleRadiusPx) {
                                        dragMode = 2
                                        initialClipIn = touchedClip.inPointSeconds
                                        initialClipOut = touchedClip.outPointSeconds
                                        lastNewIn = initialClipIn
                                        lastNewOut = initialClipOut
                                    }
                                }

                                // Press-and-hold detection for moving clip
                                if (dragMode == 0 && touchedClip != null) {
                                    var downReleased = false
                                    var movedEarly = false
                                    val holdDeadline = System.currentTimeMillis() + 220L

                                    while (System.currentTimeMillis() < holdDeadline) {
                                        val waitEvent = withTimeoutOrNull((holdDeadline - System.currentTimeMillis()).coerceAtLeast(1L)) {
                                            awaitPointerEvent()
                                        }
                                        if (waitEvent == null) {
                                            break
                                        }
                                        val change = waitEvent.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            downReleased = true
                                            break
                                        }
                                        val dist = (change.position - down.position).getDistance()
                                        if (dist > 14f) {
                                            movedEarly = true
                                            break
                                        }
                                    }

                                    if (downReleased) {
                                        val deltaPx = startX - centerPx
                                        val deltaSec = deltaPx / pixelsPerSecond
                                        val target = (startTime + deltaSec).coerceIn(0.0, currentDuration)
                                        currentOnSelectClip(touchedClip.id)
                                        currentOnSeek(target)
                                        return@awaitEachGesture
                                    } else if (movedEarly) {
                                        dragMode = 0
                                        isDragging = true
                                    } else {
                                        dragMode = 3
                                        initialClipStart = touchedClip.timelineStartSeconds
                                        lastNewStart = initialClipStart
                                        initialTrack = touchedClip.trackIndex
                                        currentDragTrack = initialTrack
                                        currentOnSelectClip(touchedClip.id)
                                        triggerSnapHaptic()
                                        isDragging = true
                                    }
                                }

                                var lastSeekDispatchMs = 0L
                                var lastTarget = startTime

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!isDragging) {
                                            val deltaPx = startX - centerPx
                                            val deltaSec = deltaPx / pixelsPerSecond
                                            val target = (startTime + deltaSec).coerceIn(0.0, currentDuration)
                                            if (touchedClip != null) {
                                                currentOnSelectClip(touchedClip.id)
                                            } else {
                                                currentOnDeselectAll()
                                            }
                                            currentOnSeek(target)
                                        } else {
                                            if (dragMode == 1 || dragMode == 2) {
                                                activeClip?.let {
                                                    currentOnTrimClipBoundaries(it.id, lastNewIn, lastNewOut, true)
                                                }
                                            } else if (dragMode == 3) {
                                                activeClip?.let {
                                                    currentOnMoveClip(it.id, lastNewStart, currentDragTrack)
                                                    triggerSnapHaptic()
                                                }
                                            } else if (dragMode == 0) {
                                                currentOnSeek(lastTarget)
                                            }
                                        }
                                        break
                                    }

                                    val currentDragPx = change.position.x - startX
                                    if (!isDragging && kotlin.math.abs(currentDragPx) > 6f) {
                                        isDragging = true
                                    }

                                    if (isDragging) {
                                        change.consume()
                                        when (dragMode) {
                                            1 -> {
                                                val deltaSec = (currentDragPx / pixelsPerSecond).toDouble()
                                                lastNewIn = (initialClipIn + deltaSec).coerceIn(0.0, initialClipOut - 0.1)
                                                lastNewOut = initialClipOut
                                                activeClip?.let {
                                                    currentOnTrimClipBoundaries(it.id, lastNewIn, lastNewOut, false)
                                                }
                                            }
                                            2 -> {
                                                val deltaSec = (currentDragPx / pixelsPerSecond).toDouble()
                                                lastNewIn = initialClipIn
                                                lastNewOut = (initialClipOut + deltaSec).coerceAtLeast(initialClipIn + 0.1)
                                                activeClip?.let {
                                                    currentOnTrimClipBoundaries(it.id, lastNewIn, lastNewOut, false)
                                                }
                                            }
                                            3 -> {
                                                val deltaSec = (currentDragPx / pixelsPerSecond).toDouble()
                                                var newStart = (initialClipStart + deltaSec).coerceAtLeast(0.0)

                                                var snappedTimeline = false
                                                if (kotlin.math.abs(newStart) < 0.15) {
                                                    newStart = 0.0
                                                    snappedTimeline = true
                                                } else if (kotlin.math.abs(newStart - currentSeekTime) < 0.15) {
                                                    newStart = currentSeekTime
                                                    snappedTimeline = true
                                                } else {
                                                    for (other in currentClips) {
                                                        if (other.id != activeClip?.id && other.trackIndex == currentDragTrack) {
                                                            val otherEnd = other.timelineStartSeconds + other.durationSeconds
                                                            if (kotlin.math.abs(newStart - otherEnd) < 0.18) {
                                                                newStart = otherEnd
                                                                snappedTimeline = true
                                                                break
                                                            }
                                                            val thisEnd = newStart + (activeClip?.durationSeconds ?: 0.0)
                                                            if (kotlin.math.abs(thisEnd - other.timelineStartSeconds) < 0.18) {
                                                                newStart = (other.timelineStartSeconds - (activeClip?.durationSeconds ?: 0.0)).coerceAtLeast(0.0)
                                                                snappedTimeline = true
                                                                break
                                                            }
                                                        }
                                                    }
                                                }

                                                val currentTouchY = change.position.y
                                                val newTrack = ((currentTouchY - 22f) / 64f).toInt().coerceIn(0, totalTrackLanes - 1)
                                                if (newTrack != currentDragTrack) {
                                                    currentDragTrack = newTrack
                                                    triggerSnapHaptic()
                                                }

                                                if (snappedTimeline && !wasSnappedTimeline) {
                                                    triggerSnapHaptic()
                                                }
                                                wasSnappedTimeline = snappedTimeline
                                                lastNewStart = newStart

                                                activeClip?.let {
                                                    currentOnMoveClip(it.id, newStart, currentDragTrack)
                                                }
                                            }
                                            else -> {
                                                val deltaSec = -currentDragPx / pixelsPerSecond
                                                val target = (startTime + deltaSec).coerceIn(0.0, currentDuration)
                                                lastTarget = target
                                                val now = System.currentTimeMillis()
                                                if (now - lastSeekDispatchMs >= 25) {
                                                    lastSeekDispatchMs = now
                                                    currentOnSeek(target)
                                                }
                                            }
                                        }
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
                                drawLine(
                                    color = Color(0xFF888888),
                                    start = Offset(secX, rulerHeight - 8f),
                                    end = Offset(secX, rulerHeight),
                                    strokeWidth = 1.5f
                                )
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
                        // B. Dynamic Track Background Slots (64f step per lane)
                        // ------------------------------------------
                        for (t in 0 until totalTrackLanes) {
                            val laneTop = 22f + (t * 64f)
                            val isOverlayLane = hasOverlays && t == (totalTrackLanes - 1)
                            val isTrackAudio = clips.any { it.trackIndex == t && it.isAudio }
                            val slotColor = when {
                                isOverlayLane -> Color(0xFF131A16)
                                isTrackAudio -> Color(0xFF101918)
                                else -> Color(0xFF141416)
                            }
                            drawRoundRect(
                                color = slotColor,
                                topLeft = Offset(0f, laneTop),
                                size = Size(size.width, 56f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                            )
                        }

                        // ------------------------------------------
                        // C. Draw Clips Across Dynamic Lanes
                        // ------------------------------------------
                        clips.forEach { clip ->
                            val lane = clip.trackIndex.coerceIn(0, totalTrackLanes - 1)
                            val clipTop = 22f + (lane * 64f)
                            val clipH = 56f
                            val clipStartPx = scrollOffsetPx + (clip.timelineStartSeconds.toFloat() * pixelsPerSecond)
                            val clipWidthPx = (clip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)
                            val clipEndPx = clipStartPx + clipWidthPx

                            if (clipEndPx >= 0 && clipStartPx <= size.width) {
                                val isClipSelected = clip.isSelected || clip.id == selectedClipId

                                when {
                                    clip.isAudio -> {
                                        // Audio track: Teal background & procedural soundwave
                                        drawRoundRect(
                                            color = Color(0xFF0F2B26),
                                            topLeft = Offset(clipStartPx, clipTop),
                                            size = Size(clipWidthPx, clipH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                        )

                                        val barW = 3f
                                        val barGap = 2f
                                        val totalBars = ((clipWidthPx - 8f) / (barW + barGap)).toInt().coerceIn(2, 200)
                                        val midY = clipTop + (clipH / 2f)

                                        for (b in 0 until totalBars) {
                                            val bx = clipStartPx + 4f + b * (barW + barGap)
                                            if (bx in 0f..size.width) {
                                                val pseudoSeed = ((clip.timelineStartSeconds * 100).toInt() + b * 13) % 100
                                                val barH = (10f + (pseudoSeed / 100f) * 36f).coerceIn(6f, 46f)
                                                drawLine(
                                                    color = if (isClipSelected) Color(0xFF00E5FF) else Color(0xFF1DE9B6),
                                                    start = Offset(bx, midY - (barH / 2f)),
                                                    end = Offset(bx, midY + (barH / 2f)),
                                                    strokeWidth = barW
                                                )
                                            }
                                        }
                                    }
                                    clip.isImage -> {
                                        // Image track: Warm Amber background & golden accent
                                        drawRoundRect(
                                            color = Color(0xFF2E2718),
                                            topLeft = Offset(clipStartPx, clipTop),
                                            size = Size(clipWidthPx, clipH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                        )

                                        drawRect(
                                            color = Color(0xFFFFB300),
                                            topLeft = Offset(clipStartPx, clipTop),
                                            size = Size(clipWidthPx, 4f)
                                        )

                                        val sliceW = 48f
                                        val slices = (clipWidthPx / sliceW).toInt().coerceAtLeast(1)
                                        for (s in 0 until slices) {
                                            val sx = clipStartPx + s * sliceW
                                            drawRect(
                                                color = if (s % 2 == 0) Color(0xFF38301D) else Color(0xFF2A2315),
                                                topLeft = Offset(sx + 1f, clipTop + 5f),
                                                size = Size(sliceW - 2f, clipH - 6f)
                                            )
                                        }
                                    }
                                    else -> {
                                        // Video track: Slate background & filmstrip frame slices
                                        drawRoundRect(
                                            color = Color(0xFF202022),
                                            topLeft = Offset(clipStartPx, clipTop),
                                            size = Size(clipWidthPx, clipH),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                        )

                                        val sliceW = 48f
                                        val slices = (clipWidthPx / sliceW).toInt().coerceAtLeast(1)
                                        for (s in 0 until slices) {
                                            val sx = clipStartPx + s * sliceW
                                            drawRect(
                                                color = if (s % 2 == 0) Color(0xFF262629) else Color(0xFF1F1F21),
                                                topLeft = Offset(sx + 1f, clipTop + 1f),
                                                size = Size(sliceW - 2f, clipH - 2f)
                                            )
                                        }
                                    }
                                }

                                // Clip boundary lines
                                drawLine(
                                    color = Color(0xFF111113),
                                    start = Offset(clipStartPx, clipTop),
                                    end = Offset(clipStartPx, clipTop + clipH),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF111113),
                                    start = Offset(clipEndPx, clipTop),
                                    end = Offset(clipEndPx, clipTop + clipH),
                                    strokeWidth = 2f
                                )

                                if (isClipSelected) {
                                    // CapCut-style prominent white border
                                    drawRoundRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipStartPx, clipTop),
                                        size = Size(clipWidthPx, clipH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                                    )

                                    // Left vertical handle with dashed line indicator
                                    drawRoundRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipStartPx - 6f, clipTop),
                                        size = Size(12f, clipH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                    drawLine(
                                        color = Color(0xFF141416),
                                        start = Offset(clipStartPx, clipTop + clipH * 0.32f),
                                        end = Offset(clipStartPx, clipTop + clipH * 0.68f),
                                        strokeWidth = 2.5f
                                    )

                                    // Right vertical handle with dashed line indicator
                                    drawRoundRect(
                                        color = BwWhite,
                                        topLeft = Offset(clipEndPx - 6f, clipTop),
                                        size = Size(12f, clipH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                    )
                                    drawLine(
                                        color = Color(0xFF141416),
                                        start = Offset(clipEndPx, clipTop + clipH * 0.32f),
                                        end = Offset(clipEndPx, clipTop + clipH * 0.68f),
                                        strokeWidth = 2.5f
                                    )
                                }
                            }
                        }

                        // ------------------------------------------
                        // D. Overlays (Tracking / Stickers)
                        // ------------------------------------------
                        if (hasOverlays) {
                            val overlayTrack = (totalTrackLanes - 1).coerceAtLeast(maxClipTrack + 1)
                            val ovTop = 22f + (overlayTrack * 64f)
                            val ovHeight = 56f

                            overlays.forEach { overlay ->
                                val ovStartPx = scrollOffsetPx + (overlay.startTimeSeconds.toFloat() * pixelsPerSecond)
                                val ovWidthPx = (overlay.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(60f)
                                val ovEndPx = ovStartPx + ovWidthPx

                                if (ovEndPx >= 0 && ovStartPx <= size.width) {
                                    when (overlay.type) {
                                        OverlayType.TRACKING_EFFECT,
                                        OverlayType.STABILIZATION_EFFECT,
                                        OverlayType.FACE_TRACK_EFFECT -> {
                                            clipRect(ovStartPx, ovTop, ovEndPx, ovTop + ovHeight) {
                                                drawRoundRect(
                                                    color = Color(0xFF193B2D),
                                                    topLeft = Offset(ovStartPx, ovTop),
                                                    size = Size(ovWidthPx, ovHeight),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                                )
                                                val stripeSpacing = 16f
                                                var sx = ovStartPx - ovHeight
                                                while (sx < ovEndPx + ovHeight) {
                                                    drawLine(
                                                        color = Color(0xFF265C45),
                                                        start = Offset(sx, ovTop + ovHeight),
                                                        end = Offset(sx + ovHeight, ovTop),
                                                        strokeWidth = 5f
                                                    )
                                                    sx += stripeSpacing
                                                }
                                            }
                                            drawLine(
                                                color = Color(0xFF32835F),
                                                start = Offset(ovStartPx, ovTop),
                                                end = Offset(ovStartPx, ovTop + ovHeight),
                                                strokeWidth = 2f
                                            )
                                        }
                                        OverlayType.SKULL_STICKER -> {
                                            drawRoundRect(
                                                color = Color(0xFFE2E4E9),
                                                topLeft = Offset(ovStartPx, ovTop),
                                                size = Size(ovWidthPx, ovHeight),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                            )
                                            if (overlay.isSelected) {
                                                drawRoundRect(
                                                    color = BwWhite,
                                                    topLeft = Offset(ovStartPx, ovTop),
                                                    size = Size(ovWidthPx, ovHeight),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                                )
                                            }
                                        }
                                        else -> {
                                            drawRoundRect(
                                                color = Color(0xFF2A2A2E),
                                                topLeft = Offset(ovStartPx, ovTop),
                                                size = Size(ovWidthPx, ovHeight),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ------------------------------------------
                        // E. Fixed Center Playhead Line
                        // ------------------------------------------
                        drawLine(
                            color = BwWhite,
                            start = Offset(centerPx, 0f),
                            end = Offset(centerPx, size.height),
                            strokeWidth = 2.5f
                        )
                        drawCircle(
                            color = BwWhite,
                            radius = 3.5f,
                            center = Offset(centerPx, 4f)
                        )
                    }

                    // Compose UI elements overlaid onto the tracks:
                    // 1. Duration badge ONLY on SELECTED clip
                    val centerPx = timelineWidthPx / 2f
                    val scrollOffsetPx = centerPx - (currentTimeSeconds.toFloat() * pixelsPerSecond)

                    clips.forEach { clip ->
                        val isClipSelected = clip.isSelected || clip.id == selectedClipId
                        if (isClipSelected) {
                            val clipLane = clip.trackIndex.coerceIn(0, totalTrackLanes - 1)
                            val clipStartPx = scrollOffsetPx + (clip.timelineStartSeconds.toFloat() * pixelsPerSecond)
                            val clipWidthPx = (clip.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(30f)

                            if (clipStartPx + clipWidthPx > 0 && clipStartPx < timelineWidthPx) {
                                val clipTopDp = with(density) { (22f + clipLane * 64f + 4f).toDp() }
                                Row(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { clipStartPx.toDp() + 6.dp },
                                            y = clipTopDp
                                        ),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xEE000000))
                                            .clickable { onSelectClip(clip.id) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%.1fs", clip.durationSeconds),
                                            color = BwWhite,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (clip.isOpticalFlowEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xEE1B5E20))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "⚡ ${clip.opticalFlowFps} FPS",
                                                color = Color(0xFFA5D6A7),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    if (clip.colorGrade.filterPreset != "original") {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xEE37474F))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = clip.colorGrade.filterPreset.uppercase().replace("_", " "),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. "Stop" button on active tracking effect
                    if (hasOverlays) {
                        val overlayTrack = (totalTrackLanes - 1).coerceAtLeast(maxClipTrack + 1)
                        val ovTopDp = with(density) { (22f + overlayTrack * 64f + 8f).toDp() }
                        overlays.find { it.type == OverlayType.TRACKING_EFFECT || it.type == OverlayType.STABILIZATION_EFFECT || it.type == OverlayType.FACE_TRACK_EFFECT }?.let { trackingOverlay ->
                            val ovStartPx = scrollOffsetPx + (trackingOverlay.startTimeSeconds.toFloat() * pixelsPerSecond)
                            val ovWidthPx = (trackingOverlay.durationSeconds.toFloat() * pixelsPerSecond).coerceAtLeast(60f)
                            val ovEndPx = ovStartPx + ovWidthPx

                            if (ovEndPx > 0 && ovStartPx < timelineWidthPx) {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = with(density) { (ovEndPx.coerceAtMost(timelineWidthPx) - 60f).toDp() },
                                            y = ovTopDp
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
}

private fun formatTimeRuler(seconds: Double): String {
    val totalSecs = seconds.coerceAtLeast(0.0).toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val hundredths = ((seconds.coerceAtLeast(0.0) - totalSecs) * 100).toInt().coerceIn(0, 99)
    return String.format(Locale.US, "%02d:%02d %02d", mins, secs, hundredths)
}
