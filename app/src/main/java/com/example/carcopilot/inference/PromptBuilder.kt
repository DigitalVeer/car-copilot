package com.example.carcopilot.inference

import android.content.Context
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.HistoryEntry
import com.example.carcopilot.model.HistoryPill
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus

/**
 * Reads system.md and per-surface prompt templates from assets and fills the
 * templates. Mirrors gemma_adapter._render_template + _format_dtcs +
 * _format_readings + _format_entries.
 */
class PromptBuilder(context: Context) {
    val systemPrompt: String = context.assets.open("system.md").bufferedReader().use { it.readText() }
    private val synthesisTemplate: String =
        context.assets.open("issue_synthesis.md").bufferedReader().use { it.readText() }
    private val mechanicDraftTemplate: String =
        context.assets.open("mechanic_draft.md").bufferedReader().use { it.readText() }
    private val historyPatternTemplate: String =
        context.assets.open("history_pattern.md").bufferedReader().use { it.readText() }

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

    fun renderHistoryPatternPrompt(
        history: List<HistoryEntry>,
        currentIssue: Issue?,
        language: String = "en",
    ): String =
        historyPatternTemplate
            .replace("{vehicle}", currentIssue?.vehicle?.displayName ?: "the user's car")
            .replace("{entries}", formatEntries(history))
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

    private fun formatEntries(entries: List<HistoryEntry>): String =
        if (entries.isEmpty()) "(none)"
        else entries.joinToString("\n") { e ->
            val pills = if (e.pills.isEmpty()) ""
            else " [${e.pills.joinToString(", ") { it.label() }}]"
            "- ${e.month}, day ${e.day} — ${e.title}$pills: ${e.sub}"
        }

    private fun HistoryPill.label(): String = when (this) {
        HistoryPill.Open -> "in progress"
        HistoryPill.Resolved -> "resolved"
        HistoryPill.Recurrence -> "recurrence"
    }
}
