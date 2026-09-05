package com.aiditor.app.ui.screens.workspace

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.aiditor.app.R
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.components.BwIconButton
import com.aiditor.app.ui.theme.*
import java.io.File
import java.util.Locale

/**
 * Preview Screen at Center to Upper side of the Workspace.
 * Conforms to: "preview screen at center to upper side"
 * Plays actual video picked from phone gallery via ExoPlayer with fallback to HUD canvas.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewSection(
    currentTimeSeconds: Double,
    totalDurationSeconds: Double,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    activeTool: ToolType?,
    videoPath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoPath) {
        if (!videoPath.isNullOrBlank()) {
            try {
                val player = ExoPlayer.Builder(context).build().apply {
                    val uri = if (videoPath.startsWith("content://") || videoPath.startsWith("file://") || videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                        Uri.parse(videoPath)
                    } else {
                        Uri.fromFile(File(videoPath))
                    }
                    setMediaItem(MediaItem.fromUri(uri))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                }
                exoPlayer = player
            } catch (_: Exception) {
                exoPlayer = null
            }
        }
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(isPlaying, exoPlayer) {
        exoPlayer?.let { player ->
            if (isPlaying && !player.isPlaying) {
                player.play()
            } else if (!isPlaying && player.isPlaying) {
                player.pause()
            }
        }
    }

    LaunchedEffect(currentTimeSeconds) {
        exoPlayer?.let { player ->
            val targetMs = (currentTimeSeconds * 1000).toLong()
            if (Math.abs(player.currentPosition - targetMs) > 250) {
                player.seekTo(targetMs)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Video Preview Container (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(BwBlack)
                .border(1.dp, BwCardStroke, RoundedCornerShape(14.dp))
                .clickable { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Simulated video canvas fallback
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Dark gradient background
                    drawRect(Color(0xFF0A0A0A))

                    // Safe area guidelines
                    val safeInset = 20f
                    drawRect(
                        color = Color(0xFF1E1E1E),
                        topLeft = Offset(safeInset, safeInset),
                        size = Size(w - safeInset * 2, h - safeInset * 2),
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Real-time Tool Visualizer Overlay (drawn on top of real or simulated video!)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                when (activeTool) {
                    ToolType.MOTION_TRACKING -> {
                        // Cyberpunk target reticle
                        val cx = w * 0.55f
                        val cy = h * 0.45f
                        drawRect(
                            color = BwWhite,
                            topLeft = Offset(cx - 30f, cy - 30f),
                            size = Size(60f, 60f),
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(color = BwWhite, radius = 3f, center = Offset(cx, cy))
                    }
                    ToolType.ROTOSCOPE -> {
                        // Neon outline around center subject
                        drawCircle(
                            color = BwWhite,
                            radius = h * 0.32f,
                            center = Offset(w / 2f, h / 2f),
                            style = Stroke(width = 2f)
                        )
                    }
                    else -> {}
                }
            }

            // Central Play Indicator overlay when paused
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .border(1.5.dp, BwWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "Play",
                        tint = BwWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Top Status Overlay (Resolution + FPS + Active Tool)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1080P • 60 FPS",
                    color = BwWhite,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (activeTool != null) {
                    Text(
                        text = " • ${activeTool.title.uppercase()}",
                        color = BwGreyLight,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Controls & Timecode Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Timecode display
            val currFormatted = formatTimecode(currentTimeSeconds)
            val totalFormatted = formatTimecode(totalDurationSeconds)
            Text(
                text = "$currFormatted / $totalFormatted",
                color = BwWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            // Step & Playback Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BwIconButton(
                    iconRes = R.drawable.ic_step_back,
                    onClick = onStepBack,
                    contentDescription = "Step Back 1 Frame",
                    size = 36.dp,
                    iconSize = 18.dp
                )
                BwIconButton(
                    iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    onClick = onPlayPauseToggle,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    backgroundColor = BwWhite,
                    tint = BwBlack,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                BwIconButton(
                    iconRes = R.drawable.ic_step_forward,
                    onClick = onStepForward,
                    contentDescription = "Step Forward 1 Frame",
                    size = 36.dp,
                    iconSize = 18.dp
                )
            }
        }
    }
}

private fun formatTimecode(seconds: Double): String {
    val totalSecs = seconds.coerceAtLeast(0.0).toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val millis = ((seconds.coerceAtLeast(0.0) - totalSecs) * 100).toInt().coerceIn(0, 99)
    return String.format(Locale.US, "%02d:%02d.%02d", mins, secs, millis)
}
