package com.example.carcopilot.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [splitProcedureIntoPhases] and [phaseForStep] — the per-step prefill
 * trimmer that drops the walkthrough-step prompt from ~3.5k tokens (full
 * procedure document) down to intro + one phase. The procedures themselves
 * live in `assets/walkthroughs/<code>.md` and are read at runtime via Android
 * Context; these tests work against synthetic procedure strings that match
 * the same `## Phase N — title` shape so they run on the JVM without an
 * AssetManager.
 */
class ProcedurePhaseSplitTest {

    private val sixPhaseProcedure = """
        # Replace the thing

        ## Vehicle context

        Stuff about the car.

        ## Parts and tools

        - Wrench
        - Plug

        ## Phase 1 — Prep

        Wait for it to cool.

        ## Phase 2 — Disconnect

        Pull the connector.

        ## Phase 3 — Remove

        Pull the part out.

        ## Phase 4 — Install

        Drop the new one in.

        ## Phase 5 — Reconnect

        Snap the wire back on.

        ## Phase 6 — Verify

        Start the engine and drive.
    """.trimIndent()

    private val fivePhaseProcedure = """
        # Replace the filte

        ## Vehicle context

        Diesel rail stuff.

        ## Phase 1 — Cool down

        Wait 10 minutes.

        ## Phase 2 — Locate

        Look under the hood.

        ## Phase 3 — Replace

        Swap the canister.

        ## Phase 4 — Bleed

        Pump the primer.

        ## Phase 5 — Verify

        Start and watch for drips.
    """.trimIndent()

    // ── splitProcedureIntoPhases ─────────────────────────────────────────────

    @Test
    fun `splits a six-phase procedure into intro plus six phases`() {
        val chunks = splitProcedureIntoPhases(sixPhaseProcedure)
        assertEquals(7, chunks.size)
        assertTrue(chunks[0].contains("# Replace the thing"))
        assertTrue(chunks[0].contains("Parts and tools"))
        assertTrue(chunks[1].startsWith("## Phase 1"))
        assertTrue(chunks[6].startsWith("## Phase 6"))
    }

    @Test
    fun `splits a five-phase procedure into intro plus five phases`() {
        val chunks = splitProcedureIntoPhases(fivePhaseProcedure)
        assertEquals(6, chunks.size)
        assertTrue(chunks[1].startsWith("## Phase 1"))
        assertTrue(chunks[5].startsWith("## Phase 5"))
    }

    @Test
    fun `returns single-element list when no phase headers are present`() {
        val body = "# A short note\n\nNo phases here.\n"
        val chunks = splitProcedureIntoPhases(body)
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("A short note"))
    }

    @Test
    fun `intro captures everything before the first phase header`() {
        val chunks = splitProcedureIntoPhases(sixPhaseProcedure)
        assertTrue(chunks[0].contains("Vehicle context"))
        assertTrue(chunks[0].contains("Parts and tools"))
        assertTrue(!chunks[0].contains("## Phase 1"))
    }

    @Test
    fun `each phase chunk keeps its own header`() {
        val chunks = splitProcedureIntoPhases(sixPhaseProcedure)
        for (i in 1..6) {
            assertTrue(
                "phase chunk $i should start with its header",
                chunks[i].startsWith("## Phase $i")
            )
        }
    }

    @Test
    fun `phase headers with double-digit numbers split correctly`() {
        val body = """
            ## Phase 1 — A

            One.

            ## Phase 10 — B

            Ten.
        """.trimIndent()
        val chunks = splitProcedureIntoPhases(body)
        assertEquals(3, chunks.size)
        assertTrue(chunks[1].startsWith("## Phase 1 "))
        assertTrue(chunks[2].startsWith("## Phase 10"))
    }

    // ── phaseForStep ─────────────────────────────────────────────────────────

    @Test
    fun `step 1 of 6 returns intro plus first phase`() {
        val phases = splitProcedureIntoPhases(sixPhaseProcedure)
        val chunk = phaseForStep(phases, stepNumber = 1, totalSteps = 6)
        assertTrue(chunk.contains("# Replace the thing"))
        assertTrue(chunk.contains("## Phase 1 — Prep"))
        assertTrue(!chunk.contains("## Phase 2"))
        assertTrue(!chunk.contains("## Phase 6"))
    }

    @Test
    fun `last step returns intro plus last phase`() {
        val phases = splitProcedureIntoPhases(sixPhaseProcedure)
        val chunk = phaseForStep(phases, stepNumber = 6, totalSteps = 6)
        assertTrue(chunk.contains("# Replace the thing"))
        assertTrue(chunk.contains("## Phase 6 — Verify"))
        assertTrue(!chunk.contains("## Phase 1"))
        assertTrue(!chunk.contains("## Phase 5"))
    }

    @Test
    fun `middle step maps by index`() {
        val phases = splitProcedureIntoPhases(sixPhaseProcedure)
        val chunk = phaseForStep(phases, stepNumber = 3, totalSteps = 6)
        assertTrue(chunk.contains("## Phase 3 — Remove"))
        assertTrue(!chunk.contains("## Phase 2"))
        assertTrue(!chunk.contains("## Phase 4"))
    }

    @Test
    fun `step number above phase count clamps to last phase`() {
        val phases = splitProcedureIntoPhases(fivePhaseProcedure)
        val chunk = phaseForStep(phases, stepNumber = 8, totalSteps = 8)
        assertTrue(chunk.contains("## Phase 5 — Verify"))
        assertTrue(!chunk.contains("## Phase 4"))
    }

    @Test
    fun `fewer steps than phases still pins prep and verify to ends`() {
        val phases = splitProcedureIntoPhases(sixPhaseProcedure)
        val first = phaseForStep(phases, stepNumber = 1, totalSteps = 3)
        val last  = phaseForStep(phases, stepNumber = 3, totalSteps = 3)
        assertTrue(first.contains("## Phase 1 — Prep"))
        assertTrue(last.contains("## Phase 6 — Verify"))
    }

    @Test
    fun `single-chunk input (no phases) returns the chunk unchanged`() {
        val phases = splitProcedureIntoPhases("# Bare note, no phases\n")
        val chunk = phaseForStep(phases, stepNumber = 1, totalSteps = 1)
        assertTrue(chunk.contains("Bare note"))
    }

    @Test
    fun `empty phases list returns empty string`() {
        val chunk = phaseForStep(emptyList(), stepNumber = 1, totalSteps = 1)
        assertEquals("", chunk)
    }

    @Test
    fun `chunk size is meaningfully smaller than full document`() {
        // The whole motivation for this split — verify a step prompt is
        // substantially shorter than the full procedure it was carved from.
        val phases = splitProcedureIntoPhases(sixPhaseProcedure)
        val singleStep = phaseForStep(phases, stepNumber = 3, totalSteps = 6)
        val fullLength = sixPhaseProcedure.length
        val stepLength = singleStep.length
        assertTrue(
            "step chunk ($stepLength chars) should be well under half the full procedure ($fullLength chars)",
            stepLength < fullLength / 2
        )
    }
}
