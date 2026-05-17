package com.example.carcopilot.inference

import android.content.Context
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus

/**
 * Reads system.md, issue_synthesis.md, and mechanic_draft.md from assets and
 * fills the templates. Mirrors gemma_adapter._render_template +
 * _format_dtcs + _format_readings.
 */
class PromptBuilder(context: Context) {
    val systemPrompt: String = context.assets.open("system.md").bufferedReader().use { it.readText() }
    private val synthesisTemplate: String =
        context.assets.open("issue_synthesis.md").bufferedReader().use { it.readText() }
    private val mechanicDraftTemplate: String =
        context.assets.open("mechanic_draft.md").bufferedReader().use { it.readText() }

    fun renderSynthesisPrompt(issue: Issue, language: String = "en"): String =
        synthesisTemplate
            .replace("{vehicle}", issue.vehicle.displayName)
            .replace("{mileage}", issue.vehicle.mileage?.toString() ?: "unknown")
            .replace("{severity}", issue.severity.name)
            .replace("{route}", issue.route.name)
            .replace("{title}", issue.title)
            .replace("{subtitle}", issue.subtitle)
            .replace("{cost_min}", issue.meta.costUsdMin?.toString() ?: "—")
            .replace("{cost_max}", issue.meta.costUsdMax?.toString() ?: "—")
            .replace("{time_minutes}", issue.meta.timeMinutes?.toString() ?: "—")
            .replace("{drivability}", issue.meta.drivability ?: "—")
            .replace("{dtcs}", formatDtcs(issue.dtcs))
            .replace("{live_readings}", formatReadings(issue.liveReadings))
            .replace("{language}", language)

    fun renderMechanicDraftPrompt(issue: Issue, language: String = "en"): String =
        mechanicDraftTemplate
            .replace("{vehicle}", issue.vehicle.displayName)
            .replace("{mileage}", issue.vehicle.mileage?.toString() ?: "unknown")
            .replace("{title}", issue.title)
            .replace("{subtitle}", issue.subtitle)
            .replace("{dtcs}", formatDtcs(issue.dtcs))
            .replace("{live_readings}", formatReadings(issue.liveReadings))
            .replace("{language}", language)

    private fun formatDtcs(dtcs: List<DTC>): String =
        if (dtcs.isEmpty()) "(none)"
        else dtcs.joinToString("\n") { d ->
            "- ${d.code}: ${d.description}" + if (d.deferred) " [deferred]" else ""
        }

    private fun formatReadings(readings: List<LiveReading>): String =
        if (readings.isEmpty()) "(none)"
        else readings.joinToString("\n") { r ->
            val unit = r.unit?.let { " $it" } ?: ""
            val note = r.note?.let { " — $it" } ?: ""
            "- ${r.key}: ${r.value}$unit (${r.status.name})$note"
        }
}
