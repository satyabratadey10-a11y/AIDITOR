package com.aiditor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.ui.theme.*

@Composable
fun BwTopBar(
    title: String,
    onBackClick: () -> Unit,
    onExportClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    modifier: Modifier = Modifier,
    canUndo: Boolean = true,
    canRedo: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BwBlack)
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back button & Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            BwIconButton(
                iconRes = R.drawable.ic_arrow_back,
                onClick = onBackClick,
                contentDescription = "Back to Projects",
                size = 38.dp,
                iconSize = 20.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title.uppercase(),
                    color = BwWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
                Text(
                    text = "AIDITOR 3.0 • MONOCHROME",
                    color = BwGreyMid,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Center: Undo / Redo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            BwIconButton(
                iconRes = R.drawable.ic_undo,
                onClick = onUndoClick,
                tint = if (canUndo) BwWhite else BwGreyDark,
                contentDescription = "Undo",
                size = 36.dp,
                iconSize = 18.dp
            )
            BwIconButton(
                iconRes = R.drawable.ic_redo,
                onClick = onRedoClick,
                tint = if (canRedo) BwWhite else BwGreyDark,
                contentDescription = "Redo",
                size = 36.dp,
                iconSize = 18.dp
            )
        }

        // Right: Video Export Button (White pill button with export icon)
        BwButton(
            text = "EXPORT",
            iconRes = R.drawable.ic_export,
            onClick = onExportClick,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.height(38.dp)
        )
    }
}
