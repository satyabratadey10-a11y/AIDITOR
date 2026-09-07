package com.aiditor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.AspectRatioMode
import com.aiditor.app.ui.theme.*

/**
 * TopBar matching CapCut / VN reference images:
 * - Left: Back arrow
 * - Center: Aspect Ratio dropdown picker ("Original v" / "1:1 v")
 * - Right: Export icon button (tray with upward arrow)
 */
@Composable
fun BwTopBar(
    title: String,
    onBackClick: () -> Unit,
    onExportClick: () -> Unit,
    aspectRatio: AspectRatioMode,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F11))
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back button
        BwIconButton(
            iconRes = R.drawable.ic_arrow_back,
            onClick = onBackClick,
            contentDescription = "Back",
            size = 36.dp,
            iconSize = 20.dp
        )

        // Center: Aspect Ratio Selector Dropdown
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_aspect_ratio),
                    contentDescription = "Aspect Ratio",
                    tint = BwWhite,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = aspectRatio.label,
                    color = BwWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "▼",
                    color = BwGreyLight,
                    fontSize = 9.sp
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E1E22))
            ) {
                AspectRatioMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = mode.label,
                                color = if (mode == aspectRatio) BwWhite else BwGreyLight,
                                fontWeight = if (mode == aspectRatio) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelectAspectRatio(mode)
                            expanded = false
                        }
                    )
                }
            }
        }

        // Right: Export Button (Icon button matching reference images)
        BwIconButton(
            iconRes = R.drawable.ic_export,
            onClick = onExportClick,
            contentDescription = "Export Video",
            size = 36.dp,
            iconSize = 20.dp
        )
    }
}
