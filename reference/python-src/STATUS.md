# STATUS — `reference/python-src/`

**HISTORICAL.** This directory holds the Python reference implementation that predates the Android port. None of it runs as part of the Android build; it is kept for archaeology and as the structural model the Kotlin code mirrors.

| Python file | Android counterpart |
|---|---|
| `schema.py` | `model/Schema.kt` |
| `dtc_table.py` | `data/DTCTable.kt` (deep) + `assets/dtc_codes.json` via `ThinDtcLoader` (thin, 256 codes) |
| `classifier.py` | `data/IssueBuilder.kt` (table-driven) + `data/RulesEngine.kt` (deterministic supporting signals) |
| `overrides.py` | not ported — live-data severity overrides not yet a feature on Android |
| `obd_source.py` | `data/OBDDataSource.kt` + impls: `FixtureOBDDataSource.kt`, `TcpOBDDataSource.kt`, `BluetoothOBDDataSource.kt` (shared `Elm327Protocol.kt`) |
| `pipeline.py` | the Compose `LaunchedEffect` collect blocks in `IssueScreen` / `WalkthroughScreen` / `MechanicDraftScreen` / `HistoryScreen` |
| `gemma_adapter.py` | `inference/GemmaService.kt` (streaming + multiplexing) + `inference/PromptBuilder.kt` (template fill + RAG injection) |
| `fallback.py` | `model/Fallbacks.kt` (per-DTC map + `synthesizeFromClassification`) + `DTCTable` per-entry `walkthroughSteps` / `mechanicDraft` / `procedureSpecs` |
| (none) | `data/RagStore.kt` — new Android-side feature; not in the Python reference |

When the Android code's behavior is unclear and you want to see what the original shape looked like, this is where to look. Do not modify these files expecting the change to affect the Android app.
