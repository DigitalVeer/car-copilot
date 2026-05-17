package com.example.carcopilot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the VehicleState schema contract.
 *
 * VehicleState is the input to the entire diagnostic pipeline. These tests
 * verify that the data class defaults, the diesel/petrol field split, and
 * the FreezeFrame structure behave as the rest of the pipeline expects.
 *
 * Add a test whenever a field is added, removed, or its semantics change.
 */
class VehicleStateTest {

    @Test
    fun `minimal construction has safe defaults`() {
        val state = VehicleState(source = DataSource.FIXTURE)
        assertNull(state.vin)
        assertEquals(EngineFamily.UNKNOWN, state.engineFamily)
        assertTrue(state.confirmedDtcs.isEmpty())
        assertTrue(state.pendingDtcs.isEmpty())
        assertTrue(state.permanentDtcs.isEmpty())
        assertNull(state.freezeFrame)
        assertTrue(state.capturedAt > 0)
    }

    @Test
    fun `all three DTC lists are independent`() {
        val state = VehicleState(
            source = DataSource.EMULATOR,
            confirmedDtcs = listOf("P0171"),
            pendingDtcs = listOf("P0101"),
            permanentDtcs = listOf("P0420"),
        )
        assertEquals(listOf("P0171"), state.confirmedDtcs)
        assertEquals(listOf("P0101"), state.pendingDtcs)
        assertEquals(listOf("P0420"), state.permanentDtcs)
    }

    @Test
    fun `petrol fields set, diesel fields null`() {
        val state = VehicleState(
            source = DataSource.BLUETOOTH,
            engineFamily = EngineFamily.PETROL,
            mafGPerSec = 1.85f,
            stftBank1Pct = 3.1f,
            ltftBank1Pct = 5.5f,
            o2Bank1S1Volts = 0.45f,
            o2Bank1S2Volts = 0.72f,
        )
        assertEquals(EngineFamily.PETROL, state.engineFamily)
        assertNotNull(state.mafGPerSec)
        assertNotNull(state.stftBank1Pct)
        assertNotNull(state.ltftBank1Pct)
        // Diesel fields absent
        assertNull(state.fuelRailPressureKpa)
        assertNull(state.boostPressureKpa)
        assertNull(state.egrPositionPct)
    }

    @Test
    fun `diesel fields set, petrol fields null`() {
        val state = VehicleState(
            source = DataSource.EMULATOR,
            engineFamily = EngineFamily.DIESEL,
            fuelRailPressureKpa = 34_500,
            fuelRailPressureTargetKpa = 35_000,
            boostPressureKpa = 120,
        )
        assertEquals(EngineFamily.DIESEL, state.engineFamily)
        assertNotNull(state.fuelRailPressureKpa)
        assertNotNull(state.boostPressureKpa)
        // Petrol fields absent
        assertNull(state.mafGPerSec)
        assertNull(state.stftBank1Pct)
        assertNull(state.ltftBank1Pct)
        assertNull(state.o2Bank1S1Volts)
    }

    @Test
    fun `universal PIDs present on both engine families`() {
        val petrol = VehicleState(
            source = DataSource.FIXTURE,
            engineFamily = EngineFamily.PETROL,
            rpm = 800,
            coolantTempC = 88,
            engineLoadPct = 22f,
            throttlePct = 14f,
            vehicleSpeedKph = 0,
            batteryVolts = 12.4f,
        )
        val diesel = VehicleState(
            source = DataSource.FIXTURE,
            engineFamily = EngineFamily.DIESEL,
            rpm = 720,
            coolantTempC = 88,
            engineLoadPct = 25f,
            batteryVolts = 12.6f,
        )
        assertEquals(800, petrol.rpm)
        assertEquals(720, diesel.rpm)
        assertEquals(88, petrol.coolantTempC)
        assertEquals(88, diesel.coolantTempC)
    }

    @Test
    fun `freeze frame carries triggering DTC and relevant PIDs`() {
        val ff = FreezeFrame(
            triggeringDtc = "P0171",
            rpm = 820,
            ltftBank1Pct = 22.7f,
            mafGPerSec = 1.3f,
        )
        val state = VehicleState(
            source = DataSource.EMULATOR,
            engineFamily = EngineFamily.PETROL,
            confirmedDtcs = listOf("P0171"),
            freezeFrame = ff,
        )
        assertNotNull(state.freezeFrame)
        assertEquals("P0171", state.freezeFrame!!.triggeringDtc)
        assertEquals(22.7f, state.freezeFrame.ltftBank1Pct!!, 0.01f)
        assertEquals(1.3f, state.freezeFrame.mafGPerSec!!, 0.01f)
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
        assertEquals(EngineFamily.UNKNOWN, VehicleState(source = DataSource.FIXTURE).engineFamily)
    }
}
