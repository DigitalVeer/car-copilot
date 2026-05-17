package com.example.carcopilot.data

/**
 * Provenance tag on every [OBDSnapshot] — where did this data come from.
 *
 * - [FIXTURE]: the bundled misfire.json scenario, produced by
 *   [FixtureOBDDataSource]. The only path today.
 * - [EMULATOR]: a Python TCP ELM327 emulator over WiFi (see
 *   FUTURE_WORK.md). Lets the Bluetooth transport be exercised without
 *   a real dongle+car combo.
 * - [BLUETOOTH]: a real ELM327-class adapter over BLE. The Phase-12
 *   target.
 *
 * Carried on the snapshot rather than queried from the data source so
 * downstream code (UI badges, logging, telemetry) doesn't have to hold
 * a reference to the source itself.
 */
enum class DataSource { FIXTURE, EMULATOR, BLUETOOTH }
