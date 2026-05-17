# STATUS — `reference/python-src/`

**HISTORICAL.** This directory holds the Python reference implementation that predates the Android port. None of it runs as part of the Android build; it is kept for archaeology and as the structural model the Kotlin code mirrors.

| Python file | Android counterpart |
|---|---|
| `schema.py` | `app/src/main/java/com/example/carcopilot/model/Schema.kt` |
| `dtc_table.py` | `app/src/main/java/com/example/carcopilot/data/DTCTable.kt` |
| `classifier.py` | `app/src/main/java/com/example/carcopilot/data/IssueBuilder.kt` |
| `overrides.py` | not ported — live-data severity overrides are not yet a feature on Android |
| `obd_source.py` | `app/src/main/java/com/example/carcopilot/data/OBDDataSource.kt` (+ `FixtureOBDDataSource.kt`) |
| `pipeline.py` | the Compose `LaunchedEffect` collect blocks in `IssueScreen` / `MechanicDraftScreen` / `HistoryScreen` |
| `gemma_adapter.py` | `app/src/main/java/com/example/carcopilot/inference/GemmaService.kt` (+ `PromptBuilder.kt`) |
| `fallback.py` | `app/src/main/java/com/example/carcopilot/model/Fallbacks.kt` (+ `DTCTable` `walkthroughSteps` / `mechanicDraft`) |

When the Android code's behavior is unclear and you want to see what the original shape looked like, this is where to look. Do not modify these files expecting the change to affect the Android app.
