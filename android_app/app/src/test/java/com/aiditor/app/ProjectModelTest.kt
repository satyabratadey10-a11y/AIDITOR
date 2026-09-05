package com.aiditor.app

import com.aiditor.app.bridge.FfmpegProcessBridge
import com.aiditor.app.data.model.*
import com.aiditor.app.data.repository.ProjectRepository
import com.aiditor.app.data.repository.VideoEditingRepository
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
}
