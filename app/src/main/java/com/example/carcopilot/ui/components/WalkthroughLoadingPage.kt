package com.example.carcopilot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.carcopilot.ui.WalkthroughPipelineState
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * Animation timings ported from inline [ThinkingDots] so the loader shares
 * one motion vocabulary with the AI strip elsewhere in the app — the user
 * already recognizes this rhythm as "thinking on-device" from the synthesis,
 * mechanic-draft, and history-pattern surfaces. Scaled up for page presence:
 * 10dp dots vs the 6dp inline version, 4dp bounce vs 3dp inline, 10dp gap
 * vs 4dp inline. Cycle (1200ms) and per-dot stagger (150ms) match the
 * inline component exactly so the two loaders read as the same animation
 * in different sizes, not two unrelated motifs.
 */
private const val DOT_CYCLE_MS = 1200
private const val DOT_STAGGER_MS = 150
private val DOT_SIZE = 10.dp
private val DOT_GAP = 10.dp
private val DOT_BOUNCE = 4.dp

/**
 * Full-page loading state for the walkthrough screen.
 *
 * Design rationale: dots that share the AI strip's bounce + alpha motion
 * (single source of motion on the page) plus an adaptive phase title that
 * absorbs the variable plan length. No count visualization, so the loader
 * never needs to rebalance when Gemma's plan turns out to have 6 or 7
 * steps instead of the assumed 5.
 *
 * Pipeline state mapping:
 *   - [WalkthroughPipelineState.BuildingPlan]: phase title reads "Drafting
 *     the plan" with no sub-line. We don't know the total step count yet,
 *     so any number we showed would be wrong half the time.
 *   - [WalkthroughPipelineState.BuildingStep]: phase title is the live plan
 *     step's title ("Inspect the spark plug"); sub-line reads "Step 3 of 5"
 *     using the freshly-parsed plan size.
 *   - [WalkthroughPipelineState.Ready]: terminal label, but the surrounding
 *     Crossfade in [com.example.carcopilot.ui.WalkthroughScreen] swaps to
 *     the actual walkthrough content the moment this state lands, so the
 *     "Ready" copy is only visible during the crossfade transition.
 *
 * Earlier iterations tried numbered skeleton rows (conflicted with the
 * bulleted body content), then a segmented progress bar (tile aesthetic
 * fell flat; 5-segment prefill rebalanced awkwardly when Gemma decided
 * on a different count). Dots + adaptive prose absorbs both problems
 * cleanly.
 */
@Composable
fun WalkthroughLoadingPage(
    state: WalkthroughPipelineState,
    modifier: Modifier = Modifier,
) {
    val (phaseTitle, phaseSub) = phaseText(state)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ColorizedHeading()
        Spacer(Modifier.height(36.dp))
        BouncingDots()
        Spacer(Modifier.height(32.dp))
        Text(
            text = phaseTitle,
            style = CarCopilotTypography.CardSubtitle.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
            ),
            color = CarCopilotColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (phaseSub != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = phaseSub,
                style = CarCopilotTypography.TabLabel.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.14.em,
                ),
                color = CarCopilotColors.TextFaint,
            )
        }
    }
}

/**
 * Loader title with the middle word colorized indigo and an accent hairline
 * sitting directly beneath it. The previous version used a single Text and
 * a 28dp hairline centered under the whole phrase — geometrically off-center
 * relative to YOUR because PREPARING (9 chars) and WALKTHROUGH (11 chars)
 * flank it asymmetrically. Measuring the actual glyph rect via
 * [rememberTextMeasurer] anchors the bar to YOUR's literal horizontal span
 * plus 4dp optical padding, so the colorize and the underline read as one
 * intentional emphasis instead of a decorative line that floats nearby.
 */
@Composable
private fun ColorizedHeading() {
    val baseStyle = remember {
        CarCopilotTypography.TabLabel.copy(
            letterSpacing = 0.18.em,
            fontWeight = FontWeight.Medium,
        )
    }
    val annotated = remember {
        buildAnnotatedString {
            append("PREPARING ")
            withStyle(
                SpanStyle(
                    color = CarCopilotColors.AccentInline,
                    fontWeight = FontWeight.SemiBold,
                ),
            ) {
                append("YOUR")
            }
            append(" WALKTHROUGH")
        }
    }
    val measurer = rememberTextMeasurer()
    val layout = remember(annotated, baseStyle, measurer) {
        measurer.measure(annotated, baseStyle)
    }
    val density = LocalDensity.current
    val yourStartIdx = "PREPARING ".length
    val yourEndIdx = yourStartIdx + "YOUR".length
    val yourLeftPx = layout.getBoundingBox(yourStartIdx).left
    val yourRightPx = layout.getBoundingBox(yourEndIdx - 1).right
    val totalWidthPx = layout.size.width.toFloat()
    val padPx = with(density) { 4.dp.toPx() }
    // Coerce so the bar never escapes the heading's own bounds — defensive
    // against future copy edits where the middle word lands closer to either
    // edge than the optical padding allows.
    val safePad = padPx
        .coerceAtMost(yourLeftPx)
        .coerceAtMost(totalWidthPx - yourRightPx)
    val barLeftPx = yourLeftPx - safePad
    val barWidthPx = (yourRightPx - yourLeftPx) + safePad * 2

    Text(
        text = annotated,
        style = baseStyle,
        color = CarCopilotColors.Text,
    )
    Spacer(Modifier.height(7.dp))
    Box(
        modifier = Modifier
            .width(with(density) { totalWidthPx.toDp() })
            .height(1.5.dp)
            .padding(start = with(density) { barLeftPx.toDp() }),
    ) {
        Box(
            modifier = Modifier
                .width(with(density) { barWidthPx.toDp() })
                .height(1.5.dp)
                .clip(RoundedCornerShape(0.75.dp))
                .background(CarCopilotColors.AccentInline.copy(alpha = 0.55f)),
        )
    }
}

private fun phaseText(state: WalkthroughPipelineState): Pair<String, String?> = when (state) {
    is WalkthroughPipelineState.BuildingPlan -> "Drafting the plan" to null
    is WalkthroughPipelineState.BuildingStep -> {
        val title = state.plan.getOrNull(state.currentIdx)?.title
            ?: "Building the next step"
        val sub = "Step ${state.currentIdx + 1} of ${state.total}"
        title to sub
    }
    is WalkthroughPipelineState.Ready -> "Ready" to null
}

@Composable
private fun BouncingDots() {
    val bouncePx = with(LocalDensity.current) { DOT_BOUNCE.toPx() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        repeat(3) { idx ->
            BouncingDot(delayMs = idx * DOT_STAGGER_MS, bouncePx = bouncePx)
        }
    }
}

@Composable
private fun BouncingDot(delayMs: Int, bouncePx: Float) {
    val transition = rememberInfiniteTransition(label = "loader-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DOT_CYCLE_MS
                0.3f at 0
                1f at (DOT_CYCLE_MS * 0.4f).toInt()
                0.3f at (DOT_CYCLE_MS * 0.8f).toInt()
                0.3f at DOT_CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "alpha",
    )
    val translateY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = DOT_CYCLE_MS
                0f at 0
                -bouncePx at (DOT_CYCLE_MS * 0.4f).toInt()
                0f at (DOT_CYCLE_MS * 0.8f).toInt()
                0f at DOT_CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "translateY",
    )
    Box(
        modifier = Modifier
            .size(DOT_SIZE)
            .graphicsLayer {
                this.alpha = alpha
                this.translationY = translateY
            }
            .clip(CircleShape)
            .background(CarCopilotColors.Accent),
    )
}
