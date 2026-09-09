package com.aiditor.app.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiditor.app.data.model.*
import com.aiditor.app.data.repository.ProjectRepository
import com.aiditor.app.data.repository.VideoEditingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Stack

data class WorkspaceUiState(
    val project: Project? = null,
    val currentTimeSeconds: Double = 0.0,
    val totalDurationSeconds: Double = 10.0,
    val isPlaying: Boolean = false,
    val activeTool: ToolType? = null,
    val activeVisualizerData: ToolVisualizerData? = null,
    val inputParams: InputParameters = InputParameters(),
    val middleParams: MiddleParameters = MiddleParameters.OpticalFlow(),
    val outputParams: OutputParameters = OutputParameters(),
    val markers: List<TimelineMarker> = emptyList(),
    val clips: List<TimelineClip> = emptyList(),
    val overlays: List<TimelineOverlay> = emptyList(),
    val selectedClipId: String? = null,
    val selectedOverlayId: String? = null,
    val aspectRatio: AspectRatioMode = AspectRatioMode.ORIGINAL,
    val isAudioMuted: Boolean = false,
    val trackingMode: ActiveTrackingMode = ActiveTrackingMode.NONE,
    val isFullscreen: Boolean = false,
    val showAspectRatioDropdown: Boolean = false,
    val showExportDialog: Boolean = false,
    val activeExportJob: ExportJob? = null,
    val showExportProgressDialog: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class WorkspaceViewModel(
    private val editingRepository: VideoEditingRepository = VideoEditingRepository(),
    private val projectRepository: ProjectRepository = ProjectRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var exportJobSubscription: Job? = null

    // Undo / Redo history stacks
    private val undoStack = Stack<ProjectSnapshot>()
    private val redoStack = Stack<ProjectSnapshot>()

    private data class ProjectSnapshot(
        val clips: List<TimelineClip>,
        val overlays: List<TimelineOverlay>,
        val markers: List<TimelineMarker>,
        val isMuted: Boolean,
        val trackingMode: ActiveTrackingMode
    )

    fun loadProject(project: Project) {
        val initialClips = if (project.clips.isNotEmpty()) {
            project.clips
        } else {
            listOf(
                TimelineClip(
                    id = "clip_0",
                    title = project.name.ifEmpty { "Main Clip" },
                    sourcePath = project.videoPath,
                    inPointSeconds = 0.0,
                    outPointSeconds = project.durationSeconds.coerceAtLeast(10.0),
                    durationSeconds = project.durationSeconds.coerceAtLeast(10.0),
                    isSelected = true
                )
            )
        }

        // Demo overlays matching reference images if none present
        val initialOverlays = if (project.overlays.isNotEmpty()) {
            project.overlays
        } else {
            listOf(
                TimelineOverlay(
                    id = "overlay_skull",
                    type = OverlayType.SKULL_STICKER,
                    label = "7.5s",
                    startTimeSeconds = 1.0,
                    durationSeconds = 7.5,
                    isSelected = true
                ),
                TimelineOverlay(
                    id = "overlay_tracking",
                    type = OverlayType.TRACKING_EFFECT,
                    label = "Tracking",
                    startTimeSeconds = 1.2,
                    durationSeconds = 5.0,
                    isProcessing = true
                ),
                TimelineOverlay(
                    id = "overlay_ok",
                    type = OverlayType.OK_STICKER,
                    label = "OK",
                    startTimeSeconds = 1.5,
                    durationSeconds = 3.2
                )
            )
        }

        _uiState.value = _uiState.value.copy(
            project = project,
            totalDurationSeconds = project.durationSeconds.coerceAtLeast(10.0),
            currentTimeSeconds = 1.85,
            markers = project.timelineMarkers,
            clips = initialClips,
            overlays = initialOverlays,
            selectedClipId = initialClips.firstOrNull()?.id,
            aspectRatio = project.aspectRatio,
            isAudioMuted = project.isAudioMuted,
            trackingMode = project.trackingMode,
            inputParams = InputParameters(
                sourcePath = project.videoPath,
                inPointSeconds = 0.0,
                outPointSeconds = project.durationSeconds
            )
        )
    }

    private fun pushUndoState() {
        val state = _uiState.value
        undoStack.push(
            ProjectSnapshot(
                clips = state.clips,
                overlays = state.overlays,
                markers = state.markers,
                isMuted = state.isAudioMuted,
                trackingMode = state.trackingMode
            )
        )
        redoStack.clear()
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value
        redoStack.push(
            ProjectSnapshot(
                clips = current.clips,
                overlays = current.overlays,
                markers = current.markers,
                isMuted = current.isAudioMuted,
                trackingMode = current.trackingMode
            )
        )
        val snap = undoStack.pop()
        _uiState.value = _uiState.value.copy(
            clips = snap.clips,
            overlays = snap.overlays,
            markers = snap.markers,
            isAudioMuted = snap.isMuted,
            trackingMode = snap.trackingMode,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value
        undoStack.push(
            ProjectSnapshot(
                clips = current.clips,
                overlays = current.overlays,
                markers = current.markers,
                isMuted = current.isAudioMuted,
                trackingMode = current.trackingMode
            )
        )
        val snap = redoStack.pop()
        _uiState.value = _uiState.value.copy(
            clips = snap.clips,
            overlays = snap.overlays,
            markers = snap.markers,
            isAudioMuted = snap.isMuted,
            trackingMode = snap.trackingMode,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun onPlaybackTimeUpdate(posSec: Double) {
        _uiState.value = _uiState.value.copy(
            currentTimeSeconds = posSec.coerceIn(0.0, _uiState.value.totalDurationSeconds)
        )
    }

    fun togglePlayPause() {
        val willPlay = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(isPlaying = willPlay)

        playbackJob?.cancel()
        // If no video is present, run high-precision synthetic timer for preview/playhead
        if (willPlay && _uiState.value.project?.videoPath.isNullOrBlank()) {
            playbackJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    delay(16) // ~60 FPS smooth demo animation
                    var nextTime = _uiState.value.currentTimeSeconds + 0.016
                    if (nextTime >= _uiState.value.totalDurationSeconds) {
                        nextTime = 0.0
                    }
                    _uiState.value = _uiState.value.copy(currentTimeSeconds = nextTime)
                }
            }
        }
    }

    fun seekTo(timeSeconds: Double) {
        val clamped = timeSeconds.coerceIn(0.0, _uiState.value.totalDurationSeconds)
        _uiState.value = _uiState.value.copy(currentTimeSeconds = clamped)
    }

    fun stepFrame(deltaSeconds: Double) {
        seekTo(_uiState.value.currentTimeSeconds + deltaSeconds)
    }

    fun selectClip(clipId: String) {
        val updated = _uiState.value.clips.map {
            it.copy(isSelected = it.id == clipId)
        }
        _uiState.value = _uiState.value.copy(
            clips = updated,
            selectedClipId = clipId
        )
    }

    fun selectOverlay(overlayId: String) {
        val updated = _uiState.value.overlays.map {
            it.copy(isSelected = it.id == overlayId)
        }
        _uiState.value = _uiState.value.copy(
            overlays = updated,
            selectedOverlayId = overlayId
        )
    }

    fun splitClipAtPlayhead() {
        pushUndoState()
        val playhead = _uiState.value.currentTimeSeconds
        val currentClips = _uiState.value.clips.toMutableList()

        // Find clip containing playhead
        val targetIndex = currentClips.indexOfFirst {
            playhead >= it.inPointSeconds && playhead <= it.outPointSeconds
        }

        if (targetIndex != -1) {
            val target = currentClips[targetIndex]
            val splitTime = playhead.coerceIn(target.inPointSeconds + 0.05, target.outPointSeconds - 0.05)
            val clipA = target.copy(
                outPointSeconds = splitTime,
                durationSeconds = splitTime - target.inPointSeconds,
                isSelected = false
            )
            val clipB = target.copy(
                id = "clip_${System.currentTimeMillis()}",
                inPointSeconds = splitTime,
                durationSeconds = target.outPointSeconds - splitTime,
                isSelected = true
            )
            currentClips[targetIndex] = clipA
            currentClips.add(targetIndex + 1, clipB)

            _uiState.value = _uiState.value.copy(
                clips = currentClips,
                selectedClipId = clipB.id
            )
        }
    }

    fun deleteSelectedClip() {
        pushUndoState()
        val selId = _uiState.value.selectedClipId ?: return
        val currentClips = _uiState.value.clips.filter { it.id != selId }
        val newSelection = currentClips.firstOrNull()?.id
        val updated = currentClips.map { it.copy(isSelected = it.id == newSelection) }

        _uiState.value = _uiState.value.copy(
            clips = updated,
            selectedClipId = newSelection
        )
    }

    fun duplicateSelectedClip() {
        pushUndoState()
        val selId = _uiState.value.selectedClipId ?: return
        val currentClips = _uiState.value.clips.toMutableList()
        val index = currentClips.indexOfFirst { it.id == selId }
        if (index != -1) {
            val original = currentClips[index]
            val copy = original.copy(
                id = "clip_dup_${System.currentTimeMillis()}",
                title = "${original.title} (Copy)",
                isSelected = true
            )
            currentClips[index] = original.copy(isSelected = false)
            currentClips.add(index + 1, copy)
            _uiState.value = _uiState.value.copy(
                clips = currentClips,
                selectedClipId = copy.id
            )
        }
    }

    fun trimClip() {
        pushUndoState()
        val playhead = _uiState.value.currentTimeSeconds
        val selId = _uiState.value.selectedClipId
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == selId) {
                clip.copy(
                    inPointSeconds = playhead,
                    durationSeconds = (clip.outPointSeconds - playhead).coerceAtLeast(0.1)
                )
            } else clip
        }
        _uiState.value = _uiState.value.copy(clips = updated)
    }

    fun trimClipBoundaries(clipId: String, newIn: Double, newOut: Double) {
        pushUndoState()
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == clipId) {
                clip.copy(
                    inPointSeconds = newIn.coerceAtLeast(0.0),
                    outPointSeconds = newOut.coerceAtLeast(newIn + 0.1),
                    durationSeconds = (newOut - newIn).coerceAtLeast(0.1)
                )
            } else clip
        }
        _uiState.value = _uiState.value.copy(clips = updated)
    }

    fun replaceSelectedClip(newVideoPath: String) {
        pushUndoState()
        val selId = _uiState.value.selectedClipId ?: return
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == selId) {
                clip.copy(sourcePath = newVideoPath)
            } else clip
        }
        _uiState.value = _uiState.value.copy(clips = updated)
    }

    fun addStickerOverlay(type: OverlayType) {
        pushUndoState()
        val playhead = _uiState.value.currentTimeSeconds
        val newOverlay = TimelineOverlay(
            id = "ov_${System.currentTimeMillis()}",
            type = type,
            label = when (type) {
                OverlayType.SKULL_STICKER -> "7.5s"
                OverlayType.OK_STICKER -> "OK"
                OverlayType.TRACKING_EFFECT -> "Tracking"
                OverlayType.STABILIZATION_EFFECT -> "Stabilizing"
                OverlayType.FACE_TRACK_EFFECT -> "Face Tracking"
                OverlayType.TEXT_OVERLAY -> "Text"
            },
            startTimeSeconds = playhead,
            durationSeconds = 4.0,
            isSelected = true
        )
        val current = _uiState.value.overlays.map { it.copy(isSelected = false) } + newOverlay
        _uiState.value = _uiState.value.copy(
            overlays = current,
            selectedOverlayId = newOverlay.id
        )
    }

    fun toggleAudioMute() {
        val newMute = !_uiState.value.isAudioMuted
        _uiState.value = _uiState.value.copy(isAudioMuted = newMute)
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _uiState.value = _uiState.value.copy(
            aspectRatio = mode,
            showAspectRatioDropdown = false
        )
    }

    fun toggleAspectRatioDropdown(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAspectRatioDropdown = show)
    }

    fun setTrackingMode(mode: ActiveTrackingMode) {
        pushUndoState()
        val newMode = if (_uiState.value.trackingMode == mode) ActiveTrackingMode.NONE else mode
        val overlays = _uiState.value.overlays.toMutableList()

        if (newMode != ActiveTrackingMode.NONE) {
            val effectOverlay = TimelineOverlay(
                id = "track_effect_${System.currentTimeMillis()}",
                type = when (newMode) {
                    ActiveTrackingMode.MOTION_TRACKING -> OverlayType.TRACKING_EFFECT
                    ActiveTrackingMode.MOTION_STABILIZATION -> OverlayType.STABILIZATION_EFFECT
                    ActiveTrackingMode.FACE_TRACKING -> OverlayType.FACE_TRACK_EFFECT
                    ActiveTrackingMode.NONE -> OverlayType.TRACKING_EFFECT
                },
                label = when (newMode) {
                    ActiveTrackingMode.MOTION_TRACKING -> "Tracking"
                    ActiveTrackingMode.MOTION_STABILIZATION -> "Stabilizing"
                    ActiveTrackingMode.FACE_TRACKING -> "Face Tracking"
                    ActiveTrackingMode.NONE -> ""
                },
                startTimeSeconds = _uiState.value.currentTimeSeconds,
                durationSeconds = 8.0,
                isProcessing = true,
                isSelected = true
            )
            overlays.add(0, effectOverlay)
        }

        _uiState.value = _uiState.value.copy(
            trackingMode = newMode,
            overlays = overlays
        )
    }

    fun stopTracking() {
        pushUndoState()
        val updated = _uiState.value.overlays.map {
            if (it.isProcessing) it.copy(isProcessing = false) else it
        }
        _uiState.value = _uiState.value.copy(
            trackingMode = ActiveTrackingMode.NONE,
            overlays = updated
        )
    }

    fun clearActiveTracking() {
        pushUndoState()
        val updated = _uiState.value.overlays.filter {
            it.type != OverlayType.TRACKING_EFFECT &&
            it.type != OverlayType.STABILIZATION_EFFECT &&
            it.type != OverlayType.FACE_TRACK_EFFECT
        }
        _uiState.value = _uiState.value.copy(
            trackingMode = ActiveTrackingMode.NONE,
            overlays = updated
        )
    }

    fun selectTool(tool: ToolType) {
        if (_uiState.value.activeTool == tool) {
            _uiState.value = _uiState.value.copy(activeTool = null, activeVisualizerData = null)
            return
        }

        val defaultMiddle = when (tool) {
            ToolType.OPTICAL_FLOW -> MiddleParameters.OpticalFlow()
            ToolType.BEAT_SYNC -> MiddleParameters.BeatSync()
            ToolType.MOTION_TRACKING -> MiddleParameters.MotionTracking()
            ToolType.SPEED_RAMP -> MiddleParameters.SpeedRamp()
            ToolType.COLOR_GRADE -> MiddleParameters.ColorGrade()
            ToolType.ROTOSCOPE -> MiddleParameters.Rotoscope()
        }

        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            middleParams = defaultMiddle
        )

        refreshVisualizerData()
    }

    fun closeToolInspector() {
        _uiState.value = _uiState.value.copy(activeTool = null, activeVisualizerData = null)
    }

    fun updateInputParams(params: InputParameters) {
        _uiState.value = _uiState.value.copy(inputParams = params)
        refreshVisualizerData()
    }

    fun updateMiddleParams(params: MiddleParameters) {
        _uiState.value = _uiState.value.copy(middleParams = params)
        refreshVisualizerData()
    }

    fun updateOutputParams(params: OutputParameters) {
        _uiState.value = _uiState.value.copy(outputParams = params)
    }

    private fun refreshVisualizerData() {
        val tool = _uiState.value.activeTool ?: return
        viewModelScope.launch {
            val visData = editingRepository.getVisualizerData(
                toolType = tool,
                input = _uiState.value.inputParams,
                middle = _uiState.value.middleParams
            )
            _uiState.value = _uiState.value.copy(activeVisualizerData = visData)
        }
    }

    fun showExportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showExportDialog = show)
    }

    fun startExport(settings: ExportSettings) {
        val tool = _uiState.value.activeTool ?: ToolType.COLOR_GRADE
        showExportDialog(false)
        _uiState.value = _uiState.value.copy(showExportProgressDialog = true)

        exportJobSubscription?.cancel()
        exportJobSubscription = viewModelScope.launch {
            val source = _uiState.value.project?.videoPath?.ifBlank { _uiState.value.inputParams.sourcePath }
                ?: _uiState.value.inputParams.sourcePath

            editingRepository.exportVideoProgress(
                toolType = tool,
                input = _uiState.value.inputParams.copy(
                    sourcePath = source,
                    muteAudio = _uiState.value.isAudioMuted
                ),
                middle = _uiState.value.middleParams,
                output = _uiState.value.outputParams.copy(
                    resolution = settings.resolution,
                    fps = settings.fps
                ),
                durationSeconds = _uiState.value.totalDurationSeconds
            ).collect { job ->
                _uiState.value = _uiState.value.copy(activeExportJob = job)
            }
        }
    }

    fun closeExportProgress() {
        _uiState.value = _uiState.value.copy(
            showExportProgressDialog = false,
            activeExportJob = null
        )
    }
}
