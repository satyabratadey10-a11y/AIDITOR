package com.aiditor.app.ui.visualizers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.CurveControlPoint
import com.aiditor.app.data.model.ToolVisualizerData
import com.aiditor.app.ui.theme.*
import kotlin.math.hypot

/**
 * Fully Interactive Bézier Speed Curve Editor.
 * Users can touch, drag, add, and delete control points (0.1x to 8.0x speed),
 * choose built-in velocity ramp presets, or sculpt their own custom curves.
 */
@Composable
fun SpeedRampVisualizerView(
    data: ToolVisualizerData.SpeedRamp,
    controlPoints: List<CurveControlPoint> = data.controlPoints,
    onControlPointsChanged: (List<CurveControlPoint>) -> Unit = {},
    selectedPreset: String = data.preset,
    onPresetSelected: (String) -> Unit = {},
    durationSeconds: Double = 2.0,
    modifier: Modifier = Modifier
) {
    val activePoints = remember(controlPoints) {
        if (controlPoints.isNotEmpty()) controlPoints
        else listOf(
            CurveControlPoint(0.0f, 1.0f),
            CurveControlPoint(0.35f, 0.2f),
            CurveControlPoint(0.7f, 2.5f),
            CurveControlPoint(1.0f, 1.0f)
        )
    }

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val maxSpeed = maxOf(4.0f, (activePoints.maxOfOrNull { it.speed } ?: 2.5f) * 1.25f)
    val minSpeed = 0.1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BwDarkSurface)
            .border(1.dp, BwCardStroke, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "BÉZIER VELOCITY CURVE",
                    color = BwWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2E7D32))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = selectedPreset.uppercase().replace("_", " "),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            val peakVal = activePoints.maxOfOrNull { it.speed } ?: 1.0f
            Text(
                text = "PEAK: ${String.format("%.1f", peakVal)}x",
                color = BwWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Touch and drag points to reshape speed curve • Tap curve to add point",
            color = BwGreyLight,
            fontSize = 10.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Bézier Curve Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BwBlack)
                .border(1.dp, BwGreyDark, RoundedCornerShape(8.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activePoints, maxSpeed) {
                        detectTapGestures { tapOffset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val hitRadius = 36.dp.toPx()

                            // Check if tapped near an existing point
                            var hitIndex: Int? = null
                            activePoints.forEachIndexed { idx, pt ->
                                val px = pt.time * w
                                val py = h - ((pt.speed - minSpeed) / (maxSpeed - minSpeed)) * h
                                if (hypot(tapOffset.x - px, tapOffset.y - py) <= hitRadius) {
                                    hitIndex = idx
                                }
                            }

                            if (hitIndex != null) {
                                selectedPointIndex = hitIndex
                            } else {
                                // Add a new point at tap position
                                val tapNormTime = (tapOffset.x / w).coerceIn(0.05f, 0.95f)
                                val tapSpeed = (minSpeed + (1f - tapOffset.y / h) * (maxSpeed - minSpeed))
                                    .coerceIn(0.1f, 8.0f)
                                val newPoints = (activePoints + CurveControlPoint(tapNormTime, tapSpeed))
                                    .sortedBy { it.time }
                                selectedPointIndex = newPoints.indexOfFirst { it.time == tapNormTime }
                                onPresetSelected("custom")
                                onControlPointsChanged(newPoints)
                            }
                        }
                    }
                    .pointerInput(activePoints, maxSpeed) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val hitRadius = 40.dp.toPx()

                                var closestIdx: Int? = null
                                var closestDist = Float.MAX_VALUE
                                activePoints.forEachIndexed { idx, pt ->
                                    val px = pt.time * w
                                    val py = h - ((pt.speed - minSpeed) / (maxSpeed - minSpeed)) * h
                                    val d = hypot(startOffset.x - px, startOffset.y - py)
                                    if (d <= hitRadius && d < closestDist) {
                                        closestDist = d
                                        closestIdx = idx
                                    }
                                }

                                if (closestIdx != null) {
                                    selectedPointIndex = closestIdx
                                    isDragging = true
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val curIdx = selectedPointIndex ?: return@detectDragGestures
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()

                                val currentPt = activePoints[curIdx]
                                val curPx = currentPt.time * w
                                val curPy = h - ((currentPt.speed - minSpeed) / (maxSpeed - minSpeed)) * h

                                val newPx = curPx + dragAmount.x
                                val newPy = curPy + dragAmount.y

                                // Clamping X
                                val newTime = when (curIdx) {
                                    0 -> 0.0f // First point locked at time = 0
                                    activePoints.size - 1 -> 1.0f // Last point locked at time = 1
                                    else -> {
                                        val prevTime = activePoints[curIdx - 1].time + 0.04f
                                        val nextTime = activePoints[curIdx + 1].time - 0.04f
                                        (newPx / w).coerceIn(prevTime, nextTime)
                                    }
                                }

                                // Clamping Y
                                val newSpeed = (minSpeed + (1f - newPy / h) * (maxSpeed - minSpeed))
                                    .coerceIn(0.1f, 8.0f)

                                val updatedList = activePoints.toMutableList()
                                updatedList[curIdx] = CurveControlPoint(newTime, newSpeed)
                                onPresetSelected("custom")
                                onControlPointsChanged(updatedList)
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                // Draw 1.0x Baseline
                val baselineY = h - ((1.0f - minSpeed) / (maxSpeed - minSpeed)) * h
                drawLine(
                    color = Color(0xFF333333),
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                // Render Bézier / Spline curve
                if (activePoints.size >= 2) {
                    val sorted = activePoints.sortedBy { it.time }
                    val curvePath = Path()
                    val fillPath = Path()

                    val firstX = sorted[0].time * w
                    val firstY = h - ((sorted[0].speed - minSpeed) / (maxSpeed - minSpeed)) * h

                    curvePath.moveTo(firstX, firstY)
                    fillPath.moveTo(firstX, h)
                    fillPath.lineTo(firstX, firstY)

                    // Spline through points
                    for (i in 0 until sorted.size - 1) {
                        val p0 = sorted[i]
                        val p1 = sorted[i + 1]

                        val x0 = p0.time * w
                        val y0 = h - ((p0.speed - minSpeed) / (maxSpeed - minSpeed)) * h
                        val x1 = p1.time * w
                        val y1 = h - ((p1.speed - minSpeed) / (maxSpeed - minSpeed)) * h

                        val cx = (x0 + x1) / 2f
                        curvePath.cubicTo(cx, y0, cx, y1, x1, y1)
                        fillPath.cubicTo(cx, y0, cx, y1, x1, y1)
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Draw translucent gradient under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw white Bézier stroke
                    drawPath(
                        path = curvePath,
                        color = Color.White,
                        style = Stroke(width = 3.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // Draw Draggable Control Points
                activePoints.forEachIndexed { i, pt ->
                    val cx = pt.time * w
                    val cy = h - ((pt.speed - minSpeed) / (maxSpeed - minSpeed)) * h
                    val isSel = selectedPointIndex == i

                    if (isSel) {
                        // Outer glowing touch ring
                        drawCircle(
                            color = Color(0xFF4CAF50).copy(alpha = 0.35f),
                            radius = 16.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color(0xFF4CAF50),
                            radius = 8.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                    }

                    // Inner point handle
                    drawCircle(color = BwBlack, radius = 6.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(
                        color = if (isSel) Color(0xFF4CAF50) else Color.White,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            // Real-time floating tooltip for selected point
            selectedPointIndex?.let { idx ->
                if (idx in activePoints.indices) {
                    val pt = activePoints[idx]
                    val timeSec = pt.time * durationSeconds
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1E2820))
                            .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "POINT $idx • ${String.format("%.2f", pt.speed)}x @ ${String.format("%.2f", timeSec)}s",
                            color = Color(0xFFA5D6A7),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Presets Chips Row
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(
                "hero_moment" to "Hero Moment",
                "bullet_time" to "Bullet Time",
                "montage" to "Montage",
                "flash_in" to "Flash In",
                "flash_out" to "Flash Out",
                "linear" to "Standard (1.0x)"
            )

            presets.forEach { (presetKey, label) ->
                val isSel = selectedPreset == presetKey
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) Color.White else Color(0xFF1F1F24))
                        .border(1.dp, if (isSel) Color.White else Color(0xFF2A2A30), RoundedCornerShape(8.dp))
                        .clickable {
                            onPresetSelected(presetKey)
                            val presetPoints = when (presetKey) {
                                "hero_moment" -> listOf(
                                    CurveControlPoint(0.0f, 1.0f),
                                    CurveControlPoint(0.35f, 0.2f),
                                    CurveControlPoint(0.70f, 3.5f),
                                    CurveControlPoint(1.0f, 1.0f)
                                )
                                "bullet_time" -> listOf(
                                    CurveControlPoint(0.0f, 1.0f),
                                    CurveControlPoint(0.30f, 0.1f),
                                    CurveControlPoint(0.70f, 0.1f),
                                    CurveControlPoint(1.0f, 1.0f)
                                )
                                "montage" -> listOf(
                                    CurveControlPoint(0.0f, 0.5f),
                                    CurveControlPoint(0.25f, 2.5f),
                                    CurveControlPoint(0.50f, 0.5f),
                                    CurveControlPoint(0.75f, 2.5f),
                                    CurveControlPoint(1.0f, 1.0f)
                                )
                                "flash_in" -> listOf(
                                    CurveControlPoint(0.0f, 4.0f),
                                    CurveControlPoint(0.30f, 1.0f),
                                    CurveControlPoint(1.0f, 1.0f)
                                )
                                "flash_out" -> listOf(
                                    CurveControlPoint(0.0f, 1.0f),
                                    CurveControlPoint(0.70f, 1.0f),
                                    CurveControlPoint(1.0f, 4.0f)
                                )
                                else -> listOf(
                                    CurveControlPoint(0.0f, 1.0f),
                                    CurveControlPoint(1.0f, 1.0f)
                                )
                            }
                            selectedPointIndex = null
                            onControlPointsChanged(presetPoints)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.Black else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Curve Point Toolbar: + Point, - Remove Point, Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + Add Point
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222228))
                    .border(1.dp, Color(0xFF33333E), RoundedCornerShape(6.dp))
                    .clickable {
                        // Find middle time
                        val newTime = 0.5f
                        val newPoints = (activePoints + CurveControlPoint(newTime, 2.0f))
                            .sortedBy { it.time }
                        selectedPointIndex = newPoints.indexOfFirst { it.time == newTime }
                        onPresetSelected("custom")
                        onControlPointsChanged(newPoints)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+ Add Point", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // - Delete Point
            val canDelete = selectedPointIndex != null &&
                    selectedPointIndex != 0 &&
                    selectedPointIndex != activePoints.size - 1 &&
                    activePoints.size > 2
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (canDelete) Color(0xFF2A1C1C) else Color(0xFF18181A))
                    .border(1.dp, if (canDelete) Color(0xFF7F2D2D) else Color(0xFF2A2A30), RoundedCornerShape(6.dp))
                    .clickable(enabled = canDelete) {
                        val idx = selectedPointIndex ?: return@clickable
                        val updated = activePoints.toMutableList().apply { removeAt(idx) }
                        selectedPointIndex = null
                        onPresetSelected("custom")
                        onControlPointsChanged(updated)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "- Delete Point",
                    color = if (canDelete) Color(0xFFFF8A80) else Color(0xFF666666),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ↺ Reset to Linear (1.0x)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF222228))
                    .border(1.dp, Color(0xFF33333E), RoundedCornerShape(6.dp))
                    .clickable {
                        val flat = listOf(
                            CurveControlPoint(0.0f, 1.0f),
                            CurveControlPoint(1.0f, 1.0f)
                        )
                        selectedPointIndex = null
                        onPresetSelected("linear")
                        onControlPointsChanged(flat)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("↺ Reset", color = Color(0xFFAAAAAA), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
