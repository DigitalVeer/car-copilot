package com.example.carcopilot.data

import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the OBDSnapshot schema contract.
 *
 * OBDSnapshot is the input to the diagnostic pipeline — the adapter's
 * view of the vehicle that the [IssueBuilder] reads to assemble an
 * [com.example.carcopilot.model.Issue]. These tests verify that data
 * class defaults, the three-list DTC split, and the Phase-12-prep
 * provenance fields behave as the rest of the pipeline expects.
 *
 * Add a test whenever a field is added, removed, or its semantics change.
 *
 * Adapted from will/dev's VehicleStateTest. The tests asserting on
 * typed petrol-only/diesel-only PID fields and on FreezeFrame are not
 * lifted: our OBDSnapshot models live data as an untyped List<LiveReading>
 * rather than typed-per-family fields, and we don't carry a freeze frame.
 * Those are out of scope for Phase-12-prep — they'd require absorbing
 * Will's full VehicleState rebuild, not just the provenance fields.
 */
class OBDSnapshotTest {

    private val corolla = VehicleInfo(
        year = 2009, make = "Toyota", model = "Corolla",
        mileage = 187_000, displayName = "2009 Corolla",
    )

    private fun snapshot(
        source: DataSource = DataSource.FIXTURE,
        engineFamily: EngineFamily = EngineFamily.UNKNOWN,
        dtcs: List<DTC> = emptyList(),
        pendingDtcs: List<DTC> = emptyList(),
        permanentDtcs: List<DTC> = emptyList(),
    ) = OBDSnapshot(
        source = source,
        capturedAt = "2026-05-17T00:00:00Z",
        vehicle = corolla,
        engineFamily = engineFamily,
        dtcs = dtcs,
        pendingDtcs = pendingDtcs,
        permanentDtcs = permanentDtcs,
        liveReadings = emptyList(),
    )

    @Test
    fun `minimal construction has safe defaults`() {
        val state = snapshot()
        assertEquals(EngineFamily.UNKNOWN, state.engineFamily)
        assertTrue(state.dtcs.isEmpty())
        assertTrue(state.pendingDtcs.isEmpty())
        assertTrue(state.permanentDtcs.isEmpty())
        assertNull(state.vehicle.vin)
    }

    @Test
    fun `all three DTC lists are independent`() {
        val state = snapshot(
            source = DataSource.EMULATOR,
            dtcs = listOf(DTC("P0171", "System too lean")),
            pendingDtcs = listOf(DTC("P0101", "MAF range/performance")),
            permanentDtcs = listOf(DTC("P0420", "Catalyst efficiency below threshold")),
        )
        assertEquals(listOf("P0171"), state.dtcs.map { it.code })
        assertEquals(listOf("P0101"), state.pendingDtcs.map { it.code })
        assertEquals(listOf("P0420"), state.permanentDtcs.map { it.code })
    }

    @Test
    fun `engineFamily is independent of other snapshot content`() {
        // The PID-typing split Will's tests asserted on doesn't apply to our
        // shape (we use List<LiveReading> not typed fields), but we can still
        // verify that engineFamily attaches as a free-floating tag — every
        // snapshot carries one, regardless of what its live readings contain.
        val petrol = snapshot(engineFamily = EngineFamily.PETROL)
        val diesel = snapshot(engineFamily = EngineFamily.DIESEL)
        val hybrid = snapshot(engineFamily = EngineFamily.HYBRID)
        assertEquals(EngineFamily.PETROL, petrol.engineFamily)
        assertEquals(EngineFamily.DIESEL, diesel.engineFamily)
        assertEquals(EngineFamily.HYBRID, hybrid.engineFamily)
    }

    @Test
    fun `VehicleInfo vin defaults to null and can be set`() {
        val noVin = VehicleInfo(year = 2009, make = "Toyota", model = "Corolla",
            mileage = 187_000, displayName = "2009 Corolla")
        assertNull(noVin.vin)

        val withVin = VehicleInfo(year = 2008, make = "Toyota", model = "Hilux",
            mileage = 230_000, displayName = "2008 Hilux",
            vin = "JTFBT22P100123456")
        assertEquals("JTFBT22P100123456", withVin.vin)
    }

    @Test
    fun `DataSource values cover all three transport modes`() {
        // This test fails if someone accidentally removes or renames a DataSource value,
        // which would silently break the offline badge logic in the UI.
        val sources = DataSource.values().map { it.name }.toSet()
        assertTrue("FIXTURE missing", "FIXTURE" in sources)
        assertTrue("EMULATOR missing", "EMULATOR" in sources)
        assertTrue("BLUETOOTH missing", "BLUETOOTH" in sources)
    }

    @Test
    fun `EngineFamily UNKNOWN is the safe default`() {
        assertEquals(EngineFamily.UNKNOWN, snapshot().engineFamily)
    }

    @Test
    fun `EngineFamily carries HYBRID for future hybrid adapters`() {
        // HYBRID has no current consumer but stays in the enum so a hybrid
        // OBD adapter doesn't force a schema change on first contact.
        val families = EngineFamily.values().map { it.name }.toSet()
        assertTrue("HYBRID missing", "HYBRID" in families)
    }
}
