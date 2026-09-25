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

    @Test
    fun testOpticalFlowCacheCommandBuilder() {
        val cmd = FfmpegProcessBridge.buildOpticalFlowCacheCommand(
            inputPath = "input.mp4",
            outputPath = "cached_60fps.mp4",
            targetFps = 60,
            flowMode = "mci",
            scdThreshold = 10.0,
            inPointSeconds = 1.0,
            outPointSeconds = 4.0,
            slowMoFactor = 0.5f
        )
        val cmdStr = cmd.joinToString(" ")
        assertTrue(cmdStr.contains("ffmpeg"))
        assertTrue(cmdStr.contains("-ss 1.000"))
        assertTrue(cmdStr.contains("-t 3.000"))
        assertTrue(cmdStr.contains("minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:me=epzs:mb_size=16:search_param=16:vsbmc=0:scd=fdiff:scd_threshold=10.0"))
        assertTrue(cmdStr.contains("setpts=2.0*PTS"))
        assertTrue(cmdStr.contains("-preset ultrafast"))
        assertTrue(cmdStr.contains("cached_60fps.mp4"))
    }

    @Test
    fun testOpticalFlowRenderProgressFlow() = runBlocking {
        val repo = VideoEditingRepository()
        val jobs = mutableListOf<ExportJob>()
        repo.renderOpticalFlowProgress(
            sourcePath = "", // Triggers fallback standalone progressive pipeline
            targetFps = 60,
            flowMode = "mci"
        ).collect { job ->
            jobs.add(job)
        }

        assertTrue(jobs.isNotEmpty())
        assertEquals(ExportStatus.INITIALIZING, jobs.first().status)
        assertEquals(ExportStatus.COMPLETED, jobs.last().status)
        assertEquals(100f, jobs.last().progressPercentage, 0.01f)
        assertTrue(jobs.last().outputPath.endsWith(".mp4"))
    }

    @Test
    fun testFfmpegCommandBuilderForMotionTrackingHud() {
        val input = InputParameters(sourcePath = "input.mp4")
        val middle = MiddleParameters.MotionTracking(
            trackingMode = "hud_callout",
            targetX = 0.6f,
            targetY = 0.4f,
            boxWidth = 0.25f,
            boxHeight = 0.25f,
            hudTitle = "TARGET LOCKED",
            hudSubtitle = "TRACKING 60FPS"
        )
        val output = OutputParameters(outputPath = "tracked.mp4", fps = 60)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.MOTION_TRACKING, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("ffmpeg"))
        assertTrue(cmdStr.contains("drawbox"))
        assertTrue(cmdStr.contains("drawtext"))
        assertTrue(cmdStr.contains("TARGET LOCKED"))
        assertTrue(cmdStr.contains("tracked.mp4"))
    }

    @Test
    fun testFfmpegCommandBuilderForMotionTrackingTargetLock() {
        val input = InputParameters(sourcePath = "input.mp4")
        val middle = MiddleParameters.MotionTracking(
            trackingMode = "target_lock",
            isTargetLockActive = true,
            targetX = 0.7f,
            targetY = 0.3f
        )
        val output = OutputParameters(outputPath = "stabilized.mp4", fps = 60)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.MOTION_TRACKING, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("crop="))
        assertTrue(cmdStr.contains("scale="))
        assertTrue(cmdStr.contains("stabilized.mp4"))
    }

    @Test
    fun testFfmpegCommandBuilderForRotoscopeNeonSaber() {
        val input = InputParameters(sourcePath = "input.mp4")
        val middle = MiddleParameters.Rotoscope(
            preset = "neon_saber",
            neonColor = "#00F0FF",
            outlineWidth = 4.0f,
            glowIntensity = 1.5f
        )
        val output = OutputParameters(outputPath = "neon.mp4", fps = 30)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.ROTOSCOPE, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("edgedetect"))
        assertTrue(cmdStr.contains("negate"))
        assertTrue(cmdStr.contains("neon.mp4"))
    }

    @Test
    fun testFfmpegCommandBuilderForRotoscopeBehindText() {
        val input = InputParameters(sourcePath = "input.mp4")
        val middle = MiddleParameters.Rotoscope(
            preset = "behind_text",
            textContent = "CYBERPUNK"
        )
        val output = OutputParameters(outputPath = "behind_text.mp4", fps = 30)

        val cmd = FfmpegProcessBridge.buildCommand(ToolType.ROTOSCOPE, input, middle, output)
        val cmdStr = cmd.joinToString(" ")

        assertTrue(cmdStr.contains("drawtext=text='CYBERPUNK'"))
        assertTrue(cmdStr.contains("behind_text.mp4"))
    }

    @Test
    fun testRotoscopeRenderProgressFlow() = runBlocking {
        val repo = VideoEditingRepository()
        val jobs = mutableListOf<ExportJob>()
        repo.renderRotoscopeProgress(
            sourcePath = "", // Triggers fallback standalone progressive pipeline
            preset = "neon_saber",
            neonColor = "#39FF14"
        ).collect { job ->
            jobs.add(job)
        }

        assertTrue(jobs.isNotEmpty())
        assertEquals(ExportStatus.INITIALIZING, jobs.first().status)
        assertEquals(ExportStatus.COMPLETED, jobs.last().status)
        assertEquals(100f, jobs.last().progressPercentage, 0.01f)
        assertTrue(jobs.last().outputPath.contains("roto_neon_saber"))
    }

    @Test
    fun testSubjectOutlinerDefaultContour() {
        val contour = com.aiditor.app.util.SubjectOutliner.generateDefaultContour(
            targetX = 0.5f,
            targetY = 0.5f,
            boxWidth = 0.16f,
            boxHeight = 0.14f,
            numRays = 24
        )
        assertEquals(24, contour.size)
        contour.forEach { pt ->
            assertTrue("Contour X should be in bounds", pt.x in 0.0f..1.0f)
            assertTrue("Contour Y should be in bounds", pt.y in 0.0f..1.0f)
        }
    }

    @Test
    fun testMotionTrackerEngineAdvancedFallback() = runBlocking {
        // Without an active video context, should return valid simulated keyframes and contours
        val result = com.aiditor.app.util.MotionTrackerEngine.trackSubjectAdvanced(
            context = null,
            videoPath = "",
            startTimeSeconds = 0.0,
            durationSeconds = 5.0,
            initialX = 0.5f,
            initialY = 0.5f,
            boxWidth = 0.16f,
            boxHeight = 0.14f,
            numSamples = 10
        )
        assertNotNull(result)
        assertEquals(10, result.keyframes.size)
        assertEquals(10, result.contours.size)
        assertEquals(24, result.contours.first().size)
    }

    @Test
    fun testWorkspaceClipMoveAndTransform() {
        val vm = WorkspaceViewModel()
        val project = Project(
            id = "proj_transform",
            name = "Test Transform",
            videoPath = "vid.mp4",
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
                    timelineStartSeconds = 0.0,
                    trackIndex = 0,
                    scale = 1.0f,
                    panX = 0.0f,
                    panY = 0.0f,
                    isSelected = true
                )
            )
        )
        vm.loadProject(project)

        // Move clip along timeline and shift to Track 1
        vm.updateClipPosition("c1", newTimelineStartSeconds = 3.5, newTrackIndex = 1)
        val movedClip = vm.uiState.value.clips.first()
        assertEquals(3.5, movedClip.timelineStartSeconds, 0.01)
        assertEquals(1, movedClip.trackIndex)
        assertEquals(13.5, vm.uiState.value.totalDurationSeconds, 0.01)

        // Scale and Pan
        vm.updateClipTransform("c1", scale = 1.8f, panX = 45f, panY = -30f)
        val transformedClip = vm.uiState.value.clips.first()
        assertEquals(1.8f, transformedClip.scale, 0.01f)
        assertEquals(45f, transformedClip.panX, 0.01f)
        assertEquals(-30f, transformedClip.panY, 0.01f)
    }
}
