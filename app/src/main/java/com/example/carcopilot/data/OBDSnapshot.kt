package com.example.carcopilot.data

import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.VehicleInfo

/**
 * One adapter-grade snapshot of the vehicle's current state — what a real
 * Bluetooth OBD-II adapter could plausibly return on a single scan.
 *
 * Classifier outputs (severity, route, narrative title, walkthrough text,
 * mechanic draft) deliberately live *outside* this type. Those are derived
 * from the DTC codes via [DTCTable] in the Phase-11B IssueBuilder; an
 * adapter has no business inventing them.
 *
 * Reuses [VehicleInfo], [DTC], and [LiveReading] from model/Schema.kt to
 * avoid a translation layer that exists only to feel clean. The DTC
 * descriptions ride along on each [DTC] because that's how the existing
 * schema is shaped — for the fixture impl they're hardcoded next to the
 * fixture data; for a future BLE impl they'd come from the adapter's
 * onboard code database.
 */
data class OBDSnapshot(
    val capturedAt: String,
    val vehicle: VehicleInfo,
    val dtcs: List<DTC>,
    val pendingDtcs: List<DTC>,
    val liveReadings: List<LiveReading>,
)
