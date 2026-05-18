package com.example.carcopilot.data

import com.example.carcopilot.model.Confidence
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.model.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {

    private val vehicle = VehicleInfo(
        year = 2009, make = "Toyota", model = "Corolla",
        mileage = 187_000, displayName = "2009 Corolla",
    )

    private fun snapshot(
        dtcs: List<DTC>,
        readings: List<LiveReading> = emptyList(),
        engineFamily: EngineFamily = EngineFamily.PETROL,
    ) = OBDSnapshot(
        source = DataSource.FIXTURE,
        capturedAt = "2026-05-17T10:00:00Z",
        vehicle = vehicle,
        engineFamily = engineFamily,
        dtcs = dtcs,
        pendingDtcs = emptyList(),
        liveReadings = readings,
    )

    private fun reading(key: String, value: String, status: LiveStatus = LiveStatus.normal) =
        LiveReading(key = key, value = value, status = status)

    // ── P0087 fuel rail ───────────────────────────────────────────────────────

    @Test
    fun `P0087 with critically low rail pressure returns HIGH confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0087", "Fuel rail pressure too low")),
            readings = listOf(reading("Fuel rail pressure", "18000", LiveStatus.severe)),
            engineFamily = EngineFamily.DIESEL,
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals("P0087", result.primaryDtcCode)
        assertTrue(result.supportingSignals.any { "18000" in it })
    }

    @Test
    fun `P0087 with low but not critical rail pressure returns HIGH confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0087", "Fuel rail pressure too low")),
            readings = listOf(reading("Fuel rail pressure", "28000", LiveStatus.warning)),
            engineFamily = EngineFamily.DIESEL,
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.HIGH, result.confidence)
        assertTrue(result.supportingSignals.any { "28000" in it })
    }

    @Test
    fun `P1229 alongside P0087 adds SCV signal`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P1229", "SCV circuit fault")),
            readings = listOf(reading("Fuel rail pressure", "28000", LiveStatus.warning)),
            engineFamily = EngineFamily.DIESEL,
        )
        val result = RulesEngine.classify(snap)
        assertTrue(result.supportingSignals.any { "SCV" in it })
    }

    @Test
    fun `P0087 with no rail reading returns LOW confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0087", "Fuel rail pressure too low")),
            engineFamily = EngineFamily.DIESEL,
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.LOW, result.confidence)
    }

    // ── P0171 lean condition ──────────────────────────────────────────────────

    @Test
    fun `P0171 with high LTFT and low MAF returns HIGH confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0171", "System too lean (Bank 1)")),
            readings = listOf(
                reading("Long-term fuel trim", "18.0", LiveStatus.warning),
                reading("MAF sensor", "1.85", LiveStatus.warning),
            ),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.HIGH, result.confidence)
        assertTrue(result.supportingSignals.any { "18" in it })
        assertTrue(result.supportingSignals.any { "1.85" in it })
    }

    @Test
    fun `P0171 with high LTFT alone returns MEDIUM confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0171", "System too lean (Bank 1)")),
            readings = listOf(reading("Long-term fuel trim", "16.0", LiveStatus.warning)),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `P0171 with mild LTFT returns MEDIUM confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0171", "System too lean (Bank 1)")),
            readings = listOf(reading("Long-term fuel trim", "12.0", LiveStatus.warning)),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `P0171 with no readings returns LOW confidence`() {
        val snap = snapshot(dtcs = listOf(DTC("P0171", "System too lean (Bank 1)")))
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.LOW, result.confidence)
    }

    // ── P0301 misfire ─────────────────────────────────────────────────────────

    @Test
    fun `P0301 with lean O2 and rough RPM returns HIGH confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0301", "Cylinder 1 misfire detected")),
            readings = listOf(
                reading("O₂ sensor (bank 1)", "0.15", LiveStatus.warning),
                reading("RPM", "680", LiveStatus.warning),
            ),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.HIGH, result.confidence)
        assertTrue(result.supportingSignals.any { "lean" in it || "0.15" in it })
    }

    @Test
    fun `P0301 with rich O2 returns HIGH confidence and injector cause`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0301", "Cylinder 1 misfire detected")),
            readings = listOf(reading("O₂ sensor (bank 1)", "0.9", LiveStatus.warning)),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.HIGH, result.confidence)
        assertTrue(result.likelyCause.contains("injector"))
    }

    @Test
    fun `P0303 with rough RPM and no O2 reading returns MEDIUM confidence`() {
        val snap = snapshot(
            dtcs = listOf(DTC("P0303", "Cylinder 3 misfire detected")),
            readings = listOf(reading("RPM", "700", LiveStatus.warning)),
        )
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.MEDIUM, result.confidence)
        assertEquals("P0303", result.primaryDtcCode)
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty DTC list returns LOW confidence`() {
        val snap = snapshot(dtcs = emptyList())
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun `unknown DTC returns LOW confidence with correct code`() {
        val snap = snapshot(dtcs = listOf(DTC("P0420", "Catalyst efficiency below threshold")))
        val result = RulesEngine.classify(snap)
        assertEquals(Confidence.LOW, result.confidence)
        assertEquals("P0420", result.primaryDtcCode)
    }
}
