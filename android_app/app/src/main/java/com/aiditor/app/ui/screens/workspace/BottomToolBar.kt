package com.aiditor.app.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.R
import com.aiditor.app.data.model.ActiveTrackingMode
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.theme.*

/**
 * Bottom Action Toolbar matching the reference images:
 * - Far left: Return / back button `<` with two dots `··` in a dark rounded card
 * - Action tools: Split, Delete, Duplicate, Replace, Image, Edit, Tune, Speed, Track, Clear
 */
@Composable
fun BottomToolBar(
    onBack: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onReplace: () -> Unit,
    onAddImage: () -> Unit,
    onEdit: () -> Unit,
    onTune: () -> Unit,
    onSpeed: () -> Unit,
    onTrack: () -> Unit,
    onClear: () -> Unit,
    trackingMode: ActiveTrackingMode = ActiveTrackingMode.NONE,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF141416))
            .border(width = 1.dp, color = Color(0xFF202024))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Far Left: Back Navigation Button with two dots above chevron
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF202024))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Two dots `··`
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Box(modifier = Modifier.size(2.5.dp).background(BwWhite, RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.size(2.5.dp).background(BwWhite, RoundedCornerShape(1.dp)))
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = BwWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Horizontally scrollable tool actions
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Split
            ToolItem(
                iconRes = R.drawable.ic_split,
                title = "Split",
                onClick = onSplit
            )

            // 2. Delete
            ToolItem(
                iconRes = R.drawable.ic_delete,
                title = "Delete",
                onClick = onDelete
            )

            // 3. Duplicate
            ToolItem(
                iconRes = R.drawable.ic_duplicate,
                title = "Duplicate",
                onClick = onDuplicate
            )

            // 4. Replace
            ToolItem(
                iconRes = R.drawable.ic_replace,
                title = "Replace",
                onClick = onReplace
            )

            // 5. Image / Overlay
            ToolItem(
                iconRes = R.drawable.ic_image_overlay,
                title = "Image",
                onClick = onAddImage
            )

            // 6. Edit
            ToolItem(
                iconRes = R.drawable.ic_tune,
                title = "Edit",
                onClick = onEdit
            )

            // 7. Tune
            ToolItem(
                iconRes = R.drawable.ic_color_grade,
                title = "Tune",
                onClick = onTune
            )

            // 8. Speed
            ToolItem(
                iconRes = R.drawable.ic_speed_ramp,
                title = "Speed",
                onClick = onSpeed
            )

            // 9. Track / Stabilize Mode
            ToolItem(
                iconRes = R.drawable.ic_motion_track,
                title = if (trackingMode != ActiveTrackingMode.NONE) "Tracking" else "Track",
                onClick = onTrack,
                isHighlighted = trackingMode != ActiveTrackingMode.NONE
            )

            // 10. Clear
            ToolItem(
                iconRes = R.drawable.ic_clear,
                title = "Clear",
                onClick = onClear
            )
        }
    }
}

@Composable
private fun ToolItem(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = if (isHighlighted) Color(0xFF4CAF50) else BwWhite,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = if (isHighlighted) Color(0xFF4CAF50) else BwGreyLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
