# STATUS — `reference/fixtures/`

**MIXED.**

- **`misfire.json`** — **AUTHORITATIVE**, mirrored verbatim into `app/src/main/assets/misfire.json` and loaded at runtime by `FixtureOBDDataSource` when its `ACTIVE_FIXTURE` const points to `"misfire.json"`. The original demo scenario. Edit both copies or the diff will surprise someone.
- **`hilux_fuel_rail.json`** — (not in this directory; lives only in `app/src/main/assets/hilux_fuel_rail.json`.) 2012 Hilux diesel, P0087 low fuel-rail pressure. Selected at runtime by flipping `FixtureOBDDataSource.ACTIVE_FIXTURE` to `"hilux_fuel_rail.json"`. Added in W2 work for Will's RAG/RulesEngine validation path. Consider mirroring back into `reference/fixtures/` for shape-reference if a third scenario lands.
- **`healthy.json`** — **HISTORICAL**. Healthy-state scenario from the original Python design. The Android app does not load this fixture today; healthy rendering isn't a wired screen variant. Kept as a reference for the shape a healthy snapshot should take.
- **`overheat.json`** — **HISTORICAL**. Severe overheat scenario from the original Python design. The Android app does not load this fixture today; severe-route rendering isn't wired beyond the `Severity.severe` enum value. Kept as a reference for the shape a severe snapshot should take, and as the seed for a future overheat DTCTable entry.

When live-data severity overrides or additional scenarios land on Android (see FUTURE_WORK), `healthy.json` and `overheat.json` are the natural starting points — but they need an `OBDDataSource` impl that can select between fixtures, which doesn't exist yet.
