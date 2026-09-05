package com.aiditor.app.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiditor.app.data.model.Project
import com.aiditor.app.ui.components.BwTopBar
import com.aiditor.app.ui.components.ExportDialog
import com.aiditor.app.ui.components.ExportProgressDialog
import com.aiditor.app.ui.theme.BwBlack

/**
 * Screen 2: Video Editing Workspace.
 * Layout:
 * - TopBar: Video Export button, Back to main menu, Title, Undo/Redo (statusBarsPadding)
 * - Center-to-upper: Video Preview Screen with HUD overlay and gallery video playback
 * - Center-to-bottom: Interactive Multi-track Timeline Scrubber
 * - Bottom side: Feature/Tool list as in bottom bar (6 core tools) (navigationBarsPadding)
 * - Docked Tool Inspector: Complete access to modify input, middle, output with Real Visualizer!
 */
@Composable
fun WorkspaceScreen(
    project: Project,
    viewModel: WorkspaceViewModel,
    onBackToMainMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(project.id) {
        viewModel.loadProject(project)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BwBlack,
        topBar = {
            BwTopBar(
                title = uiState.project?.name ?: "WORKSPACE",
                onBackClick = onBackToMainMenu,
                onExportClick = { viewModel.showExportDialog(true) },
                onUndoClick = { /* Undo action */ },
                onRedoClick = { /* Redo action */ },
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BwBlack)
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
                        onApplyToTimeline = { viewModel.closeToolInspector() },
                        modifier = Modifier.heightIn(max = 320.dp)
                    )
                }

                // Bottom feature/tool list
                BottomToolBar(
                    activeTool = uiState.activeTool,
                    onSelectTool = { viewModel.selectTool(it) }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BwBlack)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. CENTER-TO-UPPER: Video Preview Screen
            VideoPreviewSection(
                videoPath = uiState.project?.videoPath,
                currentTimeSeconds = uiState.currentTimeSeconds,
                totalDurationSeconds = uiState.totalDurationSeconds,
                isPlaying = uiState.isPlaying,
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onStepBack = { viewModel.stepFrame(-1.0 / 30.0) },
                onStepForward = { viewModel.stepFrame(1.0 / 30.0) },
                activeTool = uiState.activeTool
            )

            // 2. CENTER-TO-BOTTOM: Timeline Scrubber
            TimelineSection(
                currentTimeSeconds = uiState.currentTimeSeconds,
                totalDurationSeconds = uiState.totalDurationSeconds,
                markers = uiState.markers,
                onSeek = { viewModel.seekTo(it) },
                onSplit = { viewModel.splitClipAtPlayhead() },
                onTrim = { viewModel.trimClip() },
                modifier = Modifier.padding(bottom = 6.dp)
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

        // Export Progress Dialog
        if (uiState.showExportProgressDialog && uiState.activeExportJob != null) {
            ExportProgressDialog(
                exportJob = uiState.activeExportJob!!,
                onDismiss = { viewModel.closeExportProgress() }
            )
        }
    }
}
