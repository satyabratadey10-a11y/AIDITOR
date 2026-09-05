package com.aiditor.app.ui.screens.mainmenu

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.Project
import com.aiditor.app.ui.components.BwButton
import com.aiditor.app.ui.components.BwFab
import com.aiditor.app.ui.components.CreateProjectDialog
import com.aiditor.app.ui.theme.*
import com.aiditor.app.util.PickedVideoDetails
import com.aiditor.app.util.VideoPickerHelper

/**
 * Screen 1: Main Menu (Appears on launch).
 * Shows all previous projects in cards with thumbnail, total file size,
 * created and last modified dates, and a rounded white FAB with '+' icon.
 * Includes native Android gallery video picker integration and proper edge-to-edge window insets.
 */
@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel,
    onProjectSelect: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var pickedVideo by remember { mutableStateOf<PickedVideoDetails?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Modern Photo/Video Picker launcher
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
            val details = VideoPickerHelper.extractVideoDetails(context, uri)
            pickedVideo = details
            showCreateDialog = true
        }
    }

    // Fallback file/gallery picker launcher
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {
            }
            val details = VideoPickerHelper.extractVideoDetails(context, uri)
            pickedVideo = details
            showCreateDialog = true
        }
    }

    val launchGallery: () -> Unit = {
        try {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } catch (_: Exception) {
            getContentLauncher.launch("video/*")
        }
    }

    Scaffold(
        containerColor = BwBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BwBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "AIDITOR",
                            color = BwWhite,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "AUTONOMOUS AI VIDEO CORE • V3.0",
                            color = BwGreyLight,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // Projects Count Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BwDarkSurface)
                            .border(1.dp, BwCardStroke, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${uiState.projects.size} PROJECTS",
                            color = BwWhite,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = BwCardStroke,
                    thickness = 1.dp
                )
            }
        },
        floatingActionButton = {
            BwFab(
                onClick = launchGallery,
                modifier = Modifier.navigationBarsPadding(),
                contentDescription = "Create New Project"
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Projects List or Empty State (No fake placeholder projects!)
            if (uiState.projects.isEmpty() && !uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_video_placeholder),
                        contentDescription = null,
                        tint = BwGreyLight,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "NO PROJECTS YET",
                        color = BwWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick a video from your gallery to start editing",
                        color = BwGreyLight,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    BwButton(
                        text = "BROWSE GALLERY",
                        iconRes = R.drawable.ic_video_placeholder,
                        onClick = launchGallery
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(
                        items = uiState.projects,
                        key = { it.id }
                    ) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectSelect(project) },
                            onDelete = { viewModel.deleteProject(project.id) }
                        )
                    }
                }
            }
        }

        // Create Project Dialog with selected gallery video
        if (showCreateDialog) {
            CreateProjectDialog(
                initialVideo = pickedVideo,
                onBrowseGallery = launchGallery,
                onDismiss = { showCreateDialog = false },
                onCreate = { name, path, sizeBytes, sizeFormatted, duration, width, height ->
                    viewModel.createProject(
                        name = name,
                        videoPath = path,
                        fileSizeBytes = sizeBytes,
                        fileSizeFormatted = sizeFormatted,
                        durationSeconds = duration,
                        width = width,
                        height = height
                    ) { createdProject ->
                        showCreateDialog = false
                        onProjectSelect(createdProject)
                    }
                }
            )
        }
    }
}
