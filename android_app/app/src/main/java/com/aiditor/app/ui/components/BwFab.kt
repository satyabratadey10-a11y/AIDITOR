package com.aiditor.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aiditor.app.R
import com.aiditor.app.ui.theme.BwBlack
import com.aiditor.app.ui.theme.BwWhite

/**
 * Rounded white Floating Action Button with '+' icon for creating new projects.
 * Conforms to: "a rounded white button with '+' icon,that button for add or create new projects"
 */
@Composable
fun BwFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Create New Project"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        label = "fab_scale"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .size(64.dp),
        shape = CircleShape,
        containerColor = BwWhite,
        contentColor = BwBlack,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        ),
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_add),
            contentDescription = contentDescription,
            tint = BwBlack,
            modifier = Modifier.size(28.dp)
        )
    }
}
