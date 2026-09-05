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
fun RotoscopeVisualizerView(
    data: ToolVisualizerData.Rotoscope,
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
                text = "ROTOSCOPE ALPHA MATTE & CONTOUR",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${data.preset.uppercase()} • [${data.textContent}]",
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

                // Draw background grid lines
                drawLine(Color(0xFF222222), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
                drawLine(Color(0xFF222222), Offset(w / 2f, 0f), Offset(w / 2f, h), 1f)

                // Draw rotoscope polygonal contour
                if (data.contourPoints.isNotEmpty()) {
                    val polyPath = Path()
                    data.contourPoints.forEachIndexed { i, pt ->
                        val px = pt.x * w
                        val py = pt.y * h
                        if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
                    }
                    polyPath.close()

                    // Semi-transparent subject silhouette fill
                    drawPath(
                        path = polyPath,
                        color = Color(0x33FFFFFF)
                    )
                    // Bright white contour edge outline
                    drawPath(
                        path = polyPath,
                        color = BwWhite,
                        style = Stroke(width = 2.0f)
                    )

                    // Draw control vertices
                    data.contourPoints.forEach { pt ->
                        drawCircle(
                            color = BwWhite,
                            radius = 2.5f,
                            center = Offset(pt.x * w, pt.y * h)
                        )
                    }
                }
            }
        }
    }
}
