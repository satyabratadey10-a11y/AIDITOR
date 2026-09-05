package com.aiditor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aiditor.app.R
import com.aiditor.app.data.model.ExportJob
import com.aiditor.app.data.model.ExportSettings
import com.aiditor.app.data.model.ExportStatus
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.PickedVideoDetails
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    initialVideo: PickedVideoDetails?,
    onBrowseGallery: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, path: String, sizeBytes: Long, sizeFormatted: String, duration: Double, width: Int, height: Int) -> Unit
) {
    var projectName by remember(initialVideo) {
        mutableStateOf(
            initialVideo?.displayName?.substringBeforeLast(".") ?: "New Project"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BwDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BwCardStroke),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEW PROJECT",
                        color = BwWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    BwIconButton(
                        iconRes = R.drawable.ic_close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        size = 30.dp,
                        iconSize = 16.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gallery Video Selection Card
                Text(
                    text = "GALLERY VIDEO",
                    color = BwGreyLight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (initialVideo != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BwCardBackground)
                            .border(1.dp, BwCardStroke, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_video_placeholder),
                                    contentDescription = null,
                                    tint = BwWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = initialVideo.displayName,
                                    color = BwWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${initialVideo.sizeFormatted} • ${String.format(Locale.US, "%.1fs", initialVideo.durationSeconds)} • ${initialVideo.width}x${initialVideo.height}",
                                color = BwGreyLight,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            BwButton(
                                text = "CHANGE VIDEO",
                                iconRes = R.drawable.ic_video_placeholder,
                                onClick = onBrowseGallery,
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BwCardBackground)
                            .border(1.dp, BwCardStroke, RoundedCornerShape(12.dp))
                            .clickable { onBrowseGallery() }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_video_placeholder),
                                contentDescription = null,
                                tint = BwWhite,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TAP TO SELECT VIDEO FROM GALLERY",
                                color = BwWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Project Title input
                Text(
                    text = "PROJECT TITLE",
                    color = BwGreyLight,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BwWhite,
                        unfocusedTextColor = BwWhite,
                        focusedBorderColor = BwWhite,
                        unfocusedBorderColor = BwCardStroke
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = BwGreyLight, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    BwButton(
                        text = "CREATE PROJECT",
                        iconRes = R.drawable.ic_check,
                        enabled = projectName.isNotBlank() && initialVideo != null,
                        onClick = {
                            if (projectName.isNotBlank() && initialVideo != null) {
                                onCreate(
                                    projectName,
                                    initialVideo.uri.toString(),
                                    initialVideo.sizeBytes,
                                    initialVideo.sizeFormatted,
                                    initialVideo.durationSeconds,
                                    initialVideo.width,
                                    initialVideo.height
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onConfirmExport: (settings: ExportSettings) -> Unit
) {
    var selectedRes by remember { mutableStateOf("1080p") }
    var selectedFps by remember { mutableStateOf(60) }
    var selectedCodec by remember { mutableStateOf("libx264") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BwDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BwCardStroke),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "EXPORT VIDEO",
                    color = BwWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Resolution
                Text(text = "Resolution", color = BwGreyLight, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("720p", "1080p", "4k").forEach { res ->
                        val isSel = selectedRes == res
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BwWhite else BwCardBackground)
                                .border(1.dp, if (isSel) BwWhite else BwCardStroke, RoundedCornerShape(8.dp))
                                .clickable { selectedRes = res }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(res.uppercase(), color = if (isSel) BwBlack else BwWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                // Frame Rate
                Text(text = "Frame Rate", color = BwGreyLight, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { fps ->
                        val isSel = selectedFps == fps
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) BwWhite else BwCardBackground)
                                .border(1.dp, if (isSel) BwWhite else BwCardStroke, RoundedCornerShape(8.dp))
                                .clickable { selectedFps = fps }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${fps} FPS", color = if (isSel) BwBlack else BwWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = BwGreyLight)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    BwButton(
                        text = "START EXPORT",
                        onClick = {
                            onConfirmExport(
                                ExportSettings(
                                    resolution = selectedRes,
                                    fps = selectedFps,
                                    codec = selectedCodec
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ExportProgressDialog(
    exportJob: ExportJob,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = {
        if (exportJob.status == ExportStatus.COMPLETED || exportJob.status == ExportStatus.FAILED) {
            onDismiss()
        }
    }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BwDarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BwCardStroke),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (exportJob.status == ExportStatus.COMPLETED) "EXPORT COMPLETE" else "RENDERING VIDEO",
                    color = BwWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                LinearProgressIndicator(
                    progress = { exportJob.progressPercentage / 100f },
                    color = BwWhite,
                    trackColor = BwGreyDark,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${exportJob.progressPercentage.toInt()}%",
                    color = BwWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = exportJob.message,
                    color = BwGreyLight,
                    fontSize = 12.sp
                )

                if (exportJob.status == ExportStatus.COMPLETED || exportJob.status == ExportStatus.FAILED) {
                    Spacer(modifier = Modifier.height(20.dp))
                    BwButton(
                        text = "DONE",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
