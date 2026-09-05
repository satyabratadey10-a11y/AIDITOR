package com.aiditor.app.ui.screens.mainmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.Project
import com.aiditor.app.ui.components.BwCard
import com.aiditor.app.ui.components.BwIconButton
import com.aiditor.app.ui.theme.*

/**
 * Project card component for Screen 1 (Main Menu).
 * Conforms to: "shows all previous project,project's thumbnail/cover,total file size,created and last modifyed date"
 */
@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BwCard(
        onClick = onClick,
        modifier = modifier.padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Cover
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 75.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BwBlack)
                    .border(1.dp, BwCardStroke, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Vector placeholder icon
                Icon(
                    painter = painterResource(id = R.drawable.ic_video_placeholder),
                    contentDescription = "Project Thumbnail",
                    tint = BwGreyLight,
                    modifier = Modifier.size(32.dp)
                )

                // Duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%.1fs", project.durationSeconds),
                        color = BwWhite,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Project Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    color = BwWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // File Size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SIZE: ",
                        color = BwGreyMid,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = project.fileSizeFormatted,
                        color = BwWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Created & Modified Dates
                Text(
                    text = "CREATED: ${project.createdAt}",
                    color = BwGreyLight,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    text = "MODIFIED: ${project.modifiedAt}",
                    color = BwGreyLight,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            // Delete Action Button
            BwIconButton(
                iconRes = R.drawable.ic_delete,
                onClick = onDelete,
                contentDescription = "Delete Project",
                tint = BwGreyMid,
                size = 32.dp,
                iconSize = 16.dp
            )
        }
    }
}
