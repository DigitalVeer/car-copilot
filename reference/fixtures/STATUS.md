# STATUS — `reference/fixtures/`

**MIXED.**

- **`misfire.json`** — **AUTHORITATIVE**, mirrored verbatim into `app/src/main/assets/misfire.json` and loaded at runtime by `FixtureOBDDataSource`. The active fixture. If you edit this, edit the mirror too (or the diff will surprise someone).
- **`healthy.json`** — **HISTORICAL**. Healthy-state scenario from the original Python design. The Android app does not load this fixture today; healthy rendering isn't a wired screen variant. Kept as a reference for the shape a healthy snapshot should take.
- **`overheat.json`** — **HISTORICAL**. Severe overheat scenario from the original Python design. The Android app does not load this fixture today; severe-route rendering isn't wired beyond the `Severity.severe` enum value. Kept as a reference for the shape a severe snapshot should take, and as the seed for a future overheat DTCTable entry.

When live-data severity overrides or additional scenarios land on Android (see FUTURE_WORK), `healthy.json` and `overheat.json` are the natural starting points — but they need an `OBDDataSource` impl that can select between fixtures, which doesn't exist yet.
