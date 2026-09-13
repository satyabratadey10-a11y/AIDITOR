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
                val flowFilter = if (middle.flowMode == "blend") {
                    "minterpolate=fps=${middle.targetFps}:mi_mode=blend:scd=fdiff:scd_threshold=${middle.scdThreshold}"
                } else {
                    "minterpolate=fps=${middle.targetFps}:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:me=epzs:mb_size=16:search_param=16:vsbmc=0:scd=fdiff:scd_threshold=${middle.scdThreshold}"
                }
                filters.add(flowFilter)
                if (middle.slowMoFactor < 1.0f) {
                    val ptsMult = 1.0f / middle.slowMoFactor.coerceAtLeast(0.1f)
                    filters.add("setpts=$ptsMult*PTS")
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
                filters.add("eq=contrast=${middle.contrast}:brightness=${middle.brightness}:saturation=${middle.saturation}:gamma=${middle.gamma}")
                when (middle.filterPreset) {
                    "vintage_90s" -> filters.add("colorbalance=rs=0.15:gs=0.05:bs=-0.15")
                    "cyberpunk_cool" -> filters.add("colorbalance=rs=-0.10:gs=0.05:bs=0.25")
                    "warm_gold" -> filters.add("colorbalance=rs=0.20:gs=0.10:bs=-0.10")
                    "bw_cinema" -> filters.add("eq=contrast=1.35:saturation=0.0")
                    else -> {}
                }
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

    /**
     * Dedicated 60 FPS Optical Flow segment caching command builder.
     * Uses mobile-optimized parameters (EPZS diamond search, AOBMC adaptive weighting,
     * ultrafast x264 preset) for rapid on-device background rendering.
     */
    fun buildOpticalFlowCacheCommand(
        inputPath: String,
        outputPath: String,
        targetFps: Int = 60,
        flowMode: String = "mci",
        scdThreshold: Double = 10.0,
        inPointSeconds: Double = 0.0,
        outPointSeconds: Double? = null,
        slowMoFactor: Float = 1.0f
    ): List<String> {
        val cmd = mutableListOf<String>()
        cmd.add("ffmpeg")
        cmd.add("-hide_banner")
        cmd.add("-y")
        if (inPointSeconds > 0.0) {
            cmd.add("-ss")
            cmd.add(String.format("%.3f", inPointSeconds))
        }
        if (outPointSeconds != null && outPointSeconds > inPointSeconds) {
            cmd.add("-t")
            cmd.add(String.format("%.3f", outPointSeconds - inPointSeconds))
        }
        cmd.add("-i")
        cmd.add(inputPath)

        val filters = mutableListOf<String>()
        val flowFilter = if (flowMode == "blend") {
            "minterpolate=fps=$targetFps:mi_mode=blend:scd=fdiff:scd_threshold=$scdThreshold"
        } else {
            "minterpolate=fps=$targetFps:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:me=epzs:mb_size=16:search_param=16:vsbmc=0:scd=fdiff:scd_threshold=$scdThreshold"
        }
        filters.add(flowFilter)
        if (slowMoFactor < 1.0f) {
            val ptsMult = 1.0f / slowMoFactor.coerceAtLeast(0.1f)
            filters.add("setpts=$ptsMult*PTS")
        }
        cmd.add("-vf")
        cmd.add(filters.joinToString(","))
        cmd.add("-c:v")
        cmd.add("libx264")
        cmd.add("-preset")
        cmd.add("ultrafast")
        cmd.add("-crf")
        cmd.add("20")
        cmd.add("-pix_fmt")
        cmd.add("yuv420p")
        cmd.add("-r")
        cmd.add(targetFps.toString())
        cmd.add("-c:a")
        cmd.add("copy")
        cmd.add("-movflags")
        cmd.add("+faststart")
        cmd.add(outputPath)
        return cmd
    }
}
