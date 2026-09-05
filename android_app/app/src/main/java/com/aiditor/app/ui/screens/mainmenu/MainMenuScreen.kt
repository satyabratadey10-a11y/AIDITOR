package com.aiditor.app.ui.screens.mainmenu

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.Project
import com.aiditor.app.ui.components.BwFab
import com.aiditor.app.ui.components.CreateProjectDialog
import com.aiditor.app.ui.theme.*

/**
 * Screen 1: Main Menu (Appears on launch).
 * Shows all previous projects in cards with thumbnail, total file size,
 * created and last modified dates, and a rounded white FAB with '+' icon.
 */
@Composable
fun MainMenuScreen(
    viewModel: MainMenuViewModel,
    onProjectSelect: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = BwBlack,
        floatingActionButton = {
            BwFab(
                onClick = { viewModel.showCreateDialog(true) },
                contentDescription = "Create New Project"
            )
        },
        floatingActionButtonPosition = FabPosition.End,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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

                // Status indicator badge
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

            // Subtitle divider
            HorizontalDivider(
                color = BwCardStroke,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Projects List
            if (uiState.projects.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_video_placeholder),
                            contentDescription = null,
                            tint = BwGreyDark,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO PROJECTS FOUND",
                            color = BwWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button below to create your first edit",
                            color = BwGreyLight,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
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

        // Create Project Dialog
        if (uiState.showCreateDialog) {
            CreateProjectDialog(
                onDismiss = { viewModel.showCreateDialog(false) },
                onCreate = { name, path ->
                    viewModel.createProject(name, path) { createdProject ->
                        onProjectSelect(createdProject)
                    }
                }
            )
        }
    }
}
