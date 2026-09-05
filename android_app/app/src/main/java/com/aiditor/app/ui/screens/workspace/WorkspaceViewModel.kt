package com.aiditor.app.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiditor.app.data.model.*
import com.aiditor.app.data.repository.VideoEditingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val project: Project? = null,
    val currentTimeSeconds: Double = 0.0,
    val totalDurationSeconds: Double = 30.0,
    val isPlaying: Boolean = false,
    val activeTool: ToolType? = null,
    val activeVisualizerData: ToolVisualizerData? = null,
    val inputParams: InputParameters = InputParameters(),
    val middleParams: MiddleParameters = MiddleParameters.OpticalFlow(),
    val outputParams: OutputParameters = OutputParameters(),
    val markers: List<TimelineMarker> = emptyList(),
    val showExportDialog: Boolean = false,
    val activeExportJob: ExportJob? = null,
    val showExportProgressDialog: Boolean = false,
    val canUndo: Boolean = true,
    val canRedo: Boolean = false
)

class WorkspaceViewModel(
    private val editingRepository: VideoEditingRepository = VideoEditingRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var exportJobSubscription: Job? = null

    fun loadProject(project: Project) {
        _uiState.value = _uiState.value.copy(
            project = project,
            totalDurationSeconds = project.durationSeconds.coerceAtLeast(5.0),
            currentTimeSeconds = 0.0,
            markers = project.timelineMarkers,
            inputParams = InputParameters(sourcePath = project.videoPath)
        )
    }

    fun togglePlayPause() {
        val willPlay = !_uiState.value.isPlaying
        _uiState.value = _uiState.value.copy(isPlaying = willPlay)

        playbackJob?.cancel()
        if (willPlay) {
            playbackJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    delay(50)
                    var nextTime = _uiState.value.currentTimeSeconds + 0.05
                    if (nextTime >= _uiState.value.totalDurationSeconds) {
                        nextTime = 0.0
                    }
                    _uiState.value = _uiState.value.copy(currentTimeSeconds = nextTime)
                }
            }
        }
    }

    fun seekTo(timeSeconds: Double) {
        _uiState.value = _uiState.value.copy(
            currentTimeSeconds = timeSeconds.coerceIn(0.0, _uiState.value.totalDurationSeconds)
        )
    }

    fun stepFrame(deltaSeconds: Double) {
        seekTo(_uiState.value.currentTimeSeconds + deltaSeconds)
    }

    fun selectTool(tool: ToolType) {
        if (_uiState.value.activeTool == tool) {
            // Toggle off if re-clicked
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

    fun splitClipAtPlayhead() {
        val current = _uiState.value.currentTimeSeconds
        val updatedMarkers = _uiState.value.markers.toMutableList().apply {
            add(TimelineMarker(current, "Cut Split"))
        }
        _uiState.value = _uiState.value.copy(markers = updatedMarkers)
    }

    fun trimClip() {
        val current = _uiState.value.currentTimeSeconds
        updateInputParams(_uiState.value.inputParams.copy(inPointSeconds = current))
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
            editingRepository.exportVideoProgress(
                toolType = tool,
                input = _uiState.value.inputParams,
                middle = _uiState.value.middleParams,
                output = _uiState.value.outputParams.copy(
                    resolution = settings.resolution,
                    fps = settings.fps
                )
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
