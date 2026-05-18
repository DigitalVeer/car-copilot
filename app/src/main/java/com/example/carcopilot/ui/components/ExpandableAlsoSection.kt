package com.example.carcopilot.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun ExpandableAlsoSection(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = CarCopilotColors.PhoneCard,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Also detected",
                        style = CarCopilotTypography.SectionLabel,
                        color = CarCopilotColors.Accent.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "${items.size} other ${if (items.size == 1) "finding" else "findings"}",
                        style = CarCopilotTypography.AlsoRow,
                        color = CarCopilotColors.TextMute,
                    )
                }
                Text(
                    text = "▼",
                    color = CarCopilotColors.Accent.copy(alpha = 0.6f),
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                )
            }
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    AlsoItemRow(text = item)
                }
            }
        }
    }
}

@Composable
private fun AlsoItemRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CarCopilotColors.PhoneCard.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .background(
                    color = CarCopilotColors.Accent.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = "•",
                style = CarCopilotTypography.AlsoRow,
                color = CarCopilotColors.Accent,
            )
        }
        Text(
            text = text,
            style = CarCopilotTypography.AlsoRow,
            color = CarCopilotColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
