package com.aiditor.app.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 2. MIDDLE PART (Algorithm parameters)
 * 3. OUTPUT PART (Resolution, target FPS, codec)
 * Along with REAL VISUALIZERS for all 6 tools!
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
                is ToolVisualizerData.OpticalFlow -> OpticalFlowVisualizerView(data = visualizerData)
                is ToolVisualizerData.BeatSync -> BeatSyncVisualizerView(data = visualizerData)
                is ToolVisualizerData.MotionTracking -> MotionTrackingVisualizerView(data = visualizerData)
                is ToolVisualizerData.SpeedRamp -> SpeedRampVisualizerView(data = visualizerData)
                is ToolVisualizerData.ColorGrade -> ColorGradeVisualizerView(data = visualizerData)
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
                Text("Target Frame Rate: ${middleParams.targetFps} FPS", color = BwGreyLight, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(60, 120).forEach { fps ->
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
                    onValueChange = { onUpdateMiddle(middleParams.copy(maxSpeedMultiplier = it)) },
                    valueRange = 1.2f..5.0f,
                    formattedValue = String.format("%.1fx", middleParams.maxSpeedMultiplier)
                )
                BwSlider(
                    label = "Ramp Duration (Seconds)",
                    value = middleParams.durationSeconds.toFloat(),
                    onValueChange = { onUpdateMiddle(middleParams.copy(durationSeconds = it.toDouble())) },
                    valueRange = 1.0f..6.0f,
                    formattedValue = String.format("%.1fs", middleParams.durationSeconds)
                )
            }
            is MiddleParameters.ColorGrade -> {
                BwSlider(
                    label = "Contrast Multiplier",
                    value = middleParams.contrast,
                    onValueChange = { onUpdateMiddle(middleParams.copy(contrast = it)) },
                    valueRange = 0.5f..2.5f
                )
                BwSlider(
                    label = "Exposure Adjustment",
                    value = middleParams.exposure,
                    onValueChange = { onUpdateMiddle(middleParams.copy(exposure = it)) },
                    valueRange = -2.0f..2.0f
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
