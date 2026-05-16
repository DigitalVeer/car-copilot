package com.example.carcopilot.model

/**
 * Kotlin mirror of the Python Issue schema (reference/python-src/schema.py).
 * Only the fields needed for the Phase 5 misfire flow are modeled.
 */

enum class Severity { healthy, warning, severe, info }

enum class Route { diy, expert, safety, info }

enum class LiveStatus { normal, warning, severe }

data class VehicleInfo(
    val year: Int,
    val make: String,
    val model: String,
    val mileage: Int?,
    val displayName: String,
)

data class IssueMeta(
    val costUsdMin: Int? = null,
    val costUsdMax: Int? = null,
    val timeMinutes: Int? = null,
    val difficulty: String? = null,
    val drivability: String? = null,
)

data class DTC(
    val code: String,
    val description: String,
    val deferred: Boolean = false,
)

data class LiveReading(
    val key: String,
    val value: String,
    val unit: String? = null,
    val status: LiveStatus = LiveStatus.normal,
    val note: String? = null,
)

data class Issue(
    val id: String,
    val vehicle: VehicleInfo,
    val severity: Severity,
    val route: Route,
    val category: String,
    val title: String,
    val subtitle: String,
    val meta: IssueMeta,
    val dtcs: List<DTC>,
    val liveReadings: List<LiveReading>,
)
