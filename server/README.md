# CAR·COPILOT cloud `/diagnose` service

> STATUS: **thin-slice proof of concept** for the cloud product direction in
> `../STRATEGY.md`. Not part of the shipped on-device app. It deliberately
> crosses the hackathon's "no cloud calls" constraint — see the STATUS banner in
> `STRATEGY.md` before treating any of this as production.

A minimal FastAPI service that demonstrates the cloud architecture end to end:
the same `OBDSnapshot` JSON the on-device adapter produces goes in, deterministic
classification runs server-side, and a friend-voice synthesis streams back from
Claude — reusing the app's own prompts.

## What it shows

- **Deterministic classification stays the source of truth** (`dtc_table.py`),
  mirroring the locked `DTCTable` principle. The model only writes narrative.
- **Prompts are reused verbatim** from `app/src/main/assets/` — the voice is the
  asset.
- **Prompt caching** on the system prompt — the cloud answer to the "prefill
  dominates wall-clock" finding in `FUTURE_WORK.md`.
- **Token-delta streaming** in the same JSON-envelope shape the Android
  `SynthesisState` extractor already consumes, so the client change is small.

## Run

```bash
pip install -r server/requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
# Default model is claude-sonnet-4-6 (the workhorse). For the agentic tier:
#   export CARCOPILOT_MODEL=claude-opus-4-8
uvicorn server.diagnose_service:app --reload --port 8000
```

From the repo root, send the bundled misfire fixture:

```bash
curl -N -X POST localhost:8000/diagnose \
  -H 'content-type: application/json' \
  --data @app/src/main/assets/misfire.json
```

You'll see a stream of `data: "<token>"` SSE frames carrying the synthesis JSON
envelope (`{"synthesis": "...", "good_news": "..."}`), terminated by
`event: done`.

## Request shape

The body is an `OBDSnapshot` in the same snake_case shape as
`app/src/main/assets/misfire.json` — `vehicle`, `raw_dtcs`, `live_data`, etc.
The Android `CloudInferenceService` serializes its `OBDSnapshot` to exactly this.

## What's stubbed (intentionally)

- **Only the synthesis surface** is wired. Mechanic draft, history pattern, and
  walkthrough plan/step follow the same pattern (load the asset prompt, fill,
  stream) and are left as the obvious next step.
- **RAG context** is a placeholder — vector retrieval is the future-work lever in
  `STRATEGY.md`.
- **No auth, no rate limiting, no persistence.** Production needs all three plus
  the per-VIN `HistoryRepository`.
- **No tool use yet** — recall/parts/TSB lookup (and the numeric-fidelity win
  it brings) is the next capability to prove.
