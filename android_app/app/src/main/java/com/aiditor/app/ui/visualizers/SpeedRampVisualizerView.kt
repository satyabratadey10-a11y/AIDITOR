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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*

@Composable
fun SpeedRampVisualizerView(
    data: ToolVisualizerData.SpeedRamp,
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
                text = "BÉZIER VELOCITY CURVE (${data.preset.uppercase()})",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "PEAK: ${data.peakSpeed}x",
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
                val maxSpeed = maxOf(3.0f, data.peakSpeed * 1.1f)

                // Draw 1.0x baseline
                val baselineY = h - (1.0f / maxSpeed) * h
                drawLine(
                    color = Color(0xFF333333),
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1f
                )

                if (data.samples.isNotEmpty()) {
                    val path = Path()
                    val totalDuration = data.samples.last().time.coerceAtLeast(0.1f)

                    data.samples.forEachIndexed { i, sample ->
                        val px = (sample.time / totalDuration) * w
                        val py = h - (sample.value / maxSpeed) * h
                        if (i == 0) {
                            path.moveTo(px, py)
                        } else {
                            path.lineTo(px, py)
                        }
                    }

                    // Stroke curve in pure white
                    drawPath(
                        path = path,
                        color = BwWhite,
                        style = Stroke(width = 2.5f)
                    )
                }

                // Draw keyframe control points
                data.controlPoints.forEach { pt ->
                    val cx = (pt.time / 2.0f) * w
                    val cy = h - (pt.speed / maxSpeed) * h
                    drawCircle(color = BwBlack, radius = 5f, center = Offset(cx, cy))
                    drawCircle(color = BwWhite, radius = 4f, center = Offset(cx, cy))
                }
            }
        }
    }
}
