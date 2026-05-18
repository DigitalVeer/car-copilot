package com.example.carcopilot.data

import android.content.Context
import com.example.carcopilot.model.DTC
import com.example.carcopilot.model.LiveReading
import com.example.carcopilot.model.LiveStatus
import com.example.carcopilot.model.VehicleInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Which fixture file under `app/src/main/assets/` to read. One-line edit
 * to switch the FIXTURE data source between scenarios for on-device smoke:
 *
 *   "misfire.json"          — 2009 Corolla, P0301 cylinder-1 misfire
 *   "hilux_fuel_rail.json"  — 2012 Hilux,   P0087 low fuel-rail pressure
 *
 * Only consulted on FIXTURE builds — EMULATOR / BLUETOOTH paths use their
 * own data sources and ignore this constant.
 */
private const val ACTIVE_FIXTURE = "hilux_fuel_rail.json"

/**
 * Fixture-backed [OBDDataSource]. Reads the fixture named by
 * [ACTIVE_FIXTURE] from assets, decodes live PIDs against [ReadingSpec],
 * and returns a snapshot equivalent to what the legacy
 * [com.example.carcopilot.model.Fixtures.loadMisfireIssue] path produces.
 *
 * Description-for-code lookup is local to this file in 11A — it's the
 * fixture's stand-in for the adapter's onboard DTC description database.
 * A future BLE impl would read descriptions off the adapter itself.
 *
 * Always reports [ConnectionState.Connected] — there's nothing to pair
 * with. A demo variant could simulate a Disconnected → Connecting →
 * Connected handshake on first read; not worth the wiring until UI
 * surfaces consume connection state.
 */
class FixtureOBDDataSource(private val context: Context) : OBDDataSource {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun readSnapshot(): Result<OBDSnapshot> = runCatching {
        val raw = context.assets.open(ACTIVE_FIXTURE).bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(raw) as JsonObject
        val vehicleJson = fixture["vehicle"] as JsonObject
        val liveData = fixture["live_data"] as JsonObject
        val capturedAt = fixture["captured_at"]!!.jsonPrimitive.content

        val vehicle = VehicleInfo(
            year = vehicleJson["year"]!!.jsonPrimitive.content.toInt(),
            make = vehicleJson["make"]!!.jsonPrimitive.content,
            model = vehicleJson["model"]!!.jsonPrimitive.content,
            mileage = vehicleJson["mileage"]?.jsonPrimitive?.content?.toIntOrNull(),
            displayName = vehicleJson["display_name"]!!.jsonPrimitive.content,
        )

        OBDSnapshot(
            source = DataSource.FIXTURE,
            capturedAt = capturedAt,
            vehicle = vehicle,
            // Honor the fixture's engine_family if present (e.g. the hilux
            // diesel scenario sets DIESEL); the legacy misfire fixture
            // doesn't carry the field, so fall back to PETROL — the 2009
            // Corolla 1ZZ-FE that the original demo targets. UNKNOWN is
            // reserved for real-hardware snapshots that arrive before VIN
            // decode runs.
            engineFamily = parseEngineFamily(fixture["engine_family"]?.jsonPrimitive?.content),
            dtcs = readDtcCodes(fixture, "raw_dtcs").map { it.toDtc() },
            pendingDtcs = readDtcCodes(fixture, "pending_dtcs").map { it.toDtc() },
            permanentDtcs = emptyList(),
            liveReadings = buildReadings(liveData),
        )
    }

    private fun parseEngineFamily(raw: String?): EngineFamily = when (raw?.uppercase()) {
        "PETROL", "GASOLINE" -> EngineFamily.PETROL
        "DIESEL" -> EngineFamily.DIESEL
        "HYBRID" -> EngineFamily.HYBRID
        else -> EngineFamily.PETROL
    }

    private fun readDtcCodes(fixture: JsonObject, field: String): List<String> {
        val arr = fixture[field] as? JsonArray ?: return emptyList()
        return arr.map { it.jsonPrimitive.content }
    }

    private fun String.toDtc(): DTC = DTC(code = this, description = descriptionFor(this))

    private fun descriptionFor(code: String): String = when (code) {
        "P0301" -> "Cylinder 1 misfire detected"
        else -> code
    }

    /** Mirrors the legacy pipeline._build_readings + _format_value. */
    private fun buildReadings(liveData: JsonObject): List<LiveReading> {
        return liveData.entries.map { (key, rawEl) ->
            val rawStr = rawEl.jsonPrimitive.content
            val asDouble = rawStr.toDoubleOrNull()
            val spec = ReadingSpec.SPECS[key]
            val displayKey = spec?.display ?: key
            val unit = spec?.unit
            val (status, note) = spec?.let { s ->
                if (asDouble != null) s.statusFn(asDouble) else LiveStatus.normal to null
            } ?: (LiveStatus.normal to null)
            val display = if (asDouble != null && asDouble == asDouble.toInt().toDouble())
                asDouble.toInt().toString() else rawStr
            LiveReading(key = displayKey, value = display, unit = unit, status = status, note = note)
        }
    }
}

private data class ReadingSpec(
    val display: String,
    val unit: String?,
    val statusFn: (Double) -> Pair<LiveStatus, String?>,
) {
    companion object {
        private val normal = LiveStatus.normal to null as String?
        val SPECS: Map<String, ReadingSpec> = mapOf(
            "rpm" to ReadingSpec("RPM (idle)", "rpm") { v ->
                when {
                    v < 600 -> LiveStatus.warning to "stalling"
                    v < 800 -> LiveStatus.warning to "rough"
                    else -> normal
                }
            },
            "coolant_temp_c" to ReadingSpec("Coolant temperature", "°C") { v ->
                when {
                    v > 105 -> LiveStatus.severe to "overheating"
                    v > 100 -> LiveStatus.warning to "hot"
                    v < 60 -> LiveStatus.warning to "cold"
                    else -> normal
                }
            },
            "battery_v" to ReadingSpec("Battery voltage", "V") { v ->
                when {
                    v < 11.5 -> LiveStatus.severe to "low"
                    v < 12.2 -> LiveStatus.warning to "weak"
                    else -> normal
                }
            },
            "o2_bank1_v" to ReadingSpec("O₂ sensor (bank 1)", "V") { v ->
                when {
                    v > 0.85 -> LiveStatus.warning to "rich"
                    v < 0.1 -> LiveStatus.warning to "lean"
                    else -> normal
                }
            },
            "fuel_trim_short_pct" to ReadingSpec("Short-term fuel trim", "%") { v ->
                if (kotlin.math.abs(v) >= 10) LiveStatus.warning to "compensating" else normal
            },
            "vehicle_speed_kph" to ReadingSpec("Vehicle speed", "kph") { _ -> normal },
            // Diesel fuel-rail pressure — display name MUST match the key
            // RulesEngine.classifyFuelRail looks up for P0087 / P1229.
            // Normal idle on a 2KD-FTV is ~34,500 kPa; thresholds mirror
            // the bands RulesEngine uses to assign HIGH confidence.
            "fuel_rail_kpa" to ReadingSpec("Fuel rail pressure", "kPa") { v ->
                when {
                    v < 20_000 -> LiveStatus.severe to "critically low"
                    v < 30_000 -> LiveStatus.warning to "low"
                    else -> normal
                }
            },
            "engine_load_pct" to ReadingSpec("Engine load", "%") { _ -> normal },
        )
    }
}
