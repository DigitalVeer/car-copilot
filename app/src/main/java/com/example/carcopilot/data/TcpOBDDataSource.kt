package com.example.carcopilot.data

import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Socket
import java.time.Instant

/**
 * Connects to the Python OBD-II emulator (emulator/obd_emulator.py) over
 * WiFi TCP and speaks ELM327 AT commands to read live PIDs and DTCs.
 *
 * Each [readSnapshot] call opens a fresh TCP connection, runs the handshake,
 * polls all PIDs, and closes the socket. Stateless by design.
 *
 * Vehicle info is queried via the non-standard ATVI command after the
 * handshake. The emulator responds with a JSON blob containing year, make,
 * model, mileage, displayName, and engineFamily so switching scenarios in
 * the TUI is reflected immediately in the app without a restart. Real
 * ELM327 adapters return "?" for unknown AT commands — [readSnapshot] falls
 * back to the constructor-provided [vehicle] and [engineFamily] when the
 * response cannot be parsed as JSON.
 *
 * On Android emulator: host = "10.0.2.2" (redirects to Mac localhost).
 * On a real device over WiFi: host = Mac's LAN IP (e.g. "192.168.1.5").
 */
class TcpOBDDataSource(
    private val host: String,
    private val port: Int,
    private val vehicle: VehicleInfo,
    private val engineFamily: EngineFamily = EngineFamily.UNKNOWN,
) : OBDDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun readSnapshot(): Result<OBDSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            _connectionState.value = ConnectionState.Connecting
            Socket(host, port).use { socket ->
                socket.soTimeout = 5_000
                val session = Elm327Session(socket.inputStream, socket.outputStream)

                session.awaitPrompt()
                session.cmd("ATZ")
                session.cmd("ATE0")
                session.cmd("ATH0")
                session.cmd("ATSP0")

                // Query emulator vehicle info. Real adapters return "?" or "OK";
                // we fall back to constructor values when parsing fails.
                val vi = parseVehicleInfo(session.cmd("ATVI"))
                val resolvedVehicle = vi?.first ?: vehicle
                val resolvedFamily  = vi?.second ?: engineFamily

                val confirmed = decodeDtcFrame(session.cmd("03"))
                val pending   = decodeDtcFrame(session.cmd("07"))
                val permanent = decodeDtcFrame(session.cmd("0A"))

                val pidSet = if (resolvedFamily == EngineFamily.DIESEL) DIESEL_PIDS else PETROL_PIDS
                val readings = pidSet.mapNotNull { spec ->
                    decodePid(spec, session.cmd("01${spec.pid}"))
                }

                _connectionState.value = ConnectionState.Connected
                OBDSnapshot(
                    source = DataSource.EMULATOR,
                    capturedAt = Instant.now().toString(),
                    vehicle = resolvedVehicle,
                    engineFamily = resolvedFamily,
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

    private fun parseVehicleInfo(raw: String): Pair<VehicleInfo, EngineFamily>? = try {
        val obj = JSONObject(raw)
        val info = VehicleInfo(
            year        = obj.getInt("year"),
            make        = obj.getString("make"),
            model       = obj.getString("model"),
            mileage     = obj.optInt("mileage", 0),
            displayName = obj.getString("displayName"),
        )
        val family = when (obj.optString("engineFamily", "")) {
            "PETROL" -> EngineFamily.PETROL
            "DIESEL" -> EngineFamily.DIESEL
            else     -> EngineFamily.UNKNOWN
        }
        info to family
    } catch (_: Exception) {
        null
    }
}
