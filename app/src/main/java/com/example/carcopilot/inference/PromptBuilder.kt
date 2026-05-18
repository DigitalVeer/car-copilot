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
        "P0087" to context.assets.open("walkthroughs/P0087.md").bufferedReader().use { it.readText() },
    )

    /**
     * Pre-split procedures keyed by DTC code, structured as
     * `[intro, phase1, phase2, ...]`. The plan call still gets the full
     * document (it clusters phases into steps and needs the whole text); the
     * per-step call gets `intro + phaseForStep(...)` only.
     *
     * Splitting happens once at app start so step generation doesn't pay
     * per-call regex parsing. BenchmarkInfo on the synthesis surface measured
     * prefill at ~142 tokens/sec — at that rate, dropping the per-step
     * procedure from ~1,450 tokens (P0301) or ~2,180 tokens (P0087) down to
     * intro + one phase chunk (~400-700 tokens) buys back ~10-15 seconds of
     * wall-clock before the first body bullet appears.
     */
    private val procedurePhases: Map<String, List<String>> =
        procedures.mapValues { (_, body) -> splitProcedureIntoPhases(body) }

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
            .replace("{procedure}", procedureChunkForStep(issue, planStep.number, totalSteps))

    /**
     * Resolve the per-step procedure chunk for [issue]'s primary DTC. Falls
     * back to the full document when the splitter couldn't find any
     * `## Phase N` headers (legacy procedure shape). The chunk always opens
     * with the intro section — title, vehicle context, parts list, named
     * tools — so the step body still sees the bill of materials and named
     * components even when the relevant phase doesn't restate them.
     */
    private fun procedureChunkForStep(issue: Issue, stepNumber: Int, totalSteps: Int): String {
        val code = issue.dtcs.firstOrNull()?.code
            ?: error("Issue ${issue.id} has no DTCs; cannot resolve a walkthrough procedure.")
        val phases = procedurePhases[code]
            ?: error("No curated procedure for DTC $code. Add assets/walkthroughs/$code.md and wire it into PromptBuilder.procedures.")
        return phaseForStep(phases, stepNumber, totalSteps)
    }

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

/**
 * Split a curated procedure document into `[intro, phase1, phase2, ...]`
 * for per-step prefill. A phase boundary is any line matching `## Phase N`
 * (case-sensitive, N is one or more digits) — the shape every curated
 * procedure under `assets/walkthroughs/` follows today. Everything before
 * the first phase header (markdown title, vehicle-context section, parts
 * and tools list) becomes the intro chunk at index 0; every subsequent
 * chunk keeps its own `## Phase N — title` heading so the model still
 * sees which phase it's reading.
 *
 * Returns a single-element list when no phase headers are found. Callers
 * detect this and either fall back to the original full-document
 * substitution or accept that the intro carries everything.
 */
internal fun splitProcedureIntoPhases(body: String): List<String> {
    val pattern = Regex("""(?m)^## Phase \d+""")
    val matches = pattern.findAll(body).toList()
    if (matches.isEmpty()) return listOf(body.trim())
    val chunks = mutableListOf<String>()
    chunks += body.substring(0, matches.first().range.first).trimEnd()
    for (i in matches.indices) {
        val start = matches[i].range.first
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
        chunks += body.substring(start, end).trimEnd()
    }
    return chunks
}

/**
 * Pick the per-step procedure chunk from the splitter output. Step 1 maps
 * to the first phase, the last step maps to the last phase, middle steps
 * map by index (clamped). The intro is always included so the step body
 * still sees the vehicle context, parts list, named tools, and any
 * cross-phase context that lives before `## Phase 1`.
 *
 * The plan call is intentionally NOT routed through this function — it
 * clusters the curated phases into the plan's step list and needs to see
 * every phase header to do that. Only the per-step body call benefits
 * from the reduced grounding.
 *
 * Step↔phase alignment is positional, not semantic — the plan prompt
 * explicitly tells Gemma "one logical phase per step" with prep first and
 * verification last, so for the curated P0301 (6 phases) and P0087 (5
 * phases) the indexed mapping is the right one. When step count and
 * phase count diverge, the bias is toward the indexed phase the user is
 * most likely on (step 3 of 4 → middle-late phase), with prep + verify
 * pinned to the ends.
 */
internal fun phaseForStep(phases: List<String>, stepNumber: Int, totalSteps: Int): String {
    if (phases.size < 2) return phases.firstOrNull().orEmpty()
    val intro = phases.first()
    val phaseChunks = phases.drop(1)
    val targetIdx = when {
        stepNumber <= 1 -> 0
        stepNumber >= totalSteps -> phaseChunks.lastIndex
        else -> (stepNumber - 1).coerceIn(0, phaseChunks.lastIndex)
    }
    return buildString {
        if (intro.isNotBlank()) {
            append(intro)
            append("\n\n")
        }
        append(phaseChunks[targetIdx])
    }
}
