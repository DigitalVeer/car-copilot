package com.example.carcopilot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the ELM327 decode functions in Elm327Protocol.kt.
 *
 * Real dongles deviate from the Python emulator in ways that are easy to
 * miss: extra whitespace, mixed-case hex, different line endings. These
 * tests lock the happy path and the most common real-world edge cases so
 * a regression surfaces here before it surfaces as a silent empty snapshot
 * on a live vehicle.
 */
class Elm327ProtocolTest {

    // ── decodeDtcFrame ────────────────────────────────────────────────────────

    @Test
    fun `decodes single confirmed DTC`() {
        // Mode 03 response: "43 01 01 71" → P0171
        val dtcs = decodeDtcFrame("43 01 01 71")
        assertEquals(1, dtcs.size)
        assertEquals("P0171", dtcs[0].code)
    }

    @Test
    fun `decodes P0301 misfire`() {
        // "43 01 03 01" → P0301
        val dtcs = decodeDtcFrame("43 01 03 01")
        assertEquals(1, dtcs.size)
        assertEquals("P0301", dtcs[0].code)
    }

    @Test
    fun `decodes two DTCs in one frame`() {
        // "43 02 01 71 03 01" → P0171 + P0301
        val dtcs = decodeDtcFrame("43 02 01 71 03 01")
        assertEquals(2, dtcs.size)
        assertEquals("P0171", dtcs[0].code)
        assertEquals("P0301", dtcs[1].code)
    }

    @Test
    fun `decodes pending DTCs (mode 07, response byte 47)`() {
        val dtcs = decodeDtcFrame("47 01 01 71")
        assertEquals(1, dtcs.size)
        assertEquals("P0171", dtcs[0].code)
    }

    @Test
    fun `decodes permanent DTCs (mode 0A, response byte 4A)`() {
        val dtcs = decodeDtcFrame("4A 01 03 01")
        assertEquals(1, dtcs.size)
        assertEquals("P0301", dtcs[0].code)
    }

    @Test
    fun `returns empty list when count byte is zero`() {
        assertTrue(decodeDtcFrame("43 00").isEmpty())
    }

    @Test
    fun `returns empty list for NO DATA response`() {
        assertTrue(decodeDtcFrame("NO DATA").isEmpty())
    }

    @Test
    fun `returns empty list for empty string`() {
        assertTrue(decodeDtcFrame("").isEmpty())
    }

    @Test
    fun `returns empty list when response byte is unrecognised`() {
        // Wrong mode response byte — should not crash
        assertTrue(decodeDtcFrame("41 01 01 71").isEmpty())
    }

    @Test
    fun `tolerates extra whitespace between bytes`() {
        // Some dongles insert extra spaces
        val dtcs = decodeDtcFrame("43  01  01  71")
        assertEquals(1, dtcs.size)
        assertEquals("P0171", dtcs[0].code)
    }

    @Test
    fun `decodes P0087 diesel fuel rail DTC`() {
        // P0087 → prefix P (0), 0, 8, 7 → bytes 0x00 0x87
        val dtcs = decodeDtcFrame("43 01 00 87")
        assertEquals("P0087", dtcs[0].code)
    }

    @Test
    fun `attaches known description from DTC_DESCRIPTIONS`() {
        val dtcs = decodeDtcFrame("43 01 01 71")
        assertEquals("System too lean (Bank 1)", dtcs[0].description)
    }

    @Test
    fun `falls back to code string for unknown DTC`() {
        // Unknown code — description should be the code itself, not blank
        val dtcs = decodeDtcFrame("43 01 0F FF")
        assertEquals(1, dtcs.size)
        assertEquals(dtcs[0].code, dtcs[0].description)
    }

    @Test
    fun `decodes chassis code C-prefix`() {
        // C-prefix: top two bits of b1 = 01 → b1 = 0x40
        val dtcs = decodeDtcFrame("43 01 40 01")
        assertEquals("C0001", dtcs[0].code)
    }

    @Test
    fun `decodes body code B-prefix`() {
        // B-prefix: top two bits of b1 = 10 → b1 = 0x80
        val dtcs = decodeDtcFrame("43 01 80 01")
        assertEquals("B0001", dtcs[0].code)
    }

    @Test
    fun `decodes network code U-prefix`() {
        // U-prefix: top two bits of b1 = 11 → b1 = 0xC0
        val dtcs = decodeDtcFrame("43 01 C0 01")
        assertEquals("U0001", dtcs[0].code)
    }

    // ── decodePid ─────────────────────────────────────────────────────────────

    @Test
    fun `decodes RPM from mode 01 response`() {
        // "41 0C 0C 80" → (0x0C*256 + 0x80) / 4 = (3072 + 128) / 4 = 800 rpm
        val spec = PETROL_PIDS.first { it.pid == "0C" }
        val reading = decodePid(spec, "41 0C 0C 80")!!
        assertEquals("800", reading.value)
        assertEquals("rpm", reading.unit)
    }

    @Test
    fun `decodes coolant temperature`() {
        // "41 05 80" → 0x80 - 40 = 128 - 40 = 88°C
        val spec = PETROL_PIDS.first { it.pid == "05" }
        val reading = decodePid(spec, "41 05 80")!!
        assertEquals("88", reading.value)
        assertEquals("°C", reading.unit)
    }

    @Test
    fun `decodes MAF sensor`() {
        // "41 10 00 B9" → (0x00*256 + 0xB9) / 100 = 185 / 100 = 1.85 g/s
        val spec = PETROL_PIDS.first { it.pid == "10" }
        val reading = decodePid(spec, "41 10 00 B9")!!
        assertEquals("1.85", reading.value)
    }

    @Test
    fun `decodes long-term fuel trim positive lean`() {
        // "41 07 97" → (0x97 - 128) * 100 / 128 = 23 * 100 / 128 = 17.97%
        val spec = PETROL_PIDS.first { it.pid == "07" }
        val reading = decodePid(spec, "41 07 97")!!
        assertEquals("17.97", reading.value)
    }

    @Test
    fun `decodes battery voltage`() {
        // "41 42 30 70" → (0x30*256 + 0x70) / 1000 = (12288+112)/1000 = 12.4V
        val spec = PETROL_PIDS.first { it.pid == "42" }
        val reading = decodePid(spec, "41 42 30 70")!!
        assertEquals("12.40", reading.value)
    }

    @Test
    fun `returns null for NO DATA`() {
        val spec = PETROL_PIDS.first { it.pid == "0C" }
        assertNull(decodePid(spec, "NO DATA"))
    }

    @Test
    fun `returns null for wrong response mode byte`() {
        // Response byte should be 41, not 43
        val spec = PETROL_PIDS.first { it.pid == "0C" }
        assertNull(decodePid(spec, "43 0C 0C 80"))
    }

    @Test
    fun `returns null for empty response`() {
        val spec = PETROL_PIDS.first { it.pid == "0C" }
        assertNull(decodePid(spec, ""))
    }

    @Test
    fun `RPM status is warning when engine is stalling`() {
        // RPM < 600 → stalling
        val spec = PETROL_PIDS.first { it.pid == "0C" }
        // 400 rpm → (0x06 * 256 + 0x40) / 4 = (1536 + 64) / 4 = 400
        val reading = decodePid(spec, "41 0C 06 40")!!
        assertEquals(com.example.carcopilot.model.LiveStatus.warning, reading.status)
        assertEquals("stalling", reading.note)
    }

    @Test
    fun `coolant status is severe when overheating`() {
        // 110°C → A = 150 = 0x96 → 150 - 40 = 110
        val spec = PETROL_PIDS.first { it.pid == "05" }
        val reading = decodePid(spec, "41 05 96")!!
        assertEquals(com.example.carcopilot.model.LiveStatus.severe, reading.status)
        assertEquals("overheating", reading.note)
    }

    @Test
    fun `battery status is severe when critically low`() {
        // 11.0V → 11000 = 0x2AF8
        val spec = PETROL_PIDS.first { it.pid == "42" }
        val reading = decodePid(spec, "41 42 2A F8")!!
        assertEquals(com.example.carcopilot.model.LiveStatus.severe, reading.status)
    }

    @Test
    fun `diesel fuel rail pressure decodes correctly`() {
        // "41 23 0A F0" → (0x0A*256 + 0xF0) * 10 = (2560+240)*10 = 28000 kPa
        val spec = DIESEL_PIDS.first { it.pid == "23" }
        val reading = decodePid(spec, "41 23 0A F0")!!
        assertEquals("28000", reading.value)
        assertEquals(com.example.carcopilot.model.LiveStatus.warning, reading.status)
    }
}
