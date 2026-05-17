package com.example.carcopilot.data

import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between CAR·COPILOT and the OBD-II adapter.
 *
 * Phase 11A introduces the contract; the only implementation today is
 * [FixtureOBDDataSource], but the surface is deliberately shaped for a
 * future Bluetooth adapter — that's why [readSnapshot] is a suspending
 * function returning [Result], and [connectionState] is exposed even
 * though the fixture impl is always [ConnectionState.Connected].
 *
 * Do **not** collapse the suspend or unwrap the Result "because fixtures
 * don't fail." BLE will, and that's the whole reason this interface
 * exists. Phase 12 should be able to drop in a `BluetoothOBDDataSource`
 * without widening this surface.
 */
interface OBDDataSource {
    /**
     * Capture one snapshot of the vehicle's current state. Suspends
     * because a real adapter must round-trip over BLE; returns [Result]
     * because the adapter can fail (out of range, paired-but-not-linked,
     * unsupported PID, bad CRC, vehicle ignition off).
     */
    suspend fun readSnapshot(): Result<OBDSnapshot>

    /**
     * Current adapter link state. Hot [StateFlow] so consumers can render
     * a connection chip, gate scan actions while disconnected, or react
     * to a mid-session drop. The fixture impl emits
     * [ConnectionState.Connected] from construction and never changes.
     */
    val connectionState: StateFlow<ConnectionState>
}

/**
 * Bluetooth adapter link state. [Failed] carries a human-readable reason
 * so the UI can say "out of range" or "pairing rejected" instead of a
 * generic error.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
