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
import androidx.compose.ui.graphics.nativeCanvas
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

                // Parse neon color safely
                val baseNeonColor = try {
                    Color(android.graphics.Color.parseColor(data.neonColor))
                } catch (e: Exception) {
                    Color(0xFF00F0FF)
                }

                // Draw background grid lines
                drawLine(Color(0xFF222222), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
                drawLine(Color(0xFF222222), Offset(w / 2f, 0f), Offset(w / 2f, h), 1f)

                // If behind_text preset, draw text layer in background
                if (data.preset == "behind_text") {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(160, 255, 255, 255)
                        textSize = 36f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        data.textContent.ifEmpty { "AIDITOR" },
                        w * 0.5f,
                        h * 0.58f,
                        textPaint
                    )
                }

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
                    val fillColor = if (data.preset == "silhouette") {
                        Color(0xBB000000)
                    } else {
                        baseNeonColor.copy(alpha = 0.18f)
                    }
                    drawPath(
                        path = polyPath,
                        color = fillColor
                    )

                    // Outer Diffuse Glow
                    val glowScale = data.glowIntensity.coerceIn(0.5f, 3.0f)
                    val baseWidth = data.outlineWidth.coerceIn(1f, 12f)

                    drawPath(
                        path = polyPath,
                        color = baseNeonColor.copy(alpha = 0.20f),
                        style = Stroke(width = baseWidth * 3.5f * glowScale)
                    )

                    // Mid Neon Glow
                    drawPath(
                        path = polyPath,
                        color = baseNeonColor.copy(alpha = 0.55f),
                        style = Stroke(width = baseWidth * 1.8f * glowScale)
                    )

                    // Sharp Neon Stroke
                    drawPath(
                        path = polyPath,
                        color = baseNeonColor,
                        style = Stroke(width = baseWidth)
                    )

                    // Inner White Hot Core (Neon Saber effect)
                    if (data.preset == "neon_saber" || data.preset == "cyberpunk_glow") {
                        drawPath(
                            path = polyPath,
                            color = Color.White.copy(alpha = 0.85f),
                            style = Stroke(width = (baseWidth * 0.4f).coerceAtLeast(1.0f))
                        )
                    }

                    // Draw control vertices
                    data.contourPoints.forEach { pt ->
                        drawCircle(
                            color = baseNeonColor,
                            radius = 3.5f,
                            center = Offset(pt.x * w, pt.y * h)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.5f,
                            center = Offset(pt.x * w, pt.y * h)
                        )
                    }
                }
            }
        }
    }
}
