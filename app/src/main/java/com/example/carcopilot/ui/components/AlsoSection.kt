package com.example.carcopilot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography
import com.example.carcopilot.ui.theme.JetBrainsMono

@Composable
fun AlsoSection(
    items: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Also",
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = CarCopilotTypography.SectionLabel,
            color = CarCopilotColors.TextMute,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        items.forEach { AlsoRow(text = it) }
    }
}

@Composable
private fun AlsoRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "—",
            style = CarCopilotTypography.AlsoRow.copy(fontFamily = JetBrainsMono),
            color = CarCopilotColors.TextFaint,
        )
        Text(
            text = text,
            style = CarCopilotTypography.AlsoRow,
            color = Color(0xFFC8C8C8),
        )
    }
}
