package com.aiditor.app.ui.visualizers

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*

@Composable
fun OpticalFlowVisualizerView(
    data: ToolVisualizerData.OpticalFlow,
    isEnabled: Boolean = true,
    onToggleEnabled: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pulse animation for active vector field
    val infiniteTransition = rememberInfiniteTransition(label = "flow_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BwDarkSurface)
            .border(1.dp, if (isEnabled) Color(0xFF2E7D32) else BwCardStroke, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Master ON/OFF Switch Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isEnabled) Color(0xFF1B2E1D) else Color(0xFF1E1E22))
                .border(1.dp, if (isEnabled) Color(0xFF4CAF50) else Color(0xFF2A2A30), RoundedCornerShape(8.dp))
                .clickable { onToggleEnabled(!isEnabled) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) Color(0xFF4CAF50) else Color(0xFF777777))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "OPTICAL FLOW SMOOTHING",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (isEnabled) "ACTIVE • 60/120 FPS Motion Interpolation" else "DISABLED • Tap switch to enable",
                        color = if (isEnabled) Color(0xFFA5D6A7) else Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50),
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Subheader
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOTION VECTOR ESTIMATION (${data.targetFps} FPS • ${data.mode.uppercase()})",
                color = if (isEnabled) Color.White else Color(0xFF888888),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${data.vectors.size} Vectors",
                color = if (isEnabled) Color(0xFFA5D6A7) else Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Vector Field Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(1.dp, if (isEnabled) Color(0xFF2E7D32) else BwGreyDark, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Grid lines
                val gridCols = 8
                val gridRows = 5
                for (c in 1 until gridCols) {
                    val x = (w / gridCols) * c
                    drawLine(
                        color = Color(0xFF181818),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
                for (r in 1 until gridRows) {
                    val y = (h / gridRows) * r
                    drawLine(
                        color = Color(0xFF181818),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                val vectorColor = if (isEnabled) {
                    Color(0xFF81C784).copy(alpha = pulseAlpha)
                } else {
                    Color(0xFF444444)
                }

                // Draw motion vector arrows
                data.vectors.forEach { vec ->
                    val startX = vec.x * w
                    val startY = vec.y * h
                    val endX = startX + vec.dx * w * 3.5f
                    val endY = startY + vec.dy * h * 3.5f

                    // Vector line
                    drawLine(
                        color = vectorColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isEnabled) 1.8f else 1.0f,
                        cap = StrokeCap.Round
                    )
                    // Arrowhead tip dot
                    drawCircle(
                        color = if (isEnabled) Color.White else Color(0xFF555555),
                        radius = if (isEnabled) 2.5f else 1.5f,
                        center = Offset(endX, endY)
                    )
                }
            }

            if (!isEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OPTICAL FLOW IS DISABLED\nTurn switch ON to activate 60 FPS interpolation",
                        color = Color(0xFFBBBBBB),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
