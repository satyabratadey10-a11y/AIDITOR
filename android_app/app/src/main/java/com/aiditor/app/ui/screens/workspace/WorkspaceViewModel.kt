package com.aiditor.app.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiditor.app.data.model.*
import com.aiditor.app.data.repository.ProjectRepository
import com.aiditor.app.data.repository.VideoEditingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    private val _playbackPosition = MutableStateFlow(0.0)
    val playbackPosition: StateFlow<Double> = _playbackPosition.asStateFlow()

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
                    isSelected = false
                )
            )
        }

        val initialOverlays = project.overlays
        val initialSelectedClipId = initialClips.find { it.isSelected }?.id ?: initialClips.firstOrNull()?.id
        val selClip = initialClips.find { it.id == initialSelectedClipId }
        val calcDuration = if (initialClips.isNotEmpty()) {
            initialClips.sumOf { it.durationSeconds }.coerceAtLeast(0.5)
        } else {
            project.durationSeconds.coerceAtLeast(10.0)
        }

        _uiState.value = _uiState.value.copy(
            project = project,
            totalDurationSeconds = calcDuration,
            currentTimeSeconds = 0.0,
            markers = project.timelineMarkers,
            clips = initialClips,
            overlays = initialOverlays,
            selectedClipId = initialSelectedClipId,
            aspectRatio = project.aspectRatio,
            isAudioMuted = project.isAudioMuted,
            trackingMode = project.trackingMode,
            middleParams = selClip?.colorGrade ?: MiddleParameters.ColorGrade(),
            inputParams = InputParameters(
                sourcePath = selClip?.sourcePath ?: project.videoPath,
                inPointSeconds = selClip?.inPointSeconds ?: 0.0,
                outPointSeconds = selClip?.outPointSeconds ?: calcDuration
            )
        )
    }

    fun persistCurrentProject() {
        val currentProj = _uiState.value.project ?: return
        val currentClips = _uiState.value.clips
        val totalDur = if (currentClips.isNotEmpty()) {
            currentClips.sumOf { it.durationSeconds }.coerceAtLeast(0.5)
        } else {
            _uiState.value.totalDurationSeconds
        }
        val updated = currentProj.copy(
            clips = currentClips,
            overlays = _uiState.value.overlays,
            timelineMarkers = _uiState.value.markers,
            aspectRatio = _uiState.value.aspectRatio,
            isAudioMuted = _uiState.value.isAudioMuted,
            trackingMode = _uiState.value.trackingMode,
            durationSeconds = totalDur
        )
        _uiState.value = _uiState.value.copy(
            project = updated,
            totalDurationSeconds = totalDur
        )
        projectRepository.updateProject(updated)
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
        val clamped = posSec.coerceIn(0.0, _uiState.value.totalDurationSeconds)
        _playbackPosition.value = clamped
    }

    fun onPlaybackEnded() {
        playbackJob?.cancel()
        playbackJob = null
        val end = _uiState.value.totalDurationSeconds
        _playbackPosition.value = end
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            currentTimeSeconds = end
        )
    }

    fun togglePlayPause() {
        val willPlay = !_uiState.value.isPlaying
        
        // If at the end of the timeline, restart smoothly from beginning
        val currentPos = _playbackPosition.value
        val startTime = if (willPlay && currentPos >= (_uiState.value.totalDurationSeconds - 0.05)) {
            0.0
        } else {
            currentPos
        }

        _playbackPosition.value = startTime
        _uiState.value = _uiState.value.copy(
            isPlaying = willPlay,
            currentTimeSeconds = startTime
        )

        playbackJob?.cancel()
        if (willPlay) {
            playbackJob = viewModelScope.launch {
                var lastTimeNs = System.nanoTime()
                var lastUiUpdateMs = System.currentTimeMillis()
                while (_uiState.value.isPlaying) {
                    delay(16) // Smooth 60 FPS clock for GPU Canvas and timeline playhead
                    val nowNs = System.nanoTime()
                    val dt = ((nowNs - lastTimeNs) / 1_000_000_000.0).coerceIn(0.005, 0.050)
                    lastTimeNs = nowNs

                    val nextTime = _playbackPosition.value + dt
                    if (nextTime >= _uiState.value.totalDurationSeconds) {
                        onPlaybackEnded()
                        break
                    } else {
                        _playbackPosition.value = nextTime
                        // Throttle root UiState updates to ~5 Hz to keep UI thread completely free from GC/recomposition lag
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastUiUpdateMs >= 200) {
                            lastUiUpdateMs = nowMs
                            _uiState.value = _uiState.value.copy(currentTimeSeconds = nextTime)
                        }
                    }
                }
            }
        }
    }

    fun seekTo(timeSeconds: Double) {
        val clamped = timeSeconds.coerceIn(0.0, _uiState.value.totalDurationSeconds)
        _playbackPosition.value = clamped
        _uiState.value = _uiState.value.copy(currentTimeSeconds = clamped)
    }

    fun stepFrame(deltaSeconds: Double) {
        seekTo(_uiState.value.currentTimeSeconds + deltaSeconds)
    }

    fun selectClip(clipId: String) {
        val isAlreadySelected = _uiState.value.selectedClipId == clipId
        val targetId = if (isAlreadySelected) null else clipId
        val updated = _uiState.value.clips.map {
            it.copy(isSelected = it.id == targetId)
        }
        val selClip = updated.find { it.id == targetId }
        val newMiddle = if (selClip != null) {
            when (_uiState.value.activeTool) {
                ToolType.COLOR_GRADE -> selClip.colorGrade
                ToolType.SPEED_RAMP -> MiddleParameters.SpeedRamp(
                    curveControlPoints = selClip.speedCurvePoints.ifEmpty {
                        listOf(
                            CurveControlPoint(0.0f, 1.0f),
                            CurveControlPoint(0.35f, 0.2f),
                            CurveControlPoint(0.7f, selClip.speedMultiplier),
                            CurveControlPoint(1.0f, 1.0f)
                        )
                    },
                    maxSpeedMultiplier = selClip.speedMultiplier
                )
                ToolType.OPTICAL_FLOW -> MiddleParameters.OpticalFlow(
                    isEnabled = selClip.isOpticalFlowEnabled,
                    targetFps = selClip.opticalFlowFps
                )
                else -> selClip.colorGrade
            }
        } else _uiState.value.middleParams

        _uiState.value = _uiState.value.copy(
            clips = updated,
            selectedClipId = targetId,
            middleParams = newMiddle
        )
    }

    fun deselectAll() {
        val updated = _uiState.value.clips.map { it.copy(isSelected = false) }
        val updatedOverlays = _uiState.value.overlays.map { it.copy(isSelected = false) }
        _uiState.value = _uiState.value.copy(
            clips = updated,
            overlays = updatedOverlays,
            selectedClipId = null,
            selectedOverlayId = null
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
            persistCurrentProject()
        }
    }

    fun deleteSelectedClip() {
        pushUndoState()
        val selId = _uiState.value.selectedClipId
            ?: _uiState.value.clips.find { it.isSelected }?.id
            ?: _uiState.value.clips.firstOrNull()?.id
            ?: return
        val currentClips = _uiState.value.clips.filter { it.id != selId }
        val newSelection = currentClips.firstOrNull()?.id
        val updated = currentClips.map { it.copy(isSelected = it.id == newSelection) }

        _uiState.value = _uiState.value.copy(
            clips = updated,
            selectedClipId = newSelection
        )
        persistCurrentProject()
    }

    fun duplicateSelectedClip() {
        pushUndoState()
        val selId = _uiState.value.selectedClipId
            ?: _uiState.value.clips.find { it.isSelected }?.id
            ?: _uiState.value.clips.firstOrNull()?.id
            ?: return
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
            persistCurrentProject()
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
                    durationSeconds = ((clip.outPointSeconds - playhead) / clip.speedMultiplier).coerceAtLeast(0.1)
                )
            } else clip
        }
        _uiState.value = _uiState.value.copy(clips = updated)
        persistCurrentProject()
    }

    fun trimClipBoundaries(clipId: String, newIn: Double, newOut: Double, isCommitted: Boolean = true) {
        if (isCommitted) {
            pushUndoState()
        }
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == clipId) {
                val validIn = newIn.coerceAtLeast(0.0)
                val validOut = newOut.coerceAtLeast(validIn + 0.1)
                val newDur = ((validOut - validIn) / clip.speedMultiplier).coerceAtLeast(0.1)
                clip.copy(
                    inPointSeconds = validIn,
                    outPointSeconds = validOut,
                    durationSeconds = newDur
                )
            } else clip
        }
        val newTotalDur = updated.maxOfOrNull { it.inPointSeconds + it.durationSeconds }?.coerceAtLeast(5.0) ?: 10.0
        _uiState.value = _uiState.value.copy(
            clips = updated,
            totalDurationSeconds = newTotalDur
        )
        if (isCommitted) {
            persistCurrentProject()
        }
    }

    /**
     * Changes playback speed of the selected clip.
     * Decreasing speed lengthens the clip duration on the timeline,
     * increasing speed shortens the clip duration.
     */
    fun changeClipSpeed(multiplier: Float) {
        pushUndoState()
        val speed = multiplier.coerceIn(0.1f, 8.0f)
        val selId = _uiState.value.selectedClipId
        val updated = _uiState.value.clips.map { clip ->
            if (clip.id == selId || (selId == null && clip.isSelected)) {
                val baseSpan = (clip.outPointSeconds - clip.inPointSeconds).coerceAtLeast(0.1)
                val newDuration = (baseSpan / speed).coerceAtLeast(0.1)
                clip.copy(
                    speedMultiplier = speed,
                    durationSeconds = newDuration
                )
            } else clip
        }
        val newTotalDur = updated.maxOfOrNull { it.inPointSeconds + it.durationSeconds }?.coerceAtLeast(5.0) ?: 10.0
        _uiState.value = _uiState.value.copy(
            clips = updated,
            totalDurationSeconds = newTotalDur
        )
        persistCurrentProject()
    }

    /**
     * Imports multiple media clips (videos and/or images) at the timeline sequentially.
     * When user chooses an image, the app strictly treats the image as a 2.0-second static video clip.
     */
    fun addMediaClips(uris: List<String>, isImage: Boolean = false) {
        if (uris.isEmpty()) return
        pushUndoState()
        val currentClips = _uiState.value.clips.toMutableList()
        var lastAddedId: String? = null
        uris.forEachIndexed { index, uriStr ->
            val newId = "clip_${System.currentTimeMillis()}_$index"
            lastAddedId = newId
            val title = if (isImage) "Photo ${currentClips.size + 1}" else "Clip ${currentClips.size + 1}"
            val duration = if (isImage) 2.0 else 10.0 // 2-second static video clip for images
            val nextInPoint = currentClips.maxOfOrNull { it.inPointSeconds + it.durationSeconds } ?: 0.0
            val clip = TimelineClip(
                id = newId,
                title = title,
                sourcePath = uriStr,
                inPointSeconds = nextInPoint,
                outPointSeconds = duration,
                durationSeconds = duration,
                isImage = isImage,
                isSelected = false
            )
            currentClips.add(clip)
        }
        val finalClips = currentClips.map { it.copy(isSelected = it.id == lastAddedId) }
        val newTotalDur = finalClips.maxOfOrNull { it.inPointSeconds + it.durationSeconds }?.coerceAtLeast(5.0) ?: 10.0
        _uiState.value = _uiState.value.copy(
            clips = finalClips,
            selectedClipId = lastAddedId,
            totalDurationSeconds = newTotalDur
        )
        persistCurrentProject()
    }

    /**
     * Dynamically repositions and scales the motion tracking reticle from preview screen gestures.
     */
    fun updateTrackingTarget(targetX: Float, targetY: Float, boxW: Float, boxH: Float) {
        val motion = (_uiState.value.middleParams as? MiddleParameters.MotionTracking)
            ?: MiddleParameters.MotionTracking()
        val defaultContour = com.aiditor.app.util.SubjectOutliner.generateDefaultContour(
            targetX, targetY, boxW, boxH, 24
        )
        val updatedMotion = motion.copy(
            targetX = targetX.coerceIn(0.05f, 0.95f),
            targetY = targetY.coerceIn(0.05f, 0.95f),
            boxWidth = boxW.coerceIn(0.04f, 0.8f),
            boxHeight = boxH.coerceIn(0.04f, 0.8f),
            isTrackingDone = false,
            trackingKeyframes = emptyList(),
            subjectContour = defaultContour,
            trackingContours = emptyList()
        )
        val nextTracking = if (motion.trackingMode == "target_lock") ActiveTrackingMode.MOTION_STABILIZATION else ActiveTrackingMode.MOTION_TRACKING
        _uiState.value = _uiState.value.copy(
            middleParams = updatedMotion,
            trackingMode = nextTracking
        )

        // Asynchronously extract exact subject contour from current video frame
        val selClip = _uiState.value.clips.find { it.isSelected } ?: _uiState.value.clips.firstOrNull()
        val videoPath = selClip?.sourcePath?.ifBlank { null } ?: _uiState.value.project?.videoPath ?: ""
        val context = com.aiditor.app.AiditorApp.instance
        if (context != null && videoPath.isNotBlank()) {
            viewModelScope.launch {
                val contour = com.aiditor.app.util.SubjectOutliner.extractContourFromVideo(
                    context = context,
                    videoPath = videoPath,
                    timeSeconds = _playbackPosition.value,
                    targetX = targetX,
                    targetY = targetY,
                    boxWidth = boxW,
                    boxHeight = boxH
                )
                val curMotion = _uiState.value.middleParams as? MiddleParameters.MotionTracking
                if (curMotion != null) {
                    _uiState.value = _uiState.value.copy(
                        middleParams = curMotion.copy(subjectContour = contour)
                    )
                }
            }
        }
    }

    /**
     * Executes real hardware on-device motion tracking across video frames.
     * Computes motion trajectory keyframes and places tracking overlay onto Track 2.
     */
    fun startMotionTracking() {
        val motion = (_uiState.value.middleParams as? MiddleParameters.MotionTracking)
            ?: MiddleParameters.MotionTracking()
        val selClip = _uiState.value.clips.find { it.isSelected } ?: _uiState.value.clips.firstOrNull()
        val clipStart = selClip?.inPointSeconds ?: 0.0
        val clipDur = selClip?.durationSeconds ?: 5.0
        val videoPath = selClip?.sourcePath?.ifBlank { null } ?: _uiState.value.project?.videoPath ?: ""
        val context = com.aiditor.app.AiditorApp.instance
        val anchorTime = _playbackPosition.value.coerceIn(clipStart, clipStart + clipDur)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                middleParams = motion.copy(
                    isTrackingRunning = true,
                    trackingProgress = 0f,
                    isTrackingDone = false
                )
            )

            val trackingResult = if (context != null && videoPath.isNotBlank()) {
                com.aiditor.app.util.MotionTrackerEngine.trackSubjectAdvanced(
                    context = context,
                    videoPath = videoPath,
                    anchorTimeSeconds = anchorTime,
                    startTimeSeconds = clipStart,
                    durationSeconds = clipDur,
                    initialX = motion.targetX,
                    initialY = motion.targetY,
                    boxWidth = motion.boxWidth,
                    boxHeight = motion.boxHeight,
                    smoothFactor = motion.smoothFactor,
                    numSamples = 30,
                    onProgress = { pct ->
                        val curMotion = _uiState.value.middleParams as? MiddleParameters.MotionTracking ?: motion
                        _uiState.value = _uiState.value.copy(
                            middleParams = curMotion.copy(trackingProgress = pct)
                        )
                    }
                )
            } else {
                for (step in 1..20) {
                    delay(30)
                    val pct = (step / 20f)
                    val curMotion = _uiState.value.middleParams as? MiddleParameters.MotionTracking ?: motion
                    _uiState.value = _uiState.value.copy(
                        middleParams = curMotion.copy(trackingProgress = pct)
                    )
                }
                val anchorStep = (((anchorTime - clipStart) / clipDur) * 30).toInt().coerceIn(0, 30)
                val kfs = (0..30).map { i ->
                    val dt = (i - anchorStep) / 30f
                    val kx = (motion.targetX + 0.04f * kotlin.math.sin(dt * 3.14159f * 2f)).toFloat().coerceIn(0.05f, 0.95f)
                    val ky = (motion.targetY + 0.025f * kotlin.math.sin(dt * 3.14159f * 1.5f)).toFloat().coerceIn(0.05f, 0.95f)
                    Point2D(kx, ky)
                }
                val cnts = kfs.map { p ->
                    com.aiditor.app.util.SubjectOutliner.generateDefaultContour(p.x, p.y, motion.boxWidth, motion.boxHeight, 24)
                }
                com.aiditor.app.util.TrackingResult(kfs, cnts)
            }

            val keyframes = trackingResult.keyframes
            val contours = trackingResult.contours

            val isLock = motion.isTargetLockActive || motion.trackingMode == "target_lock"
            val newOverlay = TimelineOverlay(
                id = "ov_tracking_${System.currentTimeMillis()}",
                type = if (isLock) OverlayType.STABILIZATION_EFFECT else OverlayType.TRACKING_EFFECT,
                label = if (isLock) "Stabilize Lock" else "Motion Tracker",
                startTimeSeconds = clipStart,
                durationSeconds = clipDur,
                trackingKeyframes = keyframes,
                targetX = motion.targetX,
                targetY = motion.targetY,
                boxWidth = motion.boxWidth,
                boxHeight = motion.boxHeight,
                subjectContour = contours.firstOrNull() ?: emptyList(),
                trackingContours = contours
            )

            val nextTrackingMode = if (isLock) ActiveTrackingMode.MOTION_STABILIZATION else ActiveTrackingMode.MOTION_TRACKING
            val updatedOverlays = _uiState.value.overlays.filter {
                it.type != OverlayType.TRACKING_EFFECT && it.type != OverlayType.STABILIZATION_EFFECT
            } + newOverlay

            _uiState.value = _uiState.value.copy(
                trackingMode = nextTrackingMode,
                overlays = updatedOverlays,
                middleParams = motion.copy(
                    isTrackingRunning = false,
                    trackingProgress = 1.0f,
                    isTrackingDone = true,
                    trackingKeyframes = keyframes,
                    subjectContour = contours.firstOrNull() ?: emptyList(),
                    trackingContours = contours
                )
            )
            persistCurrentProject()
        }
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
        persistCurrentProject()
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

        val selClip = _uiState.value.clips.find { it.isSelected }
            ?: _uiState.value.clips.firstOrNull()

        val defaultMiddle = when (tool) {
            ToolType.OPTICAL_FLOW -> MiddleParameters.OpticalFlow(
                isEnabled = selClip?.isOpticalFlowEnabled ?: false,
                targetFps = selClip?.opticalFlowFps ?: 60,
                flowMode = selClip?.opticalFlowMode ?: "mci",
                cachedVideoUri = selClip?.opticalFlowCachedUri
            )
            ToolType.BEAT_SYNC -> MiddleParameters.BeatSync()
            ToolType.MOTION_TRACKING -> MiddleParameters.MotionTracking()
            ToolType.SPEED_RAMP -> MiddleParameters.SpeedRamp(
                curveControlPoints = selClip?.speedCurvePoints?.ifEmpty { null } ?: listOf(
                    CurveControlPoint(0.0f, 1.0f),
                    CurveControlPoint(0.35f, 0.2f),
                    CurveControlPoint(0.7f, selClip?.speedMultiplier ?: 2.5f),
                    CurveControlPoint(1.0f, 1.0f)
                ),
                maxSpeedMultiplier = selClip?.speedMultiplier ?: 2.5f
            )
            ToolType.COLOR_GRADE -> selClip?.colorGrade ?: MiddleParameters.ColorGrade()
            ToolType.ROTOSCOPE -> MiddleParameters.Rotoscope()
        }

        val nextTrackingMode = if (tool == ToolType.MOTION_TRACKING) {
            val isLock = (defaultMiddle as? MiddleParameters.MotionTracking)?.trackingMode == "target_lock" ||
                (defaultMiddle as? MiddleParameters.MotionTracking)?.isTargetLockActive == true
            if (isLock) ActiveTrackingMode.MOTION_STABILIZATION else ActiveTrackingMode.MOTION_TRACKING
        } else if (_uiState.value.trackingMode != ActiveTrackingMode.NONE) {
            _uiState.value.trackingMode
        } else {
            ActiveTrackingMode.NONE
        }

        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            middleParams = defaultMiddle,
            trackingMode = nextTrackingMode
        )

        refreshVisualizerData()
    }

    fun applyCurrentToolToTimeline() {
        pushUndoState()
        val tool = _uiState.value.activeTool ?: return
        val mid = _uiState.value.middleParams
        val selId = _uiState.value.selectedClipId
        val currentClips = _uiState.value.clips.toMutableList()
        val targetIdx = if (selId != null) currentClips.indexOfFirst { it.id == selId } else 0

        if (targetIdx != -1 && targetIdx < currentClips.size) {
            val clip = currentClips[targetIdx]
            val updatedClip = when (tool) {
                ToolType.OPTICAL_FLOW -> {
                    val flow = mid as? MiddleParameters.OpticalFlow ?: MiddleParameters.OpticalFlow()
                    clip.copy(
                        isOpticalFlowEnabled = flow.isEnabled,
                        opticalFlowFps = flow.targetFps,
                        opticalFlowMode = flow.flowMode,
                        opticalFlowCachedUri = flow.cachedVideoUri,
                        speedMultiplier = if (flow.isEnabled && flow.slowMoFactor < 1.0f) flow.slowMoFactor else clip.speedMultiplier
                    )
                }
                ToolType.SPEED_RAMP -> {
                    val ramp = mid as? MiddleParameters.SpeedRamp ?: MiddleParameters.SpeedRamp()
                    val baseSpan = (clip.outPointSeconds - clip.inPointSeconds).coerceAtLeast(0.1)
                    val newDur = (baseSpan / ramp.maxSpeedMultiplier).coerceAtLeast(0.1)
                    clip.copy(
                        speedMultiplier = ramp.maxSpeedMultiplier,
                        durationSeconds = newDur,
                        speedCurvePoints = ramp.curveControlPoints
                    )
                }
                ToolType.COLOR_GRADE -> {
                    val grade = mid as? MiddleParameters.ColorGrade ?: MiddleParameters.ColorGrade()
                    clip.copy(colorGrade = grade)
                }
                ToolType.MOTION_TRACKING -> {
                    val motion = mid as? MiddleParameters.MotionTracking ?: MiddleParameters.MotionTracking()
                    val isLock = motion.isTargetLockActive || motion.trackingMode == "target_lock"
                    val nextTracking = if (isLock) {
                        ActiveTrackingMode.MOTION_STABILIZATION
                    } else {
                        ActiveTrackingMode.MOTION_TRACKING
                    }
                    val hasTrackingOv = _uiState.value.overlays.any {
                        it.type == OverlayType.TRACKING_EFFECT || it.type == OverlayType.STABILIZATION_EFFECT
                    }
                    if (!hasTrackingOv) {
                        val newOv = TimelineOverlay(
                            id = "ov_tracking_${System.currentTimeMillis()}",
                            type = if (isLock) OverlayType.STABILIZATION_EFFECT else OverlayType.TRACKING_EFFECT,
                            label = if (isLock) "Stabilize Lock" else "Motion Tracker",
                            startTimeSeconds = clip.inPointSeconds,
                            durationSeconds = clip.durationSeconds
                        )
                        _uiState.value = _uiState.value.copy(overlays = _uiState.value.overlays + newOv)
                    }
                    _uiState.value = _uiState.value.copy(trackingMode = nextTracking)
                    if (!motion.isTrackingDone && !motion.isTrackingRunning) {
                        startMotionTracking()
                    }
                    clip
                }
                ToolType.ROTOSCOPE -> {
                    clip
                }
                else -> clip
            }
            currentClips[targetIdx] = updatedClip
            _uiState.value = _uiState.value.copy(clips = currentClips)
            persistCurrentProject()
        }

        closeToolInspector()
    }

    fun closeToolInspector() {
        val selClip = _uiState.value.clips.find { it.isSelected }
            ?: _uiState.value.clips.firstOrNull()
        val curMid = _uiState.value.middleParams
        val fallbackMiddle = if (curMid is MiddleParameters.MotionTracking && (curMid.isTrackingDone || curMid.isTrackingRunning)) {
            curMid
        } else {
            selClip?.colorGrade ?: curMid
        }
        _uiState.value = _uiState.value.copy(
            activeTool = null,
            activeVisualizerData = null,
            middleParams = fallbackMiddle
        )
    }

    fun updateInputParams(params: InputParameters) {
        _uiState.value = _uiState.value.copy(inputParams = params)
        refreshVisualizerData()
    }

    fun updateMiddleParams(params: MiddleParameters) {
        val nextTracking = if (params is MiddleParameters.MotionTracking) {
            val isLock = params.isTargetLockActive || params.trackingMode == "target_lock"
            if (isLock) ActiveTrackingMode.MOTION_STABILIZATION else ActiveTrackingMode.MOTION_TRACKING
        } else {
            _uiState.value.trackingMode
        }
        _uiState.value = _uiState.value.copy(
            middleParams = params,
            trackingMode = nextTracking
        )
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
            val selClip = _uiState.value.clips.find { it.isSelected } ?: _uiState.value.clips.firstOrNull()
            val source = selClip?.opticalFlowCachedUri?.takeIf { it.isNotBlank() && java.io.File(it).exists() }
                ?: selClip?.sourcePath?.takeIf { it.isNotBlank() }
                ?: _uiState.value.project?.videoPath?.takeIf { it.isNotBlank() }
                ?: _uiState.value.inputParams.sourcePath

            val inSec = selClip?.inPointSeconds ?: _uiState.value.inputParams.inPointSeconds
            val outSec = selClip?.outPointSeconds ?: _uiState.value.inputParams.outPointSeconds ?: _uiState.value.totalDurationSeconds
            val colorGrade = selClip?.colorGrade ?: (_uiState.value.middleParams as? MiddleParameters.ColorGrade)

            editingRepository.exportVideoProgress(
                toolType = tool,
                input = _uiState.value.inputParams.copy(
                    sourcePath = source,
                    inPointSeconds = inSec,
                    outPointSeconds = outSec,
                    muteAudio = _uiState.value.isAudioMuted
                ),
                middle = colorGrade ?: _uiState.value.middleParams,
                output = _uiState.value.outputParams.copy(
                    resolution = settings.resolution,
                    fps = settings.fps
                ),
                durationSeconds = (outSec - inSec).coerceAtLeast(0.1)
            ).catch { e ->
                _uiState.value = _uiState.value.copy(
                    activeExportJob = ExportJob(
                        jobId = "error_${System.currentTimeMillis()}",
                        tool = tool.title,
                        status = ExportStatus.FAILED,
                        progressPercentage = 0f,
                        message = "Export failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                        outputPath = "",
                        startedAt = System.currentTimeMillis()
                    )
                )
            }.collect { job ->
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

    private var opticalFlowJob: Job? = null

    /**
     * Triggers asynchronous on-device 60 FPS Optical Flow rendering and background caching.
     * Streams progress (0-100%) to the UI state.
     * Upon completion, automatically updates the clip and preview with the cached 60 FPS video!
     */
    fun renderOpticalFlow() {
        val selClip = _uiState.value.clips.find { it.isSelected } ?: _uiState.value.clips.firstOrNull()
        val source = selClip?.sourcePath?.ifBlank { _uiState.value.inputParams.sourcePath }
            ?: _uiState.value.inputParams.sourcePath
        val flowParams = _uiState.value.middleParams as? MiddleParameters.OpticalFlow ?: MiddleParameters.OpticalFlow()

        opticalFlowJob?.cancel()
        opticalFlowJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                middleParams = flowParams.copy(
                    isRendering = true,
                    renderProgress = 0.05f,
                    renderStatusMessage = "Starting 60 FPS Optical Flow..."
                )
            )

            editingRepository.renderOpticalFlowProgress(
                sourcePath = source,
                targetFps = flowParams.targetFps,
                flowMode = flowParams.flowMode,
                scdThreshold = flowParams.scdThreshold,
                inSec = selClip?.inPointSeconds ?: 0.0,
                outSec = selClip?.outPointSeconds ?: 10.0,
                slowMoFactor = flowParams.slowMoFactor
            ).collect { job ->
                when (job.status) {
                    ExportStatus.COMPLETED -> {
                        val cachedUri = job.outputPath
                        val currentClips = _uiState.value.clips.toMutableList()
                        val targetIdx = if (selClip != null) currentClips.indexOfFirst { it.id == selClip.id } else 0
                        if (targetIdx != -1 && targetIdx < currentClips.size) {
                            currentClips[targetIdx] = currentClips[targetIdx].copy(
                                isOpticalFlowEnabled = true,
                                opticalFlowFps = flowParams.targetFps,
                                opticalFlowMode = flowParams.flowMode,
                                opticalFlowCachedUri = cachedUri
                            )
                        }

                        _uiState.value = _uiState.value.copy(
                            clips = currentClips,
                            middleParams = flowParams.copy(
                                isEnabled = true,
                                isRendering = false,
                                renderProgress = 1.0f,
                                renderStatusMessage = "✓ 60 FPS Video Cached Successfully!",
                                cachedVideoUri = cachedUri
                            )
                        )
                    }
                    ExportStatus.FAILED -> {
                        _uiState.value = _uiState.value.copy(
                            middleParams = flowParams.copy(
                                isRendering = false,
                                renderStatusMessage = job.message.ifEmpty { "Optical Flow failed" }
                            )
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            middleParams = flowParams.copy(
                                isRendering = true,
                                renderProgress = (job.progressPercentage / 100f).coerceIn(0f, 1f),
                                renderStatusMessage = job.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun cancelOpticalFlow() {
        opticalFlowJob?.cancel()
        opticalFlowJob = null
        val flowParams = _uiState.value.middleParams as? MiddleParameters.OpticalFlow ?: MiddleParameters.OpticalFlow()
        _uiState.value = _uiState.value.copy(
            middleParams = flowParams.copy(
                isRendering = false,
                renderProgress = 0f,
                renderStatusMessage = "Cancelled"
            )
        )
    }

    private var rotoscopeJob: Job? = null

    /**
     * Triggers asynchronous on-device Rotoscope AI cutout rendering and background caching.
     * Streams progress (0-100%) to the UI state.
     */
    fun renderRotoscope() {
        val selClip = _uiState.value.clips.find { it.isSelected } ?: _uiState.value.clips.firstOrNull()
        val source = selClip?.sourcePath?.ifBlank { _uiState.value.inputParams.sourcePath }
            ?: _uiState.value.inputParams.sourcePath
        val rotoParams = _uiState.value.middleParams as? MiddleParameters.Rotoscope ?: MiddleParameters.Rotoscope()

        rotoscopeJob?.cancel()
        rotoscopeJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                middleParams = rotoParams.copy(
                    isRendering = true,
                    renderProgress = 0.05f,
                    renderStatusMessage = "Extracting temporal matte..."
                )
            )

            editingRepository.renderRotoscopeProgress(
                sourcePath = source,
                preset = rotoParams.preset,
                neonColor = rotoParams.neonColor,
                outlineWidth = rotoParams.outlineWidth,
                glowIntensity = rotoParams.glowIntensity,
                textContent = rotoParams.textContent,
                inSec = selClip?.inPointSeconds ?: 0.0,
                outSec = selClip?.outPointSeconds ?: 10.0
            ).collect { job ->
                when (job.status) {
                    ExportStatus.COMPLETED -> {
                        val cachedMask = job.outputPath
                        _uiState.value = _uiState.value.copy(
                            middleParams = rotoParams.copy(
                                isRendering = false,
                                renderProgress = 1.0f,
                                renderStatusMessage = "✓ Cutout Cached Successfully!",
                                cachedMaskUri = cachedMask
                            )
                        )
                    }
                    ExportStatus.FAILED -> {
                        _uiState.value = _uiState.value.copy(
                            middleParams = rotoParams.copy(
                                isRendering = false,
                                renderStatusMessage = job.message.ifEmpty { "Rotoscope failed" }
                            )
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            middleParams = rotoParams.copy(
                                isRendering = true,
                                renderProgress = (job.progressPercentage / 100f).coerceIn(0f, 1f),
                                renderStatusMessage = job.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun cancelRotoscope() {
        rotoscopeJob?.cancel()
        rotoscopeJob = null
        val rotoParams = _uiState.value.middleParams as? MiddleParameters.Rotoscope ?: MiddleParameters.Rotoscope()
        _uiState.value = _uiState.value.copy(
            middleParams = rotoParams.copy(
                isRendering = false,
                renderProgress = 0f,
                renderStatusMessage = "Cancelled"
            )
        )
    }
}
