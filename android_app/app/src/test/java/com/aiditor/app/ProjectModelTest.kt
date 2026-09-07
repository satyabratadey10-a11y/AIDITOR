package com.aiditor.app

import com.aiditor.app.bridge.FfmpegProcessBridge
import com.aiditor.app.data.model.*
import com.aiditor.app.data.repository.LocalProjectStorage
import com.aiditor.app.data.repository.ProjectRepository
import com.aiditor.app.data.repository.VideoEditingRepository
import com.aiditor.app.ui.screens.workspace.WorkspaceViewModel
import com.aiditor.app.util.LowMemoryThumbnailCache
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProjectModelTest {

    @Test
    fun testDefaultProjectsListIsEmpty() {
        val projects = ProjectRepository.getDefaultProjects()
        assertTrue("Projects list should be empty by default with zero placeholder projects", projects.isEmpty())
    }

    @Test
    fun testProjectCreationWithRealMetadata() = runBlocking {
        val repo = ProjectRepository()
        val created = repo.createProject(
            name = "Test Real Project",
            videoPath = "content://media/external/video/media/123",
            fileSizeBytes = 52428800L,
            fileSizeFormatted = "50.0 MB",
            durationSeconds = 18.5,
            width = 1920,
            height = 1080
        )
        assertNotNull(created.id)
        assertEquals("Test Real Project", created.name)
        assertEquals("50.0 MB", created.fileSizeFormatted)
        assertEquals(18.5, created.durationSeconds, 0.01)
        assertEquals(1920, created.width)
        assertEquals(1080, created.height)
        assertNotNull(created.createdAt)
        assertNotNull(created.modifiedAt)
        assertTrue(repo.projects.value.isNotEmpty())
        assertTrue(created.clips.isNotEmpty())
        assertEquals(18.5, created.clips.first().durationSeconds, 0.01)
    }

    @Test
    fun testFfmpegCommandBuilderForOpticalFlow() {
        val input = InputParameters(sourcePath = "input.mp4", inPointSeconds = 2.0, outPointSeconds = 6.0)
        val middle = MiddleParameters.OpticalFlow(targetFps = 60, flowMode = "mci")
        val output = OutputParameters(outputPath = "out.mp4", resolution = "1080p", fps = 60)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.OPTICAL_FLOW, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("ffmpeg"))
        assertTrue(cmdStr.contains("-ss 2.000"))
        assertTrue(cmdStr.contains("-t 4.000"))
        assertTrue(cmdStr.contains("minterpolate=fps=60"))
        assertTrue(cmdStr.contains("out.mp4"))
    }

    @Test
    fun testFfmpegCommandBuilderForColorGradeMonochrome() {
        val input = InputParameters(sourcePath = "input.mp4")
        val middle = MiddleParameters.ColorGrade(contrast = 1.3f, saturation = 0.0f)
        val output = OutputParameters(outputPath = "monochrome.mp4", fps = 60)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.COLOR_GRADE, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("eq=contrast=1.3"))
        assertTrue(cmdStr.contains("saturation=0.0"))
    }

    @Test
    fun testVideoEditingRepositoryVisualizers() = runBlocking {
        val repo = VideoEditingRepository()

        // Optical Flow
        val flowVis = repo.getVisualizerData(
            ToolType.OPTICAL_FLOW,
            InputParameters(),
            MiddleParameters.OpticalFlow(targetFps = 60)
        )
        assertTrue(flowVis is ToolVisualizerData.OpticalFlow)
        assertEquals(60, (flowVis as ToolVisualizerData.OpticalFlow).targetFps)
        assertTrue(flowVis.vectors.isNotEmpty())

        // Beat Sync
        val beatVis = repo.getVisualizerData(
            ToolType.BEAT_SYNC,
            InputParameters(),
            MiddleParameters.BeatSync(vibe = "aggressive_drift")
        )
        assertTrue(beatVis is ToolVisualizerData.BeatSync)
        assertTrue((beatVis as ToolVisualizerData.BeatSync).waveform.isNotEmpty())
        assertTrue(beatVis.beats.isNotEmpty())

        // Motion Track
        val trackVis = repo.getVisualizerData(
            ToolType.MOTION_TRACKING,
            InputParameters(),
            MiddleParameters.MotionTracking()
        )
        assertTrue(trackVis is ToolVisualizerData.MotionTracking)
        assertTrue((trackVis as ToolVisualizerData.MotionTracking).keyframes.isNotEmpty())

        // Speed Ramp
        val rampVis = repo.getVisualizerData(
            ToolType.SPEED_RAMP,
            InputParameters(),
            MiddleParameters.SpeedRamp()
        )
        assertTrue(rampVis is ToolVisualizerData.SpeedRamp)
        assertTrue((rampVis as ToolVisualizerData.SpeedRamp).samples.isNotEmpty())

        // Color Grade
        val gradeVis = repo.getVisualizerData(
            ToolType.COLOR_GRADE,
            InputParameters(),
            MiddleParameters.ColorGrade()
        )
        assertTrue(gradeVis is ToolVisualizerData.ColorGrade)
        assertEquals(256, (gradeVis as ToolVisualizerData.ColorGrade).toneCurve.size)

        // Rotoscope
        val rotoVis = repo.getVisualizerData(
            ToolType.ROTOSCOPE,
            InputParameters(),
            MiddleParameters.Rotoscope()
        )
        assertTrue(rotoVis is ToolVisualizerData.Rotoscope)
        assertTrue((rotoVis as ToolVisualizerData.Rotoscope).contourPoints.isNotEmpty())
    }

    @Test
    fun testStandaloneOnDeviceExportProgressFlow() = runBlocking {
        val repo = VideoEditingRepository()
        val input = InputParameters(sourcePath = "test_video.mp4", inPointSeconds = 1.0, outPointSeconds = 5.0)
        val middle = MiddleParameters.ColorGrade()
        val output = OutputParameters(outputPath = "build/export_test.mp4")

        val flow = repo.exportVideoProgress(ToolType.COLOR_GRADE, input, middle, output)
        val jobSteps = flow.toList()

        assertTrue("Export must emit progress updates", jobSteps.isNotEmpty())
        assertEquals("First event must be QUEUED", ExportStatus.QUEUED, jobSteps.first().status)
        assertEquals("Last event must be COMPLETED", ExportStatus.COMPLETED, jobSteps.last().status)
        assertEquals("Final progress must reach 100%", 100f, jobSteps.last().progressPercentage, 0.1f)
        assertNotNull("Output path must be specified", jobSteps.last().outputPath)
    }

    @Test
    fun testWorkspaceClipSplitAtPlayhead() {
        val vm = WorkspaceViewModel()
        val project = Project(
            id = "proj_test",
            name = "Test Clip Split",
            videoPath = "path/to/vid.mp4",
            durationSeconds = 10.0,
            fileSizeBytes = 1024L,
            fileSizeFormatted = "1 KB",
            width = 1920,
            height = 1080,
            fps = 30.0,
            clips = listOf(
                TimelineClip(
                    id = "c1",
                    title = "Main Clip",
                    sourcePath = "vid.mp4",
                    inPointSeconds = 0.0,
                    outPointSeconds = 10.0,
                    durationSeconds = 10.0,
                    isSelected = true
                )
            )
        )
        vm.loadProject(project)
        assertEquals(1, vm.uiState.value.clips.size)

        // Seek to 4.5 seconds and split
        vm.seekTo(4.5)
        vm.splitClipAtPlayhead()

        val clips = vm.uiState.value.clips
        assertEquals("Clips must be split into 2 segments", 2, clips.size)
        assertEquals(0.0, clips[0].inPointSeconds, 0.01)
        assertEquals(4.5, clips[0].outPointSeconds, 0.01)
        assertEquals(4.5, clips[1].inPointSeconds, 0.01)
        assertEquals(10.0, clips[1].outPointSeconds, 0.01)
        assertTrue("Newly created split clip must be selected", clips[1].isSelected)
    }

    @Test
    fun testWorkspaceClipDuplicateAndDelete() {
        val vm = WorkspaceViewModel()
        val project = Project(
            id = "proj_test_dup",
            name = "Test Dup",
            videoPath = "path/to/vid.mp4",
            durationSeconds = 10.0,
            fileSizeBytes = 1024L,
            fileSizeFormatted = "1 KB",
            width = 1920,
            height = 1080,
            fps = 30.0,
            clips = listOf(
                TimelineClip(
                    id = "c1",
                    title = "Main Clip",
                    sourcePath = "vid.mp4",
                    inPointSeconds = 0.0,
                    outPointSeconds = 10.0,
                    isSelected = true
                )
            )
        )
        vm.loadProject(project)

        // Duplicate
        vm.duplicateSelectedClip()
        assertEquals(2, vm.uiState.value.clips.size)

        // Delete duplicated
        vm.deleteSelectedClip()
        assertEquals(1, vm.uiState.value.clips.size)
    }

    @Test
    fun testWorkspaceUndoRedoStack() {
        val vm = WorkspaceViewModel()
        val project = Project(
            id = "proj_undo",
            name = "Test Undo",
            videoPath = "vid.mp4",
            durationSeconds = 10.0,
            fileSizeBytes = 1000L,
            fileSizeFormatted = "1 KB",
            width = 1920,
            height = 1080,
            fps = 30.0
        )
        vm.loadProject(project)
        assertFalse(vm.uiState.value.canUndo)

        // Toggle tracking mode
        vm.setTrackingMode(ActiveTrackingMode.MOTION_TRACKING)
        assertEquals(ActiveTrackingMode.MOTION_TRACKING, vm.uiState.value.trackingMode)
        assertTrue(vm.uiState.value.canUndo)

        // Undo
        vm.undo()
        assertEquals(ActiveTrackingMode.NONE, vm.uiState.value.trackingMode)
        assertTrue(vm.uiState.value.canRedo)

        // Redo
        vm.redo()
        assertEquals(ActiveTrackingMode.MOTION_TRACKING, vm.uiState.value.trackingMode)
    }

    @Test
    fun testLowMemoryThumbnailCacheClear() {
        LowMemoryThumbnailCache.clearCache()
        val cached = LowMemoryThumbnailCache.getFromCache("sample.mp4", 1.0)
        assertNull("Cache should return null after being cleared", cached)
    }
}
