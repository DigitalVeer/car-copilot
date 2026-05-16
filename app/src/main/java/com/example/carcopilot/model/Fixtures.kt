package com.example.carcopilot.model

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the misfire Issue from the bundled misfire.json fixture +
 * the deterministic P0301 entry from DTC_TABLE. Mirrors the Python
 * classifier + pipeline output (reference/python-src/{classifier,pipeline}.py).
 */
object Fixtures {
    fun loadMisfireIssue(context: Context): Issue {
        val raw = context.assets.open("misfire.json").bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(raw) as JsonObject
        val vehicleJson = fixture["vehicle"] as JsonObject
        val liveData = fixture["live_data"] as JsonObject

        val vehicle = VehicleInfo(
            year = vehicleJson["year"]!!.jsonPrimitive.content.toInt(),
            make = vehicleJson["make"]!!.jsonPrimitive.content,
            model = vehicleJson["model"]!!.jsonPrimitive.content,
            mileage = vehicleJson["mileage"]?.jsonPrimitive?.content?.toIntOrNull(),
            displayName = vehicleJson["display_name"]!!.jsonPrimitive.content,
        )

        return Issue(
            id = "20260514T194211Z-P0301",
            vehicle = vehicle,
            severity = Severity.warning,
            route = Route.diy,
            category = "misfire",
            title = "Replace ignition coil — cylinder 1",
            subtitle = "A small black block on top of the engine. No lift needed. Just a 10mm socket.",
            meta = IssueMeta(
                costUsdMin = 40,
                costUsdMax = 60,
                timeMinutes = 30,
                difficulty = "easy",
                drivability = "safe for short trips",
            ),
            dtcs = listOf(DTC(code = "P0301", description = "Cylinder 1 misfire detected")),
            liveReadings = buildReadings(liveData),
        )
    }

    /** Mirrors pipeline._build_readings + _format_value. */
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
        )
    }
}
