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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*

@Composable
fun ColorGradeVisualizerView(
    data: ToolVisualizerData.ColorGrade,
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
                text = "LUMINANCE HISTOGRAM & TONE CURVE",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "CONTRAST: ${String.format("%.2f", data.contrast)}",
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

                // Draw 256-bin histogram in monochrome
                if (data.luminanceHistogram.isNotEmpty()) {
                    val maxVal = (data.luminanceHistogram.maxOrNull() ?: 1000).toFloat().coerceAtLeast(1f)
                    val barW = w / data.luminanceHistogram.size
                    data.luminanceHistogram.forEachIndexed { i, count ->
                        val barH = (count / maxVal) * (h * 0.9f)
                        drawRect(
                            color = Color(0xFF333333),
                            topLeft = Offset(i * barW, h - barH),
                            size = Size(maxOf(1f, barW), barH)
                        )
                    }
                }

                // Draw S-curve transfer tone in bright white
                if (data.toneCurve.isNotEmpty()) {
                    val curvePath = Path()
                    val count = data.toneCurve.size
                    data.toneCurve.forEachIndexed { i, outVal ->
                        val px = (i / count.toFloat()) * w
                        val py = h - (outVal / 255.0f) * h
                        if (i == 0) curvePath.moveTo(px, py) else curvePath.lineTo(px, py)
                    }
                    drawPath(
                        path = curvePath,
                        color = BwWhite,
                        style = Stroke(width = 2.0f)
                    )
                }

                // Diagonal reference 1:1 line
                drawLine(
                    color = Color(0xFF222222),
                    start = Offset(0f, h),
                    end = Offset(w, 0f),
                    strokeWidth = 1f
                )
            }
        }
    }
}
