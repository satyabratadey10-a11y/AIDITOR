package com.aiditor.app.bridge

import com.aiditor.app.data.model.*

/**
 * Generates exact production FFmpeg commands for all editing tools,
 * supporting input range seeking, filtergraph processing, and output encoding.
 */
object FfmpegProcessBridge {

    fun buildCommand(
        toolType: ToolType,
        input: InputParameters,
        middle: MiddleParameters,
        output: OutputParameters
    ): List<String> {
        val cmd = mutableListOf("ffmpeg", "-hide_banner", "-y")

        // Input
        if (input.inPointSeconds > 0) {
            cmd.add("-ss")
            cmd.add(String.format("%.3f", input.inPointSeconds))
        }
        cmd.add("-i")
        cmd.add(input.sourcePath.ifEmpty { "input.mp4" })

        input.outPointSeconds?.let { outPt ->
            if (outPt > input.inPointSeconds) {
                cmd.add("-t")
                cmd.add(String.format("%.3f", outPt - input.inPointSeconds))
            }
        }

        // Filtergraph
        val filters = mutableListOf<String>()

        when (middle) {
            is MiddleParameters.OpticalFlow -> {
                filters.add("minterpolate=fps=${middle.targetFps}:mi_mode=${middle.flowMode}:scd=fd:scd_threshold=${middle.scdThreshold}")
                if (middle.colorGrade) {
                    filters.add("eq=contrast=1.2:saturation=0.0")
                }
            }
            is MiddleParameters.BeatSync -> {
                filters.add("eq=contrast=1.3:saturation=0.0")
            }
            is MiddleParameters.MotionTracking -> {
                val boxX = "(w*${middle.targetX}-40)"
                val boxY = "(h*${middle.targetY}-40)"
                filters.add("drawbox=x=$boxX:y=$boxY:w=80:h=80:color=white@0.9:t=2")
                filters.add("drawtext=text='[${middle.hudTitle}]':x=$boxX:y=($boxY-24):fontsize=20:fontcolor=white")
            }
            is MiddleParameters.SpeedRamp -> {
                val mult = 1.0f / middle.maxSpeedMultiplier.coerceAtLeast(0.2f)
                filters.add("setpts=$mult*PTS")
            }
            is MiddleParameters.ColorGrade -> {
                filters.add("eq=contrast=${middle.contrast}:exposure=${middle.exposure}:saturation=${middle.saturation}:brightness=${middle.brightness}:gamma=${middle.gamma}")
                filters.add("unsharp=5:5:0.8:5:5:0.0")
            }
            is MiddleParameters.Rotoscope -> {
                if (middle.preset == "neon_saber") {
                    filters.add("edgedetect=low=0.1:high=0.4,negate")
                } else {
                    filters.add("drawtext=text='${middle.textContent}':x=(w-text_w)/2:y=(h-text_h)/2:fontsize=64:fontcolor=white@0.9")
                }
            }
        }

        if (filters.isNotEmpty()) {
            cmd.add("-vf")
            cmd.add(filters.joinToString(","))
        }

        // Output
        cmd.add("-c:v")
        cmd.add(output.codec)
        cmd.add("-crf")
        cmd.add(output.crf.toString())
        cmd.add("-pix_fmt")
        cmd.add("yuv420p")
        cmd.add("-r")
        cmd.add(output.fps.toString())

        if (input.muteAudio) {
            cmd.add("-an")
        } else {
            cmd.add("-c:a")
            cmd.add("aac")
        }

        cmd.add("-movflags")
        cmd.add("+faststart")
        cmd.add(output.outputPath.ifEmpty { "output_render.mp4" })

        return cmd
    }
}
