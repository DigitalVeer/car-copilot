package com.example.carcopilot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedAIStrip(state: SynthesisState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1F2C))
            .padding(16.dp),
    ) {
        when (state) {
            is SynthesisState.Thinking -> ThinkingDots()
            is SynthesisState.Streaming -> Text(
                text = state.partial,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE6E8EE),
            )
            is SynthesisState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.synthesis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE6E8EE),
                )
                state.goodNews?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8FB9FF),
                    )
                }
                if (state.isFallback) {
                    Text(
                        text = "(offline mode)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6A7187),
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { idx ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(idx * 200),
                ),
                label = "dot$idx",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE6E8EE)),
            )
            if (idx < 2) Spacer(Modifier.width(6.dp))
        }
    }
}
