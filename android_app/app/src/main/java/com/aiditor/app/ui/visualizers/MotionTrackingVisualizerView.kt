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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*

@Composable
fun MotionTrackingVisualizerView(
    data: ToolVisualizerData.MotionTracking,
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
                text = "MOTION TRACKING TRAJECTORY & TELEMETRY",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "CONF: ${(data.averageConfidence * 100).toInt()}%",
                color = BwWhite,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(1.dp, BwGreyDark, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw center crosshair reference
                drawLine(Color(0xFF222222), Offset(w / 2f, 0f), Offset(w / 2f, h), 1f)
                drawLine(Color(0xFF222222), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)

                // Draw trajectory spline path
                var prevOffset: Offset? = null
                data.keyframes.forEach { kf ->
                    val curr = Offset(kf.x * w, kf.y * h)
                    prevOffset?.let { prev ->
                        drawLine(
                            color = Color(0xFF666666),
                            start = prev,
                            end = curr,
                            strokeWidth = 1.5f
                        )
                    }
                    prevOffset = curr
                }

                // Draw latest target bounding box & cyber crosshair
                val latest = data.keyframes.lastOrNull() ?: return@Canvas
                val cx = latest.x * w
                val cy = latest.y * h
                val bw = latest.width * w
                val bh = latest.height * h

                // Bounding box
                drawRect(
                    color = BwWhite,
                    topLeft = Offset(cx - bw / 2f, cy - bh / 2f),
                    size = Size(bw, bh),
                    style = Stroke(width = 1.5f)
                )

                // Corner reticle notches
                val notchLen = 8f
                // Top-left
                drawLine(BwWhite, Offset(cx - bw / 2f, cy - bh / 2f), Offset(cx - bw / 2f + notchLen, cy - bh / 2f), 2.5f)
                drawLine(BwWhite, Offset(cx - bw / 2f, cy - bh / 2f), Offset(cx - bw / 2f, cy - bh / 2f + notchLen), 2.5f)
                // Bottom-right
                drawLine(BwWhite, Offset(cx + bw / 2f, cy + bh / 2f), Offset(cx + bw / 2f - notchLen, cy + bh / 2f), 2.5f)
                drawLine(BwWhite, Offset(cx + bw / 2f, cy + bh / 2f), Offset(cx + bw / 2f, cy + bh / 2f - notchLen), 2.5f)

                // Center crosshair dot
                drawCircle(color = BwWhite, radius = 3.5f, center = Offset(cx, cy))
            }
        }
    }
}
