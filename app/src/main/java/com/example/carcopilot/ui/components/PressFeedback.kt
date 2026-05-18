package com.example.carcopilot.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press-and-release scale feedback for CTAs, tappable cards, and chips.
 * Apply at (or near) the START of the modifier chain so the graphics layer
 * encompasses the drawn surface (background, border). If placed AFTER
 * `clip` + `background`, the scale wraps only what comes after and the
 * button background appears stationary.
 *
 * Correct: `Modifier.scalePressable(onClick = …).clip(…).background(…).padding(…)`
 *
 * Ripple is suppressed (`indication = null`) because the scale IS the
 * feedback — adding a ripple on top reads as two unrelated effects. The
 * 0.97 default is calibrated so the motion is felt at the fingertip but
 * doesn't visually shrink the touch target (the layout box itself doesn't
 * resize; only the rendered output scales).
 *
 * Press-in is fast (80ms) so the finger feels the immediate response;
 * release-out is slower (240ms) so the button settles like a real
 * physical button springing back. `LinearOutSlowInEasing` gives the
 * settle a calm exponential deceleration matching the rest of the app's
 * motion language.
 */
fun Modifier.scalePressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = if (isPressed) 80 else 240,
            easing = LinearOutSlowInEasing,
        ),
        label = "press-scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}
