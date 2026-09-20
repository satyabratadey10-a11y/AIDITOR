package com.aiditor.app.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.*
import com.aiditor.app.ui.components.BwButton
import com.aiditor.app.ui.components.BwIconButton
import com.aiditor.app.ui.components.BwSlider
import com.aiditor.app.ui.theme.*
import com.aiditor.app.ui.visualizers.*

/**
 * Modularized Tool Inspector Sheet.
 * Divided into discrete, lightweight composables to strictly conform to Android ART JIT compiler limits
 * and eliminate bytecode method size warnings while maximizing runtime rendering performance.
 */
@Composable
fun ToolInspectorSheet(
    toolType: ToolType,
    visualizerData: ToolVisualizerData?,
    inputParams: InputParameters,
    onUpdateInput: (InputParameters) -> Unit,
    middleParams: MiddleParameters,
    onUpdateMiddle: (MiddleParameters) -> Unit,
    outputParams: OutputParameters,
    onUpdateOutput: (OutputParameters) -> Unit,
    onClose: () -> Unit,
    onApplyToTimeline: () -> Unit,
    onRenderOpticalFlow: () -> Unit = {},
    onCancelOpticalFlow: () -> Unit = {},
    onRenderRotoscope: () -> Unit = {},
    onCancelRotoscope: () -> Unit = {},
    onStartMotionTracking: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(BwDarkSurface)
            .border(1.dp, BwCardStroke, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BwWhite)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${toolType.title.uppercase()} INSPECTOR",
                    color = BwWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            BwIconButton(
                iconRes = R.drawable.ic_close,
                onClick = onClose,
                contentDescription = "Close Inspector",
                size = 30.dp,
                iconSize = 16.dp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // REAL VISUALIZER SECTION
        if (visualizerData != null) {
            ToolVisualizerSection(
                visualizerData = visualizerData,
                middleParams = middleParams,
                onUpdateMiddle = onUpdateMiddle
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Tool-specific focused algorithm parameters
        when (middleParams) {
            is MiddleParameters.OpticalFlow -> {
                OpticalFlowControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle,
                    onRenderOpticalFlow = onRenderOpticalFlow,
                    onCancelOpticalFlow = onCancelOpticalFlow
                )
            }
            is MiddleParameters.BeatSync -> {
                BeatSyncControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle
                )
            }
            is MiddleParameters.MotionTracking -> {
                MotionTrackingControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle,
                    onStartMotionTracking = onStartMotionTracking
                )
            }
            is MiddleParameters.SpeedRamp -> {
                SpeedRampControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle
                )
            }
            is MiddleParameters.ColorGrade -> {
                ColorGradeControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle
                )
            }
            is MiddleParameters.Rotoscope -> {
                RotoscopeControlsSection(
                    middleParams = middleParams,
                    onUpdateMiddle = onUpdateMiddle,
                    onRenderRotoscope = onRenderRotoscope,
                    onCancelRotoscope = onCancelRotoscope
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Button: DONE / START TRACKING
        val doneBtnText = when (middleParams) {
            is MiddleParameters.MotionTracking -> {
                if (middleParams.isTrackingRunning) "TRACKING IN PROGRESS..."
                else if (middleParams.isTrackingDone) "DONE"
                else "START TRACKING & DONE"
            }
            else -> "DONE"
        }
        val doneBtnEnabled = when (middleParams) {
            is MiddleParameters.MotionTracking -> !middleParams.isTrackingRunning
            else -> true
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            BwButton(
                text = doneBtnText,
                onClick = onApplyToTimeline,
                iconRes = R.drawable.ic_check,
                enabled = doneBtnEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToolVisualizerSection(
    visualizerData: ToolVisualizerData,
    middleParams: MiddleParameters,
    onUpdateMiddle: (MiddleParameters) -> Unit
) {
    when (visualizerData) {
        is ToolVisualizerData.OpticalFlow -> {
            val flowMiddle = middleParams as? MiddleParameters.OpticalFlow ?: MiddleParameters.OpticalFlow()
            OpticalFlowVisualizerView(
                data = visualizerData,
                isEnabled = flowMiddle.isEnabled,
                onToggleEnabled = { enabled ->
                    onUpdateMiddle(flowMiddle.copy(isEnabled = enabled))
                }
            )
        }
        is ToolVisualizerData.BeatSync -> BeatSyncVisualizerView(data = visualizerData)
        is ToolVisualizerData.MotionTracking -> MotionTrackingVisualizerView(data = visualizerData)
        is ToolVisualizerData.SpeedRamp -> {
            val rampMiddle = middleParams as? MiddleParameters.SpeedRamp ?: MiddleParameters.SpeedRamp()
            SpeedRampVisualizerView(
                data = visualizerData,
                controlPoints = rampMiddle.curveControlPoints,
                onControlPointsChanged = { updatedPoints ->
                    val peak = updatedPoints.maxOfOrNull { it.speed } ?: rampMiddle.maxSpeedMultiplier
                    onUpdateMiddle(rampMiddle.copy(curveControlPoints = updatedPoints, maxSpeedMultiplier = peak))
                },
                selectedPreset = rampMiddle.preset,
                onPresetSelected = { newPreset ->
                    onUpdateMiddle(rampMiddle.copy(preset = newPreset))
                },
                durationSeconds = rampMiddle.durationSeconds
            )
        }
        is ToolVisualizerData.ColorGrade -> {
            val gradeMiddle = middleParams as? MiddleParameters.ColorGrade ?: MiddleParameters.ColorGrade()
            ColorGradeVisualizerView(
                data = visualizerData,
                filterPreset = gradeMiddle.filterPreset
            )
        }
        is ToolVisualizerData.Rotoscope -> RotoscopeVisualizerView(data = visualizerData)
    }
}

@Composable
private fun OpticalFlowControlsSection(
    middleParams: MiddleParameters.OpticalFlow,
    onUpdateMiddle: (MiddleParameters) -> Unit,
    onRenderOpticalFlow: () -> Unit,
    onCancelOpticalFlow: () -> Unit
) {
    // Prominent Master Switch Card
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (middleParams.isEnabled) Color(0xFF1B2E1D) else Color(0xFF1C1C20)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (middleParams.isEnabled) Color(0xFF4CAF50) else BwCardStroke, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Optical Flow Interpolation",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (middleParams.isEnabled) "Status: ACTIVE (${middleParams.targetFps} FPS Smooth)" else "Status: OFF • Tap to activate",
                    color = if (middleParams.isEnabled) Color(0xFFA5D6A7) else BwGreyLight,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = middleParams.isEnabled,
                onCheckedChange = { onUpdateMiddle(middleParams.copy(isEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50),
                    uncheckedThumbColor = BwGreyLight,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Target FPS
    Text("Target Frame Rate", color = BwGreyLight, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        listOf(60, 120, 240).forEach { fps ->
            val isSel = middleParams.targetFps == fps
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) BwWhite else BwCardBackground)
                    .clickable { onUpdateMiddle(middleParams.copy(targetFps = fps)) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("${fps} FPS", color = if (isSel) BwBlack else BwWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Smooth Slow-Motion Factor
    Text("Slow-Motion Smoothing Factor", color = BwGreyLight, fontSize = 12.sp)
    val slowMoFactors = listOf(
        1.0f to "1.0x (Smooth)",
        0.5f to "2x Slow (0.5x)",
        0.25f to "4x Slow (0.25x)",
        0.125f to "8x Slow (0.125x)"
    )
    val slowMoScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(slowMoScroll)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slowMoFactors.forEach { (factor, label) ->
            val isSel = middleParams.slowMoFactor == factor
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color.White else BwCardBackground)
                    .clickable { onUpdateMiddle(middleParams.copy(slowMoFactor = factor)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Interpolation Algorithm
    Text("Motion Vector Method", color = BwGreyLight, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        listOf("mci" to "MCI (Motion Compensated)", "blend" to "Frame Blend").forEach { (mode, label) ->
            val isSel = middleParams.flowMode == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) BwWhite else BwCardBackground)
                    .clickable { onUpdateMiddle(middleParams.copy(flowMode = mode)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(label, color = if (isSel) BwBlack else BwWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    BwSlider(
        label = "Scene Cut Threshold (SCD)",
        value = middleParams.scdThreshold.toFloat(),
        onValueChange = { onUpdateMiddle(middleParams.copy(scdThreshold = it.toDouble())) },
        valueRange = 1f..30f
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Action Card: Render & Cache 60 FPS
    if (middleParams.isRendering) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D17)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYNTHESIZING 60 FPS FRAMES...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(middleParams.renderProgress * 100).toInt()}%",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { middleParams.renderProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF2E3B2F)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = middleParams.renderStatusMessage,
                    color = BwGreyLight,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCancelOpticalFlow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E1E1E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "CANCEL RENDERING",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else if (!middleParams.cachedVideoUri.isNullOrBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF142416)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✓ 60 FPS CACHED & ACTIVE",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ExoPlayer is playing true 60 FPS interpolated video",
                        color = BwGreyLight,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRenderOpticalFlow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "RE-RENDER",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Button(
            onClick = onRenderOpticalFlow,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text(
                text = "▶ RENDER & CACHE 60 FPS VIDEO",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun BeatSyncControlsSection(
    middleParams: MiddleParameters.BeatSync,
    onUpdateMiddle: (MiddleParameters) -> Unit
) {
    Text("Rhythm Vibe Style", color = BwGreyLight, fontSize = 12.sp)
    val vibes = listOf("aggressive_drift", "chill_neon", "speed_ramp_chaos")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        vibes.forEach { v ->
            val isSel = middleParams.vibe == v
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) BwWhite else BwCardBackground)
                    .clickable { onUpdateMiddle(middleParams.copy(vibe = v)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(v.substringBefore("_"), color = if (isSel) BwBlack else BwWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    BwSlider(
        label = "Beat Sensitivity",
        value = middleParams.beatSensitivity.toFloat(),
        onValueChange = { onUpdateMiddle(middleParams.copy(beatSensitivity = it.toDouble())) },
        valueRange = 0.2f..1.0f
    )
}

@Composable
private fun MotionTrackingControlsSection(
    middleParams: MiddleParameters.MotionTracking,
    onUpdateMiddle: (MiddleParameters) -> Unit,
    onStartMotionTracking: () -> Unit = {}
) {
    // Tracking Action Status & Trigger Card
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (middleParams.isTrackingDone) Color(0xFF142E1F) else Color(0xFF1C1C20)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (middleParams.isTrackingDone) Color(0xFF2E7D32) else BwCardStroke,
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MOTION TRACKER EXECUTION",
                        color = BwWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (middleParams.isTrackingRunning) "Tracking frames & propagating silhouette contour..."
                        else if (middleParams.isTrackingDone) "Active on Track 2 • Silhouette Outliner Locked"
                        else "Tap subject on preview to lock silhouette outline",
                        color = if (middleParams.isTrackingDone) Color(0xFFA5D6A7) else BwGreyLight,
                        fontSize = 10.sp
                    )
                }
            }

            // Subject Outliner Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF14241B))
                    .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Text(
                        text = "SMART SUBJECT OUTLINER",
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "${middleParams.subjectContour.size.coerceAtLeast(24)} CONTOUR NODES",
                    color = Color(0xFFA5D6A7),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (middleParams.isTrackingRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { middleParams.trackingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF00E676),
                        trackColor = Color(0xFF333333)
                    )
                    Text(
                        text = "PROGRESS: ${(middleParams.trackingProgress * 100).toInt()}%",
                        color = Color(0xFF00E676),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                BwButton(
                    text = if (middleParams.isTrackingDone) "RE-RUN MOTION TRACKING" else "START MOTION TRACKING",
                    onClick = onStartMotionTracking,
                    iconRes = R.drawable.ic_play,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text("Tracking & Stabilization Mode", color = BwGreyLight, fontSize = 12.sp)
    val trackModes = listOf(
        "hud_callout" to "HUD Callout",
        "target_lock" to "Target Lock (Stabilize)",
        "point_track" to "Point Track"
    )
    val modeScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(modeScroll)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        trackModes.forEach { (mode, label) ->
            val isSel = middleParams.trackingMode == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color.White else BwCardBackground)
                    .border(1.dp, if (isSel) Color.White else Color(0xFF2E2E36), RoundedCornerShape(8.dp))
                .clickable {
                    val isLock = (mode == "target_lock")
                    onUpdateMiddle(middleParams.copy(trackingMode = mode, isTargetLockActive = isLock))
                }
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (middleParams.isTargetLockActive) Color(0xFF1B2E1D) else Color(0xFF1C1C20)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (middleParams.isTargetLockActive) Color(0xFF4CAF50) else BwCardStroke, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Target Lock Viewport Centering",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (middleParams.isTargetLockActive) "Locks subject to screen center (0.5, 0.5) with dynamic zoom" else "Free tracking bounding reticle",
                    color = if (middleParams.isTargetLockActive) Color(0xFFA5D6A7) else BwGreyLight,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = middleParams.isTargetLockActive,
                onCheckedChange = { active ->
                    onUpdateMiddle(middleParams.copy(
                        isTargetLockActive = active,
                        trackingMode = if (active) "target_lock" else "hud_callout"
                    ))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50),
                    uncheckedThumbColor = BwGreyLight,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    BwSlider(
        label = "Target Center X",
        value = middleParams.targetX,
        onValueChange = { onUpdateMiddle(middleParams.copy(targetX = it)) },
        valueRange = 0.05f..0.95f,
        formattedValue = String.format("%.2f", middleParams.targetX)
    )
    BwSlider(
        label = "Target Center Y",
        value = middleParams.targetY,
        onValueChange = { onUpdateMiddle(middleParams.copy(targetY = it)) },
        valueRange = 0.05f..0.95f,
        formattedValue = String.format("%.2f", middleParams.targetY)
    )
    BwSlider(
        label = "Reticle Width",
        value = middleParams.boxWidth,
        onValueChange = { onUpdateMiddle(middleParams.copy(boxWidth = it)) },
        valueRange = 0.05f..0.5f,
        formattedValue = String.format("%.2f", middleParams.boxWidth)
    )
    BwSlider(
        label = "Reticle Height",
        value = middleParams.boxHeight,
        onValueChange = { onUpdateMiddle(middleParams.copy(boxHeight = it)) },
        valueRange = 0.05f..0.5f,
        formattedValue = String.format("%.2f", middleParams.boxHeight)
    )
    BwSlider(
        label = "Kalman Smooth Factor",
        value = middleParams.smoothFactor,
        onValueChange = { onUpdateMiddle(middleParams.copy(smoothFactor = it)) },
        valueRange = 0.1f..0.99f,
        formattedValue = String.format("%.2f", middleParams.smoothFactor)
    )
}

@Composable
private fun SpeedRampControlsSection(
    middleParams: MiddleParameters.SpeedRamp,
    onUpdateMiddle: (MiddleParameters) -> Unit
) {
    Text("Quick Speed Presets", color = BwGreyLight, fontSize = 12.sp)
    val speedPresets = listOf(0.2f, 0.5f, 1.0f, 2.0f, 4.0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        speedPresets.forEach { sp ->
            val isSel = kotlin.math.abs(middleParams.maxSpeedMultiplier - sp) < 0.05f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color.White else Color(0xFF1E1E24))
                    .border(1.dp, if (isSel) Color.White else Color(0xFF2E2E36), RoundedCornerShape(8.dp))
                    .clickable {
                        val updatedPts = middleParams.curveControlPoints.map { pt ->
                            if (pt.speed > 1.0f || sp < 1.0f) pt.copy(speed = sp) else pt
                        }
                        onUpdateMiddle(middleParams.copy(maxSpeedMultiplier = sp, curveControlPoints = updatedPts))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${sp}x",
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    BwSlider(
        label = "Custom Speed Multiplier",
        value = middleParams.maxSpeedMultiplier,
        onValueChange = { newMax ->
            val updatedPts = middleParams.curveControlPoints.map { pt ->
                if (pt.speed > 1.0f) pt.copy(speed = newMax) else pt
            }
            onUpdateMiddle(middleParams.copy(maxSpeedMultiplier = newMax, curveControlPoints = updatedPts))
        },
        valueRange = 1.0f..8.0f,
        formattedValue = String.format("%.1fx", middleParams.maxSpeedMultiplier)
    )
    BwSlider(
        label = "Ramp Duration (Seconds)",
        value = middleParams.durationSeconds.toFloat(),
        onValueChange = { onUpdateMiddle(middleParams.copy(durationSeconds = it.toDouble())) },
        valueRange = 0.5f..10.0f,
        formattedValue = String.format("%.1fs", middleParams.durationSeconds)
    )
}

@Composable
private fun ColorGradeControlsSection(
    middleParams: MiddleParameters.ColorGrade,
    onUpdateMiddle: (MiddleParameters) -> Unit
) {
    Text("Cinematic Filter Presets", color = BwGreyLight, fontSize = 12.sp)
    val filterPresets = listOf(
        "original" to "Original",
        "bw_cinema" to "B&W Cinema",
        "cyberpunk_cool" to "Cyberpunk",
        "warm_gold" to "Warm Gold",
        "vintage_90s" to "Vintage 90s",
        "noir_dark" to "Noir Dark",
        "vibrant_punch" to "Vibrant"
    )
    val filterScrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(filterScrollState)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterPresets.forEach { (key, title) ->
            val isSel = middleParams.filterPreset == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color.White else Color(0xFF1E1E24))
                    .border(1.dp, if (isSel) Color.White else Color(0xFF2E2E36), RoundedCornerShape(8.dp))
                    .clickable {
                        val updated = when (key) {
                            "bw_cinema" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 1.35f,
                                saturation = 0.0f,
                                brightness = -0.05f
                            )
                            "cyberpunk_cool" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 1.30f,
                                saturation = 1.25f,
                                brightness = 0.05f
                            )
                            "warm_gold" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 1.15f,
                                saturation = 1.10f,
                                brightness = 0.08f
                            )
                            "vintage_90s" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 0.95f,
                                saturation = 0.70f,
                                brightness = 0.04f
                            )
                            "noir_dark" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 1.60f,
                                saturation = 0.15f,
                                brightness = -0.12f
                            )
                            "vibrant_punch" -> middleParams.copy(
                                filterPreset = key,
                                contrast = 1.25f,
                                saturation = 1.50f,
                                brightness = 0.0f
                            )
                            else -> middleParams.copy(
                                filterPreset = "original",
                                contrast = 1.0f,
                                saturation = 1.0f,
                                brightness = 0.0f
                            )
                        }
                        onUpdateMiddle(updated)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = title,
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    BwSlider(
        label = "Contrast Multiplier",
        value = middleParams.contrast,
        onValueChange = { onUpdateMiddle(middleParams.copy(contrast = it, filterPreset = "custom")) },
        valueRange = 0.5f..2.5f,
        formattedValue = String.format("%.2fx", middleParams.contrast)
    )
    BwSlider(
        label = "Saturation (0.0 = Pure B&W)",
        value = middleParams.saturation,
        onValueChange = { onUpdateMiddle(middleParams.copy(saturation = it, filterPreset = "custom")) },
        valueRange = 0.0f..2.0f,
        formattedValue = String.format("%.2fx", middleParams.saturation)
    )
    BwSlider(
        label = "Brightness Level",
        value = middleParams.brightness,
        onValueChange = { onUpdateMiddle(middleParams.copy(brightness = it, filterPreset = "custom")) },
        valueRange = -0.5f..0.5f,
        formattedValue = String.format("%+.2f", middleParams.brightness)
    )
    BwSlider(
        label = "Exposure Adjustment",
        value = middleParams.exposure,
        onValueChange = { onUpdateMiddle(middleParams.copy(exposure = it, filterPreset = "custom")) },
        valueRange = -2.0f..2.0f,
        formattedValue = String.format("%+.1f EV", middleParams.exposure)
    )
}

@Composable
private fun RotoscopeControlsSection(
    middleParams: MiddleParameters.Rotoscope,
    onUpdateMiddle: (MiddleParameters) -> Unit,
    onRenderRotoscope: () -> Unit,
    onCancelRotoscope: () -> Unit
) {
    Text("AI Cutout & FX Preset", color = BwGreyLight, fontSize = 12.sp)
    val presets = listOf(
        "neon_saber" to "Neon Saber",
        "cyberpunk_glow" to "Cyberpunk",
        "behind_text" to "Behind Text",
        "silhouette" to "Silhouette"
    )
    val presetScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(presetScroll)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (p, label) ->
            val isSel = middleParams.preset == p
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) Color.White else BwCardBackground)
                    .border(1.dp, if (isSel) Color.White else Color(0xFF2E2E36), RoundedCornerShape(8.dp))
                    .clickable { onUpdateMiddle(middleParams.copy(preset = p)) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (isSel) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Neon Color Palette
    Text("Neon Glow Color", color = BwGreyLight, fontSize = 12.sp)
    val neonPalette = listOf(
        "#00F0FF" to "Cyan",
        "#FF007F" to "Pink",
        "#39FF14" to "Lime",
        "#FFE600" to "Yellow",
        "#FFFFFF" to "White"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        neonPalette.forEach { (hex, _) ->
            val isSel = middleParams.neonColor.equals(hex, ignoreCase = true)
            val colorVal = try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (e: Exception) {
                Color.Cyan
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colorVal)
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) Color.White else Color(0x66FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onUpdateMiddle(middleParams.copy(neonColor = hex)) }
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    BwSlider(
        label = "Neon Outline Width",
        value = middleParams.outlineWidth,
        onValueChange = { onUpdateMiddle(middleParams.copy(outlineWidth = it)) },
        valueRange = 1f..12f,
        formattedValue = String.format("%.1f px", middleParams.outlineWidth)
    )
    BwSlider(
        label = "Glow Bloom Intensity",
        value = middleParams.glowIntensity,
        onValueChange = { onUpdateMiddle(middleParams.copy(glowIntensity = it)) },
        valueRange = 0.5f..3.0f,
        formattedValue = String.format("%.2fx", middleParams.glowIntensity)
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Action Card: Render & Cache AI Cutout
    if (middleParams.isRendering) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D17)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYNTHESIZING TEMPORAL CUTOUT...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${(middleParams.renderProgress * 100).toInt()}%",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { middleParams.renderProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF2E3B2F)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = middleParams.renderStatusMessage,
                    color = BwGreyLight,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onCancelRotoscope,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E1E1E)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "CANCEL RENDERING",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else if (!middleParams.cachedMaskUri.isNullOrBlank()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF142416)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "✓ CUTOUT CACHED & ACTIVE",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Matte overlay: ${middleParams.preset.uppercase()}",
                        color = BwGreyLight,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRenderRotoscope,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "RE-RENDER",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Button(
            onClick = onRenderRotoscope,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Text(
                text = "▶ RENDER & CACHE CUTOUT",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

