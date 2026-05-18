package com.example.carcopilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.carcopilot.data.WalkthroughSpec
import com.example.carcopilot.ui.theme.CarCopilotColors
import com.example.carcopilot.ui.theme.CarCopilotTypography

/**
 * Always-visible chip grid of canonical [WalkthroughSpec]s pulled from the
 * curated procedure for the issue's DTC. Rendered once per walkthrough
 * screen (not per-step) — the procedure-level numbers stay on screen across
 * all steps so the user has the authoritative torque, gap, and time values
 * in view even when navigating between steps where Gemma's body may have
 * dropped or mangled them.
 *
 * Phase W2.1 — film-around for persisting numeric drift on less-rehearsed
 * DTC paths. Each chip is small-caps label over Geist-medium value, matching
 * the EvidenceSection visual language. No interaction; the chips are a
 * non-load-bearing reference card sitting alongside the AI body.
 */
@Composable
fun SpecsChipRow(
    specs: List<WalkthroughSpec>,
    modifier: Modifier = Modifier,
) {
    if (specs.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarCopilotColors.PhoneCardSoft)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "SPECS FROM PROCEDURE",
            style = CarCopilotTypography.EvidenceSectionHeading,
            color = CarCopilotColors.TextMute,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            specs.forEach { spec -> SpecChip(spec) }
        }
    }
}

@Composable
private fun SpecChip(spec: WalkthroughSpec) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CarCopilotColors.PhoneCard)
            .border(1.dp, CarCopilotColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = spec.label.uppercase(),
            style = CarCopilotTypography.TabLabel,
            color = CarCopilotColors.TextMute,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = spec.value,
            style = CarCopilotTypography.EvidenceRowValue,
            color = CarCopilotColors.TitleBright,
        )
    }
}
