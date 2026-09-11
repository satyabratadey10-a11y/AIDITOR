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
 * Tool Inspector Sheet providing COMPLETE ACCESS to modify:
 * 1. INPUT PART (Source trim in/out, stream, mute)
 * 2. MIDDLE PART (Algorithm parameters, interactive Bézier curve, optical flow switch, color presets)
 * 3. OUTPUT PART (Resolution, target FPS, codec)
 * Along with REAL INTERACTIVE VISUALIZERS for all tools!
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
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ==========================================
        // 1. INPUT MODIFICATION PART
        // ==========================================
        Text(
            text = "1. INPUT MODIFICATION",
            color = BwWhite,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        BwSlider(
            label = "Trim In-Point (Seconds)",
            value = inputParams.inPointSeconds.toFloat(),
            onValueChange = { onUpdateInput(inputParams.copy(inPointSeconds = it.toDouble())) },
            valueRange = 0f..10f,
            formattedValue = String.format("%.2fs", inputParams.inPointSeconds)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mute Audio Stream", color = BwGreyLight, fontSize = 12.sp)
            Switch(
                checked = inputParams.muteAudio,
                onCheckedChange = { onUpdateInput(inputParams.copy(muteAudio = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BwBlack,
                    checkedTrackColor = BwWhite,
                    uncheckedThumbColor = BwWhite,
                    uncheckedTrackColor = BwCardBackground
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 2. MIDDLE PROCESSING PART
        // ==========================================
        Text(
            text = "2. MIDDLE ALGORITHM PARAMETERS",
            color = BwWhite,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (middleParams) {
            is MiddleParameters.OpticalFlow -> {
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
            }
            is MiddleParameters.BeatSync -> {
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
            is MiddleParameters.MotionTracking -> {
                BwSlider(
                    label = "Target Coordinate X",
                    value = middleParams.targetX,
                    onValueChange = { onUpdateMiddle(middleParams.copy(targetX = it)) },
                    valueRange = 0.1f..0.9f
                )
                BwSlider(
                    label = "Target Coordinate Y",
                    value = middleParams.targetY,
                    onValueChange = { onUpdateMiddle(middleParams.copy(targetY = it)) },
                    valueRange = 0.1f..0.9f
                )
            }
            is MiddleParameters.SpeedRamp -> {
                BwSlider(
                    label = "Peak Speed Multiplier",
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
            is MiddleParameters.ColorGrade -> {
                // Preset Filter Selector
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

                // Sliders
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
            is MiddleParameters.Rotoscope -> {
                Text("Rotoscope Preset", color = BwGreyLight, fontSize = 12.sp)
                val presets = listOf("behind_text", "neon_saber", "dual_tone")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    presets.forEach { p ->
                        val isSel = middleParams.preset == p
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BwWhite else BwCardBackground)
                                .clickable { onUpdateMiddle(middleParams.copy(preset = p)) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(p.replace("_", " "), color = if (isSel) BwBlack else BwWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ==========================================
        // 3. OUTPUT CONFIGURATION PART
        // ==========================================
        Text(
            text = "3. OUTPUT CONFIGURATION & RENDER",
            color = BwWhite,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Codec: ${outputParams.codec} • ${outputParams.resolution}", color = BwGreyLight, fontSize = 12.sp)
            Text("CRF: ${outputParams.crf}", color = BwWhite, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            BwButton(
                text = "APPLY TO TIMELINE",
                onClick = onApplyToTimeline,
                iconRes = R.drawable.ic_check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
