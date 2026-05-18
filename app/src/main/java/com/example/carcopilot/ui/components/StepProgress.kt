package com.example.carcopilot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

@Composable
fun StepPill(current: Int, total: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CarCopilotColors.AccentSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "STEP $current / $total",
            style = CarCopilotTypography.TabLabel.copy(letterSpacing = 0.1.em),
            color = CarCopilotColors.AccentInline,
        )
    }
}

/**
 * Top-of-walkthrough progress bar. Each segment renders in one of three
 * states, color-coded so the user can spot at a glance where they are in the
 * procedure without reading the StepPill text:
 *
 *   - DONE → healthy green     (`idx < current - 1`)
 *   - CURRENT → accent yellow  (`idx == current - 1`)
 *   - UPCOMING → muted line    (`idx > current - 1`)
 *
 * `current` is 1-indexed to mirror StepPill's "STEP X / Y" display, so step
 * one maps to `current = 1` and lights segment index 0 as the current state.
 */
@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { idx ->
            val state = when {
                idx < current - 1 -> SegmentState.Done
                idx == current - 1 -> SegmentState.Current
                else -> SegmentState.Upcoming
            }
            Segment(state = state)
        }
    }
}

private enum class SegmentState { Done, Current, Upcoming }

@Composable
private fun RowScope.Segment(state: SegmentState) {
    val target = when (state) {
        SegmentState.Done -> CarCopilotColors.Healthy
        SegmentState.Current -> CarCopilotColors.Accent
        SegmentState.Upcoming -> CarCopilotColors.Line
    }
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 240),
        label = "step-progress-segment",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}
