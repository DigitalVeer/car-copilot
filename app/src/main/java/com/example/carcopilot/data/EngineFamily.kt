package com.example.carcopilot.data

/**
 * Combustion family of the connected vehicle. Governs which PIDs are
 * structurally meaningful: a diesel ECU will never publish short-term
 * fuel trim, a petrol ECU will never publish fuel rail pressure. The
 * fixture and the future Bluetooth path both tag every [OBDSnapshot]
 * with this so consumers don't have to guess from the field map.
 *
 * [UNKNOWN] is the safe default for snapshots that arrive before the
 * adapter has identified the vehicle (no VIN decode yet, fixture pre-
 * configuration not run). Treat UNKNOWN as "render the universal
 * fields only, don't reach for family-specific ones."
 *
 * [HYBRID] is carried even though no current fixture or scenario uses
 * it — keeping it in the enum means a hybrid OBD adapter doesn't force
 * a schema change on first contact.
 */
enum class EngineFamily { PETROL, DIESEL, HYBRID, UNKNOWN }
