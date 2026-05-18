package com.example.carcopilot.inference

import android.content.Context
import com.example.carcopilot.data.RagStore
import com.example.carcopilot.model.Classification
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.HistoryEntry
import com.example.carcopilot.model.HistoryPill
import com.example.carcopilot.model.Issue
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.ui.PlanStep

/**
 * Reads system.md and per-surface prompt templates from assets and fills the
 * templates. Mirrors gemma_adapter._render_template + _format_dtcs +
 * _format_readings + _format_entries.
 */
class PromptBuilder(context: Context) {
    val systemPrompt: String = context.assets.open("system.md").bufferedReader().use { it.readText() }
    private val ragStore = RagStore(context)
    private val synthesisTemplate: String =
        context.assets.open("issue_synthesis.md").bufferedReader().use { it.readText() }
    private val mechanicDraftTemplate: String =
        context.assets.open("mechanic_draft.md").bufferedReader().use { it.readText() }
    private val historyPatternTemplate: String =
        context.assets.open("history_pattern.md").bufferedReader().use { it.readText() }
    private val walkthroughPlanTemplate: String =
        context.assets.open("walkthrough_plan.md").bufferedReader().use { it.readText() }
    private val walkthroughStepTemplate: String =
        context.assets.open("walkthrough_step.md").bufferedReader().use { it.readText() }

    /**
     * Curated repair procedures keyed by DTC code. Loaded eagerly so a missing
     * asset surfaces at PromptBuilder construction (which runs once at app
     * start) rather than mid-stream during a live walkthrough call. The
     * procedure text is the *grounding* document — Gemma paraphrases from it,
     * never invents around it.
     *
     * Adding a new procedure is data work: drop a file at
     * `assets/walkthroughs/<CODE>.md` and add it to this map. The map exists
     * (instead of lazy-loading on demand) so an unknown code throws a clear
     * `IllegalStateException` at lookup time, which the surface methods on
     * [com.example.carcopilot.inference.GemmaService] catch into the standard
     * Phase-5 fallback path.
     */
    private val procedures: Map<String, String> = mapOf(
        "P0301" to context.assets.open("walkthroughs/P0301.md").bufferedReader().use { it.readText() },
    )

    fun renderSynthesisPrompt(
        issue: Issue,
        classification: Classification? = null,
        language: String = "en",
    ): String {
        val ragContext = issue.dtcs.firstOrNull()?.code
            ?.let { ragStore.retrieve(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "(none)"
        return synthesisTemplate
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
            .replace("{engine_family}", classification?.engineFamily?.name?.lowercase() ?: "unknown")
            .replace("{supporting_signals}", formatSupportingSignals(classification))
            .replace("{rag_context}", ragContext)
            .replace("{language}", language)
    }

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

    fun renderWalkthroughPlanPrompt(issue: Issue, language: String = "en"): String =
        walkthroughPlanTemplate
            .replace("{vehicle}", issue.vehicle.displayName)
            .replace("{mileage}", issue.vehicle.mileage?.toString() ?: "unknown")
            .replace("{title}", issue.title)
            .replace("{subtitle}", issue.subtitle)
            .replace("{difficulty}", issue.meta.difficulty ?: "—")
            .replace("{time_minutes}", issue.meta.timeMinutes?.toString() ?: "—")
            .replace("{category}", issue.category)
            .replace("{language}", language)
            .replace("{procedure}", procedureFor(issue))

    fun renderWalkthroughStepPrompt(
        issue: Issue,
        planStep: PlanStep,
        totalSteps: Int,
        language: String = "en",
    ): String =
        walkthroughStepTemplate
            .replace("{vehicle}", issue.vehicle.displayName)
            .replace("{mileage}", issue.vehicle.mileage?.toString() ?: "unknown")
            .replace("{title}", issue.title)
            .replace("{subtitle}", issue.subtitle)
            .replace("{language}", language)
            .replace("{step_number}", planStep.number.toString())
            .replace("{total_steps}", totalSteps.toString())
            .replace("{step_title}", planStep.title)
            .replace("{step_brief}", planStep.brief)
            .replace("{procedure}", procedureFor(issue))

    /**
     * Resolve the curated procedure text for [issue]'s primary DTC. Throws
     * [IllegalStateException] when the issue has no DTC or the code is
     * unmapped — both are configuration bugs that should fall back via
     * GemmaService's catch handler rather than ship empty grounding to Gemma.
     */
    private fun procedureFor(issue: Issue): String {
        val code = issue.dtcs.firstOrNull()?.code
            ?: error("Issue ${issue.id} has no DTCs; cannot resolve a walkthrough procedure.")
        return procedures[code]
            ?: error("No curated procedure for DTC $code. Add assets/walkthroughs/$code.md and wire it into PromptBuilder.procedures.")
    }

    private fun formatSupportingSignals(classification: Classification?): String {
        if (classification == null || classification.supportingSignals.isEmpty()) return "(none)"
        val lines = mutableListOf<String>()
        lines += "Confidence: ${classification.confidence.name.lowercase()}"
        lines += "Likely cause: ${classification.likelyCause}"
        classification.supportingSignals.forEach { lines += "- $it" }
        return lines.joinToString("\n")
    }

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
