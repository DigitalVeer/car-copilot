package com.example.carcopilot.data

import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.time.Instant

/**
 * Connects to the Python OBD-II emulator (emulator/obd_emulator.py) over
 * WiFi TCP and speaks ELM327 AT commands to read live PIDs and DTCs.
 *
 * Each [readSnapshot] call opens a fresh TCP connection, runs the handshake,
 * polls all PIDs, and closes the socket. Stateless by design — no reconnect
 * loop, no keep-alive. The BluetoothSPP transport will mirror this structure.
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
                session.cmd("ATE0")   // echo off
                session.cmd("ATH0")   // headers off
                session.cmd("ATSP0")  // auto protocol

                val confirmed = decodeDtcFrame(session.cmd("03"))
                val pending   = decodeDtcFrame(session.cmd("07"))
                val permanent = decodeDtcFrame(session.cmd("0A"))

                val pidSet = if (engineFamily == EngineFamily.DIESEL) DIESEL_PIDS else PETROL_PIDS
                val readings = pidSet.mapNotNull { spec ->
                    decodePid(spec, session.cmd("01${spec.pid}"))
                }

                _connectionState.value = ConnectionState.Connected
                OBDSnapshot(
                    source = DataSource.EMULATOR,
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
}

// ── ELM327 session ────────────────────────────────────────────────────────────

private class Elm327Session(
    private val input: InputStream,
    private val output: OutputStream,
) {
    fun awaitPrompt() = readUntilPrompt()

    fun cmd(command: String): String {
        output.write("$command\r".toByteArray())
        output.flush()
        return readUntilPrompt()
    }

    private fun readUntilPrompt(): String {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1 || b.toChar() == '>') break
            buf.append(b.toChar())
        }
        // Emulator sends "RESPONSE\r\r>" (with or without echo prefix before first \r).
        // Split by \r and take the last non-blank segment to handle both cases.
        return buf.toString().split('\r').lastOrNull { it.isNotBlank() }?.trim() ?: ""
    }
}

// ── DTC decoding ──────────────────────────────────────────────────────────────

private fun decodeDtcFrame(raw: String): List<DTC> {
    val parts = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 2 || parts[0] !in setOf("43", "47", "4A")) return emptyList()
    val count = parts[1].toIntOrNull(16) ?: return emptyList()
    if (count == 0 || parts.size < 2 + count * 2) return emptyList()
    return (0 until count).map { i ->
        val b1 = parts[2 + i * 2].toInt(16)
        val b2 = parts[3 + i * 2].toInt(16)
        val code = decodeDtcBytes(b1, b2)
        DTC(code = code, description = DTC_DESCRIPTIONS[code] ?: code)
    }
}

private fun decodeDtcBytes(b1: Int, b2: Int): String {
    val prefix = when ((b1 shr 6) and 0x03) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
    val d1 = (b1 shr 4) and 0x03
    val d2 = b1 and 0x0F
    val d34 = "%02X".format(b2)
    return "$prefix$d1${d2.toString(16).uppercase()}$d34"
}

private val DTC_DESCRIPTIONS = mapOf(
    "P0171" to "System too lean (Bank 1)",
    "P0301" to "Cylinder 1 misfire detected",
    "P0302" to "Cylinder 2 misfire detected",
    "P0303" to "Cylinder 3 misfire detected",
    "P0304" to "Cylinder 4 misfire detected",
    "P0087" to "Fuel rail pressure too low",
    "P1229" to "SCV (suction control valve) circuit fault",
    "P0401" to "EGR flow insufficient",
    "P0420" to "Catalyst efficiency below threshold (Bank 1)",
)

// ── PID decoding ──────────────────────────────────────────────────────────────

private data class PidSpec(
    val pid: String,
    val display: String,
    val unit: String?,
    val decode: (List<Int>) -> Double?,
    val statusFn: (Double) -> Pair<LiveStatus, String?>,
)

private val NO_STATUS: (Double) -> Pair<LiveStatus, String?> = { LiveStatus.normal to null }

private val COMMON_PIDS = listOf(
    PidSpec("0C", "RPM", "rpm",
        { b -> if (b.size >= 2) ((b[0] * 256.0) + b[1]) / 4 else null },
        { v ->
            when {
                v < 600 -> LiveStatus.warning to "stalling"
                v < 800 -> LiveStatus.warning to "rough"
                else -> LiveStatus.normal to null
            }
        }
    ),
    PidSpec("05", "Coolant temperature", "°C",
        { b -> b.firstOrNull()?.let { it.toDouble() - 40 } },
        { v ->
            when {
                v > 105 -> LiveStatus.severe to "overheating"
                v > 100 -> LiveStatus.warning to "hot"
                v < 60  -> LiveStatus.warning to "cold"
                else    -> LiveStatus.normal to null
            }
        }
    ),
    PidSpec("0D", "Vehicle speed", "kph",
        { b -> b.firstOrNull()?.toDouble() },
        NO_STATUS
    ),
    PidSpec("42", "Battery voltage", "V",
        { b -> if (b.size >= 2) ((b[0] * 256.0) + b[1]) / 1000 else null },
        { v ->
            when {
                v < 11.5 -> LiveStatus.severe to "low"
                v < 12.2 -> LiveStatus.warning to "weak"
                else     -> LiveStatus.normal to null
            }
        }
    ),
)

private val PETROL_PIDS = COMMON_PIDS + listOf(
    PidSpec("10", "MAF sensor", "g/s",
        { b -> if (b.size >= 2) ((b[0] * 256.0) + b[1]) / 100 else null },
        { v -> if (v < 2.0) LiveStatus.warning to "low" else LiveStatus.normal to null }
    ),
    PidSpec("06", "Short-term fuel trim", "%",
        { b -> b.firstOrNull()?.let { (it.toDouble() - 128) * 100 / 128 } },
        { v -> if (kotlin.math.abs(v) >= 10) LiveStatus.warning to "compensating" else LiveStatus.normal to null }
    ),
    PidSpec("07", "Long-term fuel trim", "%",
        { b -> b.firstOrNull()?.let { (it.toDouble() - 128) * 100 / 128 } },
        { v ->
            when {
                kotlin.math.abs(v) >= 20 -> LiveStatus.severe to "far out of range"
                kotlin.math.abs(v) >= 10 -> LiveStatus.warning to "compensating"
                else -> LiveStatus.normal to null
            }
        }
    ),
    PidSpec("14", "O₂ sensor (bank 1)", "V",
        { b -> b.firstOrNull()?.let { it.toDouble() / 200 } },
        { v ->
            when {
                v > 0.85 -> LiveStatus.warning to "rich"
                v < 0.1  -> LiveStatus.warning to "lean"
                else     -> LiveStatus.normal to null
            }
        }
    ),
)

private val DIESEL_PIDS = COMMON_PIDS + listOf(
    PidSpec("04", "Engine load", "%",
        { b -> b.firstOrNull()?.let { it.toDouble() * 100 / 255 } },
        NO_STATUS
    ),
    PidSpec("23", "Fuel rail pressure", "kPa",
        { b -> if (b.size >= 2) ((b[0] * 256.0) + b[1]) * 10 else null },
        { v ->
            when {
                v < 20_000 -> LiveStatus.severe to "critically low"
                v < 30_000 -> LiveStatus.warning to "low"
                else       -> LiveStatus.normal to null
            }
        }
    ),
)

private fun decodePid(spec: PidSpec, raw: String): LiveReading? {
    // Response format (headers off): "41 {PID} {data bytes...}" or "NO DATA"
    val parts = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 3 || parts[0] != "41") return null
    val dataBytes = parts.drop(2).mapNotNull { it.toIntOrNull(16) }
    val value = spec.decode(dataBytes) ?: return null
    val (status, note) = spec.statusFn(value)
    val display = if (value == kotlin.math.floor(value)) value.toInt().toString()
                  else "%.2f".format(value)
    return LiveReading(key = spec.display, value = display, unit = spec.unit, status = status, note = note)
}
