package com.aiditor.app.data.model

import androidx.annotation.DrawableRes
import com.aiditor.app.R

enum class ToolType(
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int
) {
    OPTICAL_FLOW(
        title = "Optical Flow",
        description = "60 FPS bidirectional motion vector interpolation",
        iconRes = R.drawable.ic_optical_flow
    ),
    BEAT_SYNC(
        title = "Beat Sync",
        description = "Transient rhythm detection & phonk auto-cuts",
        iconRes = R.drawable.ic_beat_sync
    ),
    MOTION_TRACKING(
        title = "Motion Track",
        description = "HUD Cyber callout reticle & lock-on camera",
        iconRes = R.drawable.ic_motion_track
    ),
    SPEED_RAMP(
        title = "Speed Ramp",
        description = "Dynamic Bézier velocity curve & flash impact",
        iconRes = R.drawable.ic_speed_ramp
    ),
    COLOR_GRADE(
        title = "Color Grade",
        description = "Monochrome cinema contrast & LUT curves",
        iconRes = R.drawable.ic_color_grade
    ),
    ROTOSCOPE(
        title = "Rotoscope",
        description = "Foreground alpha matte & neon saber contours",
        iconRes = R.drawable.ic_rotoscope
    )
}
