package com.example.carcopilot.model

/**
 * History screen domain. Shaped for the mockup §09 render — flat fields the
 * UI can drop into rows. The Python HistoryEntry has more — resolution_method,
 * actual_cost_usd, nested Issue — but we don't surface any of that yet.
 */

enum class HistoryPill { Open, Resolved, Recurrence }

data class HistoryEntry(
    val id: String,
    val day: Int,
    val month: String,
    val title: String,
    val pills: List<HistoryPill>,
    val sub: String,
)

data class HistoryStats(
    val issuesResolved: Int,
    val coilFailures: Int,
    val spentThisYearUsd: Int,
)

data class HistoryPattern(
    val label: String,
    val body: String,
)

object History {
    val PATTERN = HistoryPattern(
        label = "Pattern I've noticed",
        body = "You've replaced an ignition coil before — back in October. Two coils in seven months isn't surprising on a TSI; these little EA211 coils are known to fail early, and once one goes the others usually follow within a year. Worth picking up a spare set so you're not caught out on the next one.",
    )

    val STATS = HistoryStats(
        issuesResolved = 12,
        coilFailures = 2,
        spentThisYearUsd = 284,
    )

    val ENTRIES: List<HistoryEntry> = listOf(
        HistoryEntry(
            id = "20260514-P0301",
            day = 14,
            month = "May 2026",
            title = "Cylinder 1 misfire",
            pills = listOf(HistoryPill.Open, HistoryPill.Recurrence),
            sub = "P0301 · likely ignition coil · same pattern as October's coil failure",
        ),
        HistoryEntry(
            id = "20260322-P0457",
            day = 22,
            month = "March 2026",
            title = "Gas cap loose",
            pills = listOf(HistoryPill.Resolved),
            sub = "P0457 · you tightened it · cleared after 2 drives",
        ),
        HistoryEntry(
            id = "20251204-BATT",
            day = 4,
            month = "December 2025",
            title = "Battery weak at start",
            pills = listOf(HistoryPill.Resolved),
            sub = "11.8 V at crank · new battery from shop · \$140",
        ),
        HistoryEntry(
            id = "20251017-P0303",
            day = 17,
            month = "October 2025",
            title = "Cylinder 3 misfire",
            pills = listOf(HistoryPill.Resolved),
            sub = "P0303 · ignition coil replaced · \$45 · same symptoms as today",
        ),
    )
}
