package com.aiditor.app.ui.screens.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.TimelineMarker
import com.aiditor.app.ui.components.BwIconButton
import com.aiditor.app.ui.theme.*

/**
 * Interactive Timeline Scrubber & Multi-track View at Center to Bottom.
 * Conforms to: "and timeline at center to bottom"
 */
@Composable
fun TimelineSection(
    currentTimeSeconds: Double,
    totalDurationSeconds: Double,
    markers: List<TimelineMarker>,
    onSeek: (Double) -> Unit,
    onSplit: () -> Unit,
    onTrim: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Quick Timeline Edit Tools (Split, Trim)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MULTI-TRACK TIMELINE",
                color = BwGreyLight,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BwIconButton(
                    iconRes = R.drawable.ic_split,
                    onClick = onSplit,
                    contentDescription = "Split Clip",
                    size = 32.dp,
                    iconSize = 16.dp
                )
                BwIconButton(
                    iconRes = R.drawable.ic_trim,
                    onClick = onTrim,
                    contentDescription = "Trim Clip",
                    size = 32.dp,
                    iconSize = 16.dp
                )
            }
        }

        // Timeline Scrubber & Tracks Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BwDarkSurface)
                .border(1.dp, BwCardStroke, RoundedCornerShape(10.dp))
                .pointerInput(totalDurationSeconds) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(ratio * totalDurationSeconds)
                    }
                }
                .pointerInput(totalDurationSeconds) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(ratio * totalDurationSeconds)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Track 1: Time ruler with second ticks (0..20dp)
                val rulerHeight = 18f
                drawLine(Color(0xFF222222), Offset(0f, rulerHeight), Offset(w, rulerHeight), 1f)

                val tickCount = 10
                for (t in 0..tickCount) {
                    val tx = (w / tickCount) * t
                    drawLine(Color(0xFF666666), Offset(tx, rulerHeight - 6f), Offset(tx, rulerHeight), 1f)
                }

                // Track 2: Video Track (middle band)
                val videoTrackTop = rulerHeight + 4f
                val videoTrackH = 36f
                drawRect(
                    color = BwCardBackground,
                    topLeft = Offset(0f, videoTrackTop),
                    size = Size(w, videoTrackH)
                )
                // Video Clip Segment Blocks
                val segCount = 4
                val segW = w / segCount
                for (s in 0 until segCount) {
                    drawRect(
                        color = Color(0xFF1F1F1F),
                        topLeft = Offset(s * segW + 2f, videoTrackTop + 2f),
                        size = Size(segW - 4f, videoTrackH - 4f)
                    )
                }

                // Track 3: Audio Waveform Track (bottom band)
                val audioTrackTop = videoTrackTop + videoTrackH + 4f
                val audioTrackH = 34f
                drawRect(
                    color = Color(0xFF111111),
                    topLeft = Offset(0f, audioTrackTop),
                    size = Size(w, audioTrackH)
                )

                // Simulated audio wave bars in track
                val waveBars = 50
                val barW = w / waveBars
                val audioMidY = audioTrackTop + audioTrackH / 2f
                for (b in 0 until waveBars) {
                    val barH = ((Math.sin(b * 0.4) * 0.5 + 0.5) * 16.0).toFloat()
                    drawLine(
                        color = Color(0xFF666666),
                        start = Offset(b * barW + barW / 2f, audioMidY - barH),
                        end = Offset(b * barW + barW / 2f, audioMidY + barH),
                        strokeWidth = 1.5f
                    )
                }

                // Markers (Keyframe/Beat pins)
                markers.forEach { m ->
                    val mx = ((m.timeSeconds / totalDurationSeconds.coerceAtLeast(0.1)) * w).toFloat()
                    drawCircle(color = BwWhite, radius = 3f, center = Offset(mx, rulerHeight / 2f))
                }

                // Playhead Needle (Pure White Line with Triangle Head)
                val playheadRatio = (currentTimeSeconds / totalDurationSeconds.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
                val playheadX = playheadRatio * w

                // Vertical playhead line
                drawLine(
                    color = BwWhite,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, h),
                    strokeWidth = 2.0f
                )

                // Playhead head marker
                drawCircle(
                    color = BwWhite,
                    radius = 5.0f,
                    center = Offset(playheadX, 5f)
                )
            }
        }
    }
}
