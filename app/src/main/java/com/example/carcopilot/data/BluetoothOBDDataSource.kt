package com.example.carcopilot.data

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
 * Connects to a paired ELM327 dongle over Classic Bluetooth SPP.
 *
 * Device discovery: scans paired devices for common OBD name hints
 * (OBD, ELM, VLINK, VEEPEAK, SCAN, EOBD). If none match, falls back
 * to the first paired device. No hardcoded name required — pair the
 * dongle in Android Settings → Bluetooth and it will be found.
 *
 * Each [readSnapshot] opens a fresh RFCOMM connection, runs the ELM327
 * handshake, polls all PIDs, and closes the socket.
 */
class BluetoothOBDDataSource(
    private val context: Context,
    private val vehicle: VehicleInfo,
    private val engineFamily: EngineFamily = EngineFamily.UNKNOWN,
) : OBDDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun readSnapshot(): Result<OBDSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            _connectionState.value = ConnectionState.Connecting

            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter ?: error("Bluetooth not available on this device")

            check(adapter.isEnabled) { "Bluetooth is off — enable it in Settings" }

            @Suppress("MissingPermission")
            val bonded = adapter.bondedDevices.toList()
            check(bonded.isNotEmpty()) {
                "No paired Bluetooth devices. Pair your OBD dongle in Settings → Bluetooth first."
            }

            @Suppress("MissingPermission")
            val device = bonded.firstOrNull { d ->
                OBD_NAME_HINTS.any { hint -> d.name?.contains(hint, ignoreCase = true) == true }
            } ?: bonded.first()

            @Suppress("MissingPermission")
            if (adapter.isDiscovering) adapter.cancelDiscovery()

            @Suppress("MissingPermission")
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

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
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Case-insensitive substrings common across OBD dongle brands
        private val OBD_NAME_HINTS = listOf(
            "OBD", "ELM", "VLINK", "V-LINK", "VEEPEAK", "SCAN", "EOBD", "OBDII", "OBD2",
        )
    }
}
