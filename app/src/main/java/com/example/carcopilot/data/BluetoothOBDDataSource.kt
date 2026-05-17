package com.example.carcopilot.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Connects to a paired ELM327 Bluetooth dongle using Classic Bluetooth SPP
 * and speaks the same ELM327 AT command protocol as [TcpOBDDataSource].
 *
 * Prerequisites:
 *   1. Pair the dongle in Android Settings → Bluetooth before launching.
 *   2. Pass the dongle's Bluetooth name (e.g. "OBDII", "ELM327", "V-LINK")
 *      as [deviceName]. The adapter scans paired devices for a name match.
 *
 * Permissions required in manifest (already added):
 *   BLUETOOTH_CONNECT (API 31+), BLUETOOTH / BLUETOOTH_ADMIN (API < 31)
 *
 * Each [readSnapshot] call opens a fresh RFCOMM connection, runs the
 * handshake, polls all PIDs, and closes the socket — same stateless
 * pattern as [TcpOBDDataSource].
 */
class BluetoothOBDDataSource(
    private val context: Context,
    private val deviceName: String,
    private val vehicle: VehicleInfo,
    private val engineFamily: EngineFamily = EngineFamily.UNKNOWN,
) : OBDDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun readSnapshot(): Result<OBDSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            _connectionState.value = ConnectionState.Connecting

            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter
                ?: error("Bluetooth not available on this device")

            check(adapter.isEnabled) { "Bluetooth is off — enable it in Settings" }

            @Suppress("MissingPermission")
            val device = adapter.bondedDevices
                .firstOrNull { it.name == deviceName }
                ?: error("Dongle \"$deviceName\" not found in paired devices. Pair it in Settings → Bluetooth first.")

            @Suppress("MissingPermission")
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

            // Stop discovery if running — it slows the RFCOMM connection
            @Suppress("MissingPermission")
            if (adapter.isDiscovering) adapter.cancelDiscovery()

            socket.use { s ->
                @Suppress("MissingPermission")
                s.connect()

                val session = Elm327Session(s.inputStream, s.outputStream)

                session.awaitPrompt()
                session.cmd("ATZ")
                session.cmd("ATE0")
                session.cmd("ATH0")
                session.cmd("ATSP0")

                val confirmed = decodeDtcFrame(session.cmd("03"))
                val pending   = decodeDtcFrame(session.cmd("07"))
                val permanent = decodeDtcFrame(session.cmd("0A"))

                val pidSet = if (engineFamily == EngineFamily.DIESEL) DIESEL_PIDS else PETROL_PIDS
                val readings = pidSet.mapNotNull { spec ->
                    decodePid(spec, session.cmd("01${spec.pid}"))
                }

                _connectionState.value = ConnectionState.Connected
                OBDSnapshot(
                    source = DataSource.BLUETOOTH,
                    capturedAt = Instant.now().toString(),
                    vehicle = vehicle,
                    engineFamily = engineFamily,
                    dtcs = confirmed,
                    pendingDtcs = pending,
                    permanentDtcs = permanent,
                    liveReadings = readings,
                )
            }
        }.onFailure { e ->
            _connectionState.value = ConnectionState.Failed(e.message ?: "connection failed")
        }
    }

    companion object {
        // Standard Bluetooth SPP UUID — all ELM327 dongles use this
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
