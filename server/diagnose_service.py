"""CAR·COPILOT cloud `/diagnose` service — thin-slice proof of concept.

Takes an OBDSnapshot (the same JSON shape the on-device `OBDDataSource`
produces — see `app/src/main/assets/misfire.json`), runs the deterministic
server-side classifier (`dtc_table.py`), fills the *existing* synthesis prompt
(`app/src/main/assets/issue_synthesis.md`), and streams the friend-voice
synthesis back from Claude as Server-Sent Events.

Design notes that tie back to STRATEGY.md:
  * The deterministic classifier stays the source of truth — the model only
    generates narrative on top. (CLAUDE.md: "Gemma never classifies.")
  * Prompts are loaded from the app assets dir verbatim — the voice in
    `reference/prompts/` is the asset, not something to rewrite.
  * Prompt caching is set on the system prompt: the first call in a session
    pays the prefill, the rest read it at 0.1x. This is the cloud answer to the
    "prefill dominates wall-clock" finding in FUTURE_WORK.md.
  * Streaming emits raw model token deltas (the JSON envelope), so the Android
    side can reuse the existing `SynthesisState.extractSynthesisInProgress`
    extractor unchanged.

Run:
    pip install -r server/requirements.txt
    export ANTHROPIC_API_KEY=sk-ant-...
    uvicorn server.diagnose_service:app --reload --port 8000

Then POST a snapshot:
    curl -N -X POST localhost:8000/diagnose \
      -H 'content-type: application/json' \
      --data @app/src/main/assets/misfire.json
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Iterator

import anthropic
from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse

from .dtc_table import Classification, classify

# --- configuration -----------------------------------------------------------

# Sonnet 4.6 is the workhorse for the high-volume narrative surfaces (see
# STRATEGY.md → Frontier model recommendation). Override to claude-opus-4-8 for
# the agentic / low-confidence tier.
MODEL = os.environ.get("CARCOPILOT_MODEL", "claude-sonnet-4-6")

# Prompts are the source of truth at runtime per CLAUDE.md — load them from the
# app assets dir rather than duplicating. Resolve relative to this file so the
# service runs from any cwd.
_ASSETS = Path(
    os.environ.get(
        "CARCOPILOT_ASSETS_DIR",
        Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets",
    )
)

_SYSTEM_PROMPT = (_ASSETS / "system.md").read_text(encoding="utf-8")
_SYNTHESIS_TEMPLATE = (_ASSETS / "issue_synthesis.md").read_text(encoding="utf-8")

client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from the environment
app = FastAPI(title="CAR·COPILOT diagnose service")


# --- prompt assembly ---------------------------------------------------------


def _format_live_readings(live: dict[str, Any]) -> str:
    if not live:
        return "(none reported)"
    # Mirror the human-readable rows the on-device PromptBuilder produces.
    labels = {
        "rpm": "Engine RPM",
        "coolant_temp_c": "Coolant temp (C)",
        "o2_bank1_v": "O2 sensor bank 1 (V)",
        "fuel_trim_short_pct": "Short fuel trim (%)",
        "fuel_trim_long_pct": "Long fuel trim (%)",
        "battery_v": "Battery (V)",
        "vehicle_speed_kph": "Speed (kph)",
        "fuel_rail_pressure_kpa": "Fuel rail pressure (kPa)",
    }
    return "\n".join(f"- {labels.get(k, k)}: {v}" for k, v in live.items())


def _build_user_prompt(snapshot: dict[str, Any], cls: Classification) -> str:
    vehicle = snapshot.get("vehicle", {})
    dtcs = snapshot.get("raw_dtcs", [])
    live = snapshot.get("live_data", {})

    return (
        _SYNTHESIS_TEMPLATE
        .replace("{vehicle}", vehicle.get("display_name", "Unknown vehicle"))
        .replace("{mileage}", str(vehicle.get("mileage", "unknown")))
        .replace("{engine_family}", snapshot.get("engine_family", "gasoline"))
        .replace("{severity}", cls.severity)
        .replace("{route}", cls.route)
        .replace("{title}", cls.title)
        .replace("{subtitle}", cls.subtitle)
        .replace("{cost_min}", str(cls.cost_usd_min))
        .replace("{cost_max}", str(cls.cost_usd_max))
        .replace("{time_minutes}", str(cls.time_minutes))
        .replace("{drivability}", cls.drivability)
        .replace("{dtcs}", ", ".join(dtcs) if dtcs else "(none)")
        .replace("{live_readings}", _format_live_readings(live))
        .replace("{supporting_signals}", "\n".join(f"- {s}" for s in cls.supporting_signals))
        # RAG context retrieval is future work (STRATEGY.md → vector retrieval);
        # the template field is filled with a placeholder for now.
        .replace("{rag_context}", "(no regional context retrieved)")
        .replace("{language}", snapshot.get("language", "English"))
    )


# --- streaming ---------------------------------------------------------------


def _sse(data: str) -> str:
    """Encode one SSE `data:` frame. Newlines are escaped so multi-line token
    deltas don't break the frame; the client unescapes them."""
    return f"data: {json.dumps(data)}\n\n"


def _stream_synthesis(snapshot: dict[str, Any]) -> Iterator[str]:
    primary = (snapshot.get("raw_dtcs") or [None])[0]
    cls = classify(primary)
    user_prompt = _build_user_prompt(snapshot, cls)

    # cache_control on the system prompt: the synthesis system prompt is stable
    # across every diagnosis, so the prefill is paid once and read at 0.1x
    # thereafter. This is the lever STRATEGY.md flags against the prefill cost.
    with client.messages.stream(
        model=MODEL,
        max_tokens=400,
        system=[
            {
                "type": "text",
                "text": _SYSTEM_PROMPT,
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[{"role": "user", "content": user_prompt}],
    ) as stream:
        for text in stream.text_stream:
            yield _sse(text)
    yield "event: done\ndata: {}\n\n"


@app.post("/diagnose")
async def diagnose(request: Request) -> StreamingResponse:
    """Accept an OBDSnapshot, stream the friend-voice synthesis as SSE.

    The response body is a stream of `data: "<token delta>"` frames carrying the
    raw model JSON envelope, plus a terminal `event: done`. The client appends
    deltas and runs the existing JSON-aware extractor over the buffer.
    """
    snapshot = await request.json()
    return StreamingResponse(
        _stream_synthesis(snapshot),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "model": MODEL}
