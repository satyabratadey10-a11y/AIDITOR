package com.aiditor.app.ui.screens.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiditor.app.data.model.ActiveTrackingMode
import com.aiditor.app.data.model.MiddleParameters
import com.aiditor.app.data.model.OverlayType
import com.aiditor.app.data.model.Project
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.components.BwTopBar
import com.aiditor.app.ui.components.ExportDialog
import com.aiditor.app.ui.components.ExportProgressDialog
import com.aiditor.app.ui.components.LogcatViewerDialog
import com.aiditor.app.ui.theme.BwBlack
import com.aiditor.app.util.VideoPickerHelper

/**
 * Screen 2: Video Editing Workspace.
 * 100% Standalone On-Device Video Editor.
 * Layout strictly matching CapCut / VN reference images:
 * - TopBar: Back button, Aspect Ratio Dropdown ("Original v" / "1:1 v"), Export icon
 * - Video Preview: Scaled video with HUD overlays (Motion Tracking, Stabilization, Face Tracking)
 * - Multi-Track Timeline: Transport bar, dynamic time ruler, left track headers, free multi-tracks, centered playhead
 * - Bottom Action Toolbar: Exact tools (Split, Delete, Duplicate, Replace, Image, Edit, Tune, Speed, Track, Clear)
 * - Docked Tool Inspector with real visualizers
 */
@Composable
fun WorkspaceScreen(
    project: Project,
    viewModel: WorkspaceViewModel,
    onBackToMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(project.id) {
        viewModel.loadProject(project)
    }

    val uiState by viewModel.uiState.collectAsState()
    var showLogcatDialog by remember { mutableStateOf(false) }

    // Replace video launcher
    val replacePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.replaceSelectedClip(uri.toString())
        }
    }

    // Add image/overlay launcher
    val addImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.addStickerOverlay(OverlayType.SKULL_STICKER)
    }

    Scaffold(
        containerColor = Color(0xFF0F0F11),
        topBar = {
            BwTopBar(
                title = uiState.project?.name ?: "WORKSPACE",
                onBackClick = onBackToMainMenu,
                onExportClick = { viewModel.showExportDialog(true) },
                aspectRatio = uiState.aspectRatio,
                onSelectAspectRatio = { viewModel.setAspectRatio(it) },
                onLogsClick = { showLogcatDialog = true }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416))
                    .navigationBarsPadding()
            ) {
                // If a tool is active, display the Tool Inspector with Real Visualizer
                if (uiState.activeTool != null) {
                    ToolInspectorSheet(
                        toolType = uiState.activeTool!!,
                        visualizerData = uiState.activeVisualizerData,
                        inputParams = uiState.inputParams,
                        onUpdateInput = { viewModel.updateInputParams(it) },
                        middleParams = uiState.middleParams,
                        onUpdateMiddle = { viewModel.updateMiddleParams(it) },
                        outputParams = uiState.outputParams,
                        onUpdateOutput = { viewModel.updateOutputParams(it) },
                        onClose = { viewModel.closeToolInspector() },
                        onApplyToTimeline = { viewModel.applyCurrentToolToTimeline() },
                        modifier = Modifier.heightIn(max = 300.dp)
                    )
                }

                // Bottom feature/tool list
                BottomToolBar(
                    onBack = onBackToMainMenu,
                    onSplit = { viewModel.splitClipAtPlayhead() },
                    onDelete = { viewModel.deleteSelectedClip() },
                    onDuplicate = { viewModel.duplicateSelectedClip() },
                    onReplace = {
                        try {
                            replacePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        } catch (_: Exception) {}
                    },
                    onAddImage = {
                        viewModel.addStickerOverlay(OverlayType.SKULL_STICKER)
                    },
                    onOpticalFlow = {
                        viewModel.selectTool(ToolType.OPTICAL_FLOW)
                    },
                    onTune = {
                        viewModel.selectTool(ToolType.COLOR_GRADE)
                    },
                    onSpeed = {
                        viewModel.selectTool(ToolType.SPEED_RAMP)
                    },
                    onTrack = {
                        // Cycles through Motion Tracking -> Stabilization -> Face Tracking -> None
                        val nextMode = when (uiState.trackingMode) {
                            ActiveTrackingMode.NONE -> ActiveTrackingMode.MOTION_TRACKING
                            ActiveTrackingMode.MOTION_TRACKING -> ActiveTrackingMode.MOTION_STABILIZATION
                            ActiveTrackingMode.MOTION_STABILIZATION -> ActiveTrackingMode.FACE_TRACKING
                            ActiveTrackingMode.FACE_TRACKING -> ActiveTrackingMode.NONE
                        }
                        viewModel.setTrackingMode(nextMode)
                    },
                    onClear = {
                        viewModel.clearActiveTracking()
                    },
                    activeTool = uiState.activeTool,
                    isOpticalFlowActive = uiState.clips.find { it.isSelected }?.isOpticalFlowEnabled == true ||
                            (uiState.middleParams as? MiddleParameters.OpticalFlow)?.isEnabled == true,
                    trackingMode = uiState.trackingMode
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0D0D0E)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. UPPER SECTION: Video Preview Screen with HUD Overlays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VideoPreviewSection(
                    currentTimeSeconds = uiState.currentTimeSeconds,
                    totalDurationSeconds = uiState.totalDurationSeconds,
                    isPlaying = uiState.isPlaying,
                    isAudioMuted = uiState.isAudioMuted,
                    aspectRatio = uiState.aspectRatio,
                    trackingMode = uiState.trackingMode,
                    activeTool = uiState.activeTool,
                    middleParams = uiState.middleParams,
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onTimeUpdate = { viewModel.onPlaybackTimeUpdate(it) },
                    videoPath = uiState.project?.videoPath
                )
            }

            // 2. CENTER-TO-BOTTOM: Multi-Track Timeline
            TimelineSection(
                currentTimeSeconds = uiState.currentTimeSeconds,
                totalDurationSeconds = uiState.totalDurationSeconds,
                isPlaying = uiState.isPlaying,
                clips = uiState.clips,
                overlays = uiState.overlays,
                selectedClipId = uiState.selectedClipId,
                selectedOverlayId = uiState.selectedOverlayId,
                isAudioMuted = uiState.isAudioMuted,
                trackingMode = uiState.trackingMode,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onSeek = { viewModel.seekTo(it) },
                onStepBack = { viewModel.stepFrame(-1.0 / 30.0) },
                onStepForward = { viewModel.stepFrame(1.0 / 30.0) },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onToggleAudioMute = { viewModel.toggleAudioMute() },
                onSelectClip = { viewModel.selectClip(it) },
                onDeselectAll = { viewModel.deselectAll() },
                onSelectOverlay = { viewModel.selectOverlay(it) },
                onStopTracking = { viewModel.stopTracking() },
                onTrimClipBoundaries = { clipId, newIn, newOut ->
                    viewModel.trimClipBoundaries(clipId, newIn, newOut)
                }
            )
        }

        // Export Dialog
        if (uiState.showExportDialog) {
            ExportDialog(
                onDismiss = { viewModel.showExportDialog(false) },
                onConfirmExport = { settings ->
                    viewModel.startExport(settings)
                }
            )
        }

        // Export Real-Time Progress Bar Dialog
        if (uiState.showExportProgressDialog && uiState.activeExportJob != null) {
            ExportProgressDialog(
                exportJob = uiState.activeExportJob!!,
                onDismiss = { viewModel.closeExportProgress() }
            )
        }

        // Live Process Logcat & Diagnostic Logs Dialog
        if (showLogcatDialog) {
            LogcatViewerDialog(
                onDismiss = { showLogcatDialog = false }
            )
        }
    }
}
