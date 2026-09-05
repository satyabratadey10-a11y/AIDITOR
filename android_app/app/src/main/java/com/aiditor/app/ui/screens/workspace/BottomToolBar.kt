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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.data.model.ToolType
import com.aiditor.app.ui.theme.*

/**
 * Bottom Tool Bar containing all 6 core editing tools with 100% SVG vector icons.
 * Conforms to: "and feature/tool list at bottom side as in bottom bar"
 */
@Composable
fun BottomToolBar(
    activeTool: ToolType?,
    onSelectTool: (ToolType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BwBlack)
            .border(width = 1.dp, color = BwCardStroke)
            .padding(vertical = 10.dp, horizontal = 12.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolType.values().forEach { tool ->
            val isSelected = activeTool == tool
            val containerColor = if (isSelected) BwWhite else BwCardBackground
            val contentColor = if (isSelected) BwBlack else BwWhite
            val borderColor = if (isSelected) BwWhite else BwCardStroke

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(containerColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onSelectTool(tool) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 100% SVG Vector icon
                Icon(
                    painter = painterResource(id = tool.iconRes),
                    contentDescription = tool.title,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = tool.title,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
