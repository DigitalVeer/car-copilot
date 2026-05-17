package com.example.carcopilot.model

/**
 * Raw data off the OBD-II bus — no interpretation, no severity, no diagnosis.
 * This is the input to the pipeline. Issue is the output.
 *
 * Pipeline: VehicleState → rules engine → RAG lookup → Gemma → Issue
 *
 * PID nullability has two distinct meanings depending on engineFamily:
 *   - PETROL: diesel-only fields (railPressure, boost, egr) are structurally absent — always null
 *   - DIESEL: petrol-only fields (maf, stft, ltft, o2) are structurally absent — always null
 *   - Either: a universal field is null when the ECU hasn't responded yet or doesn't support it
 */

enum class DataSource { FIXTURE, EMULATOR, BLUETOOTH }

// Manual config for now — auto-detect from VIN character 8 is a future improvement.
enum class EngineFamily { PETROL, DIESEL, HYBRID, UNKNOWN }

data class VehicleState(

    // ── Provenance ───────────────────────────────────────────────────────────
    val source: DataSource,
    val capturedAt: Long = System.currentTimeMillis(),

    // ── Identity ─────────────────────────────────────────────────────────────
    // VIN from Mode 9 PID 02. Null if ECU doesn't support Mode 9.
    val vin: String? = null,
    // Set manually or derived from VIN decode. Governs which PIDs are structurally present.
    val engineFamily: EngineFamily = EngineFamily.UNKNOWN,

    // ── Fault codes ──────────────────────────────────────────────────────────
    val confirmedDtcs: List<String> = emptyList(),   // Mode 03 — active faults
    val pendingDtcs: List<String> = emptyList(),     // Mode 07 — seen but not confirmed
    val permanentDtcs: List<String> = emptyList(),   // Mode 0A — survive battery disconnect

    // ── Universal PIDs (petrol + diesel) ─────────────────────────────────────
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val engineLoadPct: Float? = null,
    val throttlePct: Float? = null,
    val vehicleSpeedKph: Int? = null,
    val batteryVolts: Float? = null,
    val intakeAirTempC: Int? = null,

    // ── Petrol-only PIDs ─────────────────────────────────────────────────────
    // Always null on diesel — not "unread", structurally absent.
    val mafGPerSec: Float? = null,
    val stftBank1Pct: Float? = null,       // Short-term fuel trim — spikes fast
    val ltftBank1Pct: Float? = null,       // Long-term fuel trim — diagnostic gold
    val o2Bank1S1Volts: Float? = null,     // Upstream O2 — fuel mixture
    val o2Bank1S2Volts: Float? = null,     // Downstream O2 — catalyst health
    val intakeManifoldKpa: Int? = null,

    // ── Diesel-only PIDs ─────────────────────────────────────────────────────
    // Always null on petrol — not "unread", structurally absent.
    val fuelRailPressureKpa: Int? = null,
    val fuelRailPressureTargetKpa: Int? = null,
    val boostPressureKpa: Int? = null,
    val egrPositionPct: Float? = null,

    // ── Freeze frame ─────────────────────────────────────────────────────────
    // PID snapshot captured at the moment the first DTC was set.
    // Often more diagnostic than current live readings — the fault may be intermittent.
    val freezeFrame: FreezeFrame? = null,
)

data class FreezeFrame(
    val triggeringDtc: String,
    // Universal
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val engineLoadPct: Float? = null,
    val vehicleSpeedKph: Int? = null,
    // Petrol
    val stftBank1Pct: Float? = null,
    val ltftBank1Pct: Float? = null,
    val mafGPerSec: Float? = null,
    // Diesel
    val fuelRailPressureKpa: Int? = null,
)
