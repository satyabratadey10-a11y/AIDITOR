package com.aiditor.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiditor.app.ui.theme.BwGreyDark
import com.aiditor.app.ui.theme.BwGreyLight
import com.aiditor.app.ui.theme.BwWhite

@Composable
fun BwSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    formattedValue: String = String.format("%.2f", value),
    steps: Int = 0
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = BwGreyLight,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formattedValue,
                color = BwWhite,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = BwWhite,
                activeTrackColor = BwWhite,
                inactiveTrackColor = BwGreyDark
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
