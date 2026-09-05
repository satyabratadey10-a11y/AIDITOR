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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*
import kotlin.math.*

@Composable
fun OpticalFlowVisualizerView(
    data: ToolVisualizerData.OpticalFlow,
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
                text = "OPTICAL FLOW VECTOR FIELD (${data.targetFps} FPS • ${data.mode.uppercase()})",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${data.vectors.size} Vectors",
                color = BwGreyLight,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Vector Field Canvas
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

                // Draw background grid lines
                val gridCols = 8
                val gridRows = 5
                for (c in 1 until gridCols) {
                    val x = (w / gridCols) * c
                    drawLine(
                        color = Color(0xFF1E1E1E),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
                for (r in 1 until gridRows) {
                    val y = (h / gridRows) * r
                    drawLine(
                        color = Color(0xFF1E1E1E),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                // Draw motion vector arrows
                data.vectors.forEach { vec ->
                    val startX = vec.x * w
                    val startY = vec.y * h
                    val endX = startX + vec.dx * w * 3.5f
                    val endY = startY + vec.dy * h * 3.5f

                    // Vector line
                    drawLine(
                        color = BwWhite,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round
                    )
                    // Arrowhead tip dot
                    drawCircle(
                        color = BwWhite,
                        radius = 2.0f,
                        center = Offset(endX, endY)
                    )
                }
            }
        }
    }
}
