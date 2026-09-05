package com.aiditor.app.ui.visualizers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*

@Composable
fun BeatSyncVisualizerView(
    data: ToolVisualizerData.BeatSync,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BwDarkSurface)
            .border(1.dp, BwCardStroke, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO WAVEFORM & BEAT TRANSIENTS (${data.bpm} BPM • ${data.vibe.uppercase()})",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${data.dropCount} Drops",
                color = BwWhite,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(1.dp, BwGreyDark, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Draw center baseline
                drawLine(
                    color = Color(0xFF222222),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1f
                )

                // Draw waveform bars
                val barCount = data.waveform.size
                if (barCount > 0) {
                    val barWidth = w / barCount
                    data.waveform.forEachIndexed { idx, amp ->
                        val barHeight = amp * (h * 0.85f)
                        val x = idx * barWidth
                        drawRect(
                            color = if (amp > 0.7f) BwWhite else Color(0xFF888888),
                            topLeft = Offset(x, midY - barHeight / 2f),
                            size = Size(maxOf(1f, barWidth - 1f), barHeight)
                        )
                    }
                }

                // Draw beat markers at top
                data.beats.forEach { beat ->
                    val normX = ((beat.timeSeconds % 15.0) / 15.0).toFloat() * w
                    if (beat.isDrop) {
                        // Bold white triangle/spike for beat drops
                        drawLine(
                            color = BwWhite,
                            start = Offset(normX, 0f),
                            end = Offset(normX, h),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }
    }
}
