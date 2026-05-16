# CAR·COPILOT — Technical Design Document

**Version:** 0.1 (post-mockup) · **Status:** ready to build · **Scope:** Python core + Gemma adapter + python-OBD. **Out of scope:** Android (separate doc).

This document is the source of truth for the backend. It is written to be consumed by Claude coding agents and by humans. Where you see ambiguity in this doc, prefer the most deterministic and most testable interpretation — that's what we want.

---

## Table of contents

1. [Overview & architecture](#1-overview--architecture)
2. [Core principles](#2-core-principles)
3. [Project structure](#3-project-structure)
4. [Core schema](#4-core-schema)
5. [The classification layer](#5-the-classification-layer)
6. [The Gemma adapter](#6-the-gemma-adapter)
7. [The pipeline](#7-the-pipeline)
8. [python-OBD integration](#8-python-obd-integration)
9. [Mock data & demo scenarios](#9-mock-data--demo-scenarios)
10. [History & pattern detection](#10-history--pattern-detection)
11. [UI contract](#11-ui-contract)
12. [Development environment](#12-development-environment)
13. [Testing strategy](#13-testing-strategy)
14. [Phase plan & build order](#14-phase-plan--build-order)
15. [Appendix A: prompt.md content](#appendix-a-promptmd-content)
16. [Appendix B: DTC table seed](#appendix-b-dtc-table-seed)

---

## 1. Overview & architecture

CAR·COPILOT translates raw OBD-II data into a plain-English explanation, a recommended action, and pre-drafted artifacts the user can act on. Gemma 4 runs locally and generates the warm narrative voice on top of a deterministic classification layer. Everything is on-device; no cloud.

### High-level data flow

```
┌────────────────────┐
│  OBD-II Dongle     │  (real hardware, or mocked for dev)
│  Bluetooth         │
└─────────┬──────────┘
          │ raw PIDs + DTCs
          ▼
┌────────────────────┐
│  obd_source.py     │  python-OBD wrapper OR MockOBDSource
│  → OBDSnapshot     │
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│  classifier.py     │  Deterministic — looks up DTCs in
│                    │  DTC_TABLE, applies live-data
│                    │  SEVERITY_OVERRIDES.
│  → Classification  │
└─────────┬──────────┘
          │ {severity, route, title, cost, time, ...}
          ▼
┌────────────────────┐
│  pipeline.py       │  Builds an Issue skeleton from
│                    │  classification + raw evidence.
│  → Issue (partial) │  Calls Gemma adapter to fill
│                    │  generative fields.
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│  gemma_adapter.py  │  Local Gemma via Ollama HTTP.
│                    │  Returns structured JSON
│                    │  (synthesis, walkthrough, draft).
│                    │  Falls back to canned text on failure.
└─────────┬──────────┘
          │
          ▼
┌────────────────────┐
│  Issue (complete)  │  Serialized to JSON.
│                    │  This is the UI contract.
└─────────┬──────────┘
          │
          ▼
        UI (Android later; HTML mockup now)
```

**Key insight**: the classifier owns structure. Gemma owns voice. If Gemma fails, the UI still renders correctly with fallback text. This is what makes the demo bulletproof.

---

## 2. Core principles

These are load-bearing. Don't undo them without a design discussion.

1. **Deterministic structure, generative voice.** The DTC lookup table is the ground truth for what kind of issue this is, how severe, what it costs, and what kind of help to surface. Gemma writes the human-readable explanation on top.

2. **Local-only.** No cloud calls. Ever. The trust claim ("nothing leaves the phone") depends on this. Even pattern detection runs locally.

3. **Schema-first.** The `Issue` is the central data type. Every screen in the UI is a render of an Issue (or a list of them). Even History entries wrap Issues.

4. **Polymorphic UI by classification.** The same `Issue` JSON renders differently based on `severity` and `route` fields. The backend doesn't know about UI; it just produces correctly-classified Issues.

5. **No location data.** The app never asks for or uses GPS. Any artifact that needs a location uses `"my current location"` as a placeholder string.

6. **Fail soft, never fail loud.** If Gemma misbehaves, fall back to canned text from the DTC table. If a DTC isn't in the table, classify as `unknown` and surface raw codes without synthesis. The app must never crash on the user.

---

## 3. Project structure

```
carcopilot/
├── README.md
├── pyproject.toml            # poetry or hatch
├── docker-compose.yml        # ollama + app
├── Dockerfile
├── .env.example
├── prompts/                  # versioned like code; see Appendix A
│   ├── system.md
│   ├── issue_synthesis.md
│   ├── walkthrough.md
│   ├── mechanic_draft.md
│   └── history_pattern.md
├── src/
│   └── carcopilot/
│       ├── __init__.py
│       ├── schema.py         # all dataclasses + JSON (de)serialization
│       ├── classifier.py     # classify(snapshot) -> Classification
│       ├── dtc_table.py      # the lookup table
│       ├── overrides.py      # live-data severity overrides
│       ├── gemma_adapter.py  # Gemma wrapper with retry + fallback
│       ├── pipeline.py       # OBDSnapshot -> Issue
│       ├── obd_source.py     # Real + Mock OBD sources
│       ├── history.py        # storage + pattern detection
│       ├── fallback.py       # canned text generators
│       └── cli.py            # demo entry point
├── tests/
│   ├── test_classifier.py
│   ├── test_pipeline.py
│   ├── test_gemma_adapter.py
│   ├── test_history.py
│   └── fixtures/
│       └── obd_snapshots/
│           ├── misfire.json
│           ├── overheat.json
│           ├── healthy.json
│           └── gas_cap.json
├── demo/
│   ├── run_misfire.py        # end-to-end: prints Issue JSON
│   ├── run_overheat.py
│   ├── run_healthy.py
│   └── seed_history.py
└── data/                     # gitignored; runtime data
    └── history.jsonl
```

### Dependencies (pyproject.toml)

```toml
[project]
name = "carcopilot"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "obd>=0.7.1",         # python-OBD
    "httpx>=0.27",        # for Ollama HTTP calls
    "pydantic>=2.7",      # schema validation
    "rich>=13.7",         # pretty CLI output for demo
    "python-dotenv>=1.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.23",
    "ruff>=0.4",
]
```

Note on `obd`: this library wraps ELM327 Bluetooth dongles. It will only work with a real dongle paired to the host. For dev, use the `MockOBDSource` (see §8).

---

## 4. Core schema

All schema lives in `src/carcopilot/schema.py`. Use pydantic for serialization — it gives us validation and clean JSON for free.

### 4.1 Issue (the central type)

```python
from datetime import datetime
from typing import Literal
from pydantic import BaseModel, Field

Severity = Literal["healthy", "warning", "severe"]
Route = Literal["diy", "expert", "safety"]
Difficulty = Literal["easy", "moderate", "hard"]
LiveStatus = Literal["normal", "warning", "severe"]

class VehicleInfo(BaseModel):
    year: int
    make: str
    model: str
    mileage: int | None = None
    vin: str | None = None
    display_name: str  # "2009 Corolla"

class DTC(BaseModel):
    code: str                          # "P0301"
    description: str                   # human-readable, from DTC_TABLE
    first_seen_drives_ago: int | None = None
    confirmed: bool = True             # Mode 03 vs Mode 07
    deferred: bool = False             # set when another issue takes priority

class LiveReading(BaseModel):
    key: str                           # "RPM (idle)"
    value: str                         # "740" — keep as string for UI display
    unit: str | None = None            # "rpm"
    status: LiveStatus = "normal"
    note: str | None = None            # "rough"

class WalkthroughStep(BaseModel):
    number: int
    title: str                         # "Find the coils"
    body: str                          # generated by Gemma
    diagram_hint: str | None = None    # UI maps to static asset, e.g. "engine_bay_coil1"

class IssueMeta(BaseModel):
    cost_usd_min: int | None = None
    cost_usd_max: int | None = None
    time_minutes: int | None = None
    difficulty: Difficulty | None = None
    drivability: str | None = None     # "safe for short trips"

class Issue(BaseModel):
    # Identity
    id: str
    detected_at: datetime
    vehicle: VehicleInfo

    # Classification — set by classifier.py, never touched by Gemma
    severity: Severity
    route: Route
    category: str                      # "misfire", "overheat", etc.

    # Deterministic content — set by classifier from DTC_TABLE
    title: str                         # "Replace ignition coil — cylinder 1"
    subtitle: str                      # short factual description
    meta: IssueMeta

    # Evidence — raw from OBD
    dtcs: list[DTC] = []
    live_readings: list[LiveReading] = []
    related_issue_ids: list[str] = []  # for deferral / pairing

    # Generative content — filled by Gemma adapter
    synthesis: str | None = None       # AI strip text, 1-3 sentences
    good_news: str | None = None       # optional second paragraph
    walkthrough_steps: list[WalkthroughStep] = []
    mechanic_draft: str | None = None

    # Resilience — set when Gemma fails or hasn't been called
    using_fallback_text: bool = False

    # Locale
    language: Literal["en", "es"] = "en"
```

### 4.2 Snapshot (input)

```python
class OBDSnapshot(BaseModel):
    """A point-in-time read of the OBD bus."""
    captured_at: datetime
    vehicle: VehicleInfo
    raw_dtcs: list[str]                # ["P0301"]
    pending_dtcs: list[str] = []       # Mode 07
    live_data: dict[str, float | str]  # {"coolant_temp_c": 110, "rpm": 740, ...}

    @classmethod
    def from_file(cls, path: str) -> "OBDSnapshot":
        """For fixtures."""
        ...
```

The keys in `live_data` are a defined vocabulary. See `src/carcopilot/obd_source.py` for the canonical list. Examples: `rpm`, `coolant_temp_c`, `o2_bank1_v`, `fuel_trim_short_pct`, `battery_v`, `oil_pressure_psi`, `fan_duty_pct`, `ambient_temp_c`, `misfire_count_cyl1` through `misfire_count_cyl4`.

### 4.3 Classification (intermediate type)

```python
class Classification(BaseModel):
    """Output of classifier.classify(). Pure deterministic data."""
    severity: Severity
    route: Route
    category: str
    title: str
    subtitle: str
    meta: IssueMeta
    primary_dtc: str | None            # the DTC that drove the classification
    contributing_dtcs: list[str] = []  # other DTCs grouped into this issue
    deferred_dtcs: list[str] = []      # DTCs to handle after this issue
```

### 4.4 History types

```python
ResolutionState = Literal["open", "in_progress", "resolved", "ignored"]
ResolutionMethod = Literal["diy", "shop", "self_corrected"]

class HistoryEntry(BaseModel):
    id: str
    issue: Issue
    state: ResolutionState
    resolved_at: datetime | None = None
    resolution_method: ResolutionMethod | None = None
    resolution_notes: str | None = None
    actual_cost_usd: int | None = None

class HistoryPattern(BaseModel):
    """Output of pattern detection. Surfaces on the History tab."""
    pattern_type: Literal["recurrence", "trend", "correlation"]
    related_entry_ids: list[str]
    synthesis: str                     # Gemma-generated, plain English
    suggested_root_cause: str | None = None
```

---

## 5. The classification layer

This is the most important file in the codebase for demo reliability. It's pure Python — no LLM, no I/O.

### 5.1 The DTC table

`src/carcopilot/dtc_table.py` is a `dict[str, DTCEntry]`. Seed it with 15-20 entries covering the common cases plus all demo scenarios. See Appendix B.

```python
from pydantic import BaseModel

class DTCEntry(BaseModel):
    code: str
    category: str
    title_template: str                # may contain {cylinder} etc.
    subtitle_template: str
    description: str                   # what the code means in plain terms
    severity: Severity
    route: Route
    cost_usd_min: int | None = None
    cost_usd_max: int | None = None
    time_minutes: int | None = None
    difficulty: Difficulty | None = None
    drivability: str | None = None
    fallback_synthesis: str            # canned text for when Gemma fails

DTC_TABLE: dict[str, DTCEntry] = {
    "P0301": DTCEntry(
        code="P0301",
        category="misfire",
        title_template="Replace ignition coil — cylinder {cylinder}",
        subtitle_template="A small black block on top of the engine. No lift needed. Just a 10mm socket.",
        description="Cylinder 1 misfire detected",
        severity="warning",
        route="diy",
        cost_usd_min=40, cost_usd_max=60,
        time_minutes=30,
        difficulty="easy",
        drivability="safe for short trips",
        fallback_synthesis="Cylinder 1 keeps misfiring. On older Corollas, this is almost always a worn ignition coil. About $45 and 30 minutes to fix.",
    ),
    # ... see Appendix B
}
```

### 5.2 Severity overrides from live data

Some severe conditions are detected from live readings, not DTCs. These take precedence.

```python
# src/carcopilot/overrides.py

from dataclasses import dataclass
from typing import Callable

@dataclass
class OverrideRule:
    name: str                          # "engine_overheat"
    condition: Callable[[dict], bool]
    severity: Severity
    route: Route
    category: str
    title: str
    subtitle: str
    meta: IssueMeta
    fallback_synthesis: str

OVERRIDES: list[OverrideRule] = [
    OverrideRule(
        name="engine_overheat",
        condition=lambda d: d.get("coolant_temp_c", 0) > 105,
        severity="severe",
        route="safety",
        category="overheat",
        title="Engine running too hot",
        subtitle="Coolant above safe range. Stop driving in the next few miles.",
        meta=IssueMeta(drivability="stop within 5 miles"),
        fallback_synthesis="Your coolant is at {coolant_temp_c}°C — should be 85-95°C. Stop driving within the next few minutes.",
    ),
    OverrideRule(
        name="critical_oil_pressure",
        condition=lambda d: d.get("oil_pressure_psi", 100) < 5,
        severity="severe",
        route="safety",
        category="oil_pressure",
        title="Critical oil pressure loss",
        subtitle="Engine could seize. Stop now.",
        meta=IssueMeta(drivability="stop immediately"),
        fallback_synthesis="Your oil pressure is dangerously low. Stop the engine as soon as it's safe — driving more could seize it.",
    ),
    OverrideRule(
        name="low_battery_crank",
        condition=lambda d: d.get("battery_v", 14.0) < 11.5,
        severity="warning",
        route="expert",
        category="battery",
        title="Battery weak at crank",
        subtitle="May not start next time. Get it tested.",
        meta=IssueMeta(cost_usd_min=120, cost_usd_max=180, drivability="may not restart"),
        fallback_synthesis="Your battery is reading low when you start the car. It may not start next time. Most parts stores will test it for free.",
    ),
]
```

### 5.3 The classify function

```python
# src/carcopilot/classifier.py

def classify(snapshot: OBDSnapshot) -> list[Classification]:
    """
    Returns a list of Classifications, ORDERED BY SEVERITY (severe first).
    The first item is the primary issue for the home screen.
    
    Algorithm:
    1. Check live-data overrides first. If any severe override matches, that's
       the primary classification. All other DTCs become deferred.
    2. Otherwise, group DTCs by category and classify each group.
    3. Sort by severity (severe > warning > healthy).
    4. If no DTCs and no overrides matched: return [healthy_classification()].
    """
    classifications = []
    
    # Step 1: live-data overrides
    severe_override = _check_severe_overrides(snapshot.live_data)
    if severe_override:
        # All DTCs become deferred under the override
        classifications.append(_classification_from_override(
            severe_override, 
            deferred_dtcs=snapshot.raw_dtcs
        ))
        # Other overrides still register but don't bump priority
        ...
    
    # Step 2: DTC classification
    for code in snapshot.raw_dtcs:
        if code in DTC_TABLE:
            classifications.append(_classification_from_dtc(code, snapshot))
        else:
            classifications.append(_unknown_dtc_classification(code))
    
    # Step 3: sort
    SEV_ORDER = {"severe": 0, "warning": 1, "healthy": 2}
    classifications.sort(key=lambda c: SEV_ORDER[c.severity])
    
    # Step 4: healthy
    if not classifications:
        classifications.append(_healthy_classification(snapshot))
    
    return classifications
```

The classifier never calls Gemma. It is fast, deterministic, and 100% testable.

---

## 6. The Gemma adapter

The adapter is the only place that touches the LLM. Everything outside this file assumes it might fail and handles that gracefully.

### 6.1 Local Gemma via Ollama

For local dev, run Gemma 4 via Ollama. It exposes an HTTP API at `localhost:11434` and supports JSON-mode output, which we'll use to constrain Gemma's responses.

```bash
ollama pull gemma3:4b   # or whatever the actual gemma 4 tag is — verify on setup
ollama serve
```

(For production Android, swap this layer for AICore / Mediapipe LLM Inference. The interface above stays the same.)

### 6.2 Adapter interface

```python
# src/carcopilot/gemma_adapter.py

import httpx
import json
from pathlib import Path
from typing import Literal

class GemmaAdapter:
    def __init__(
        self,
        host: str = "http://localhost:11434",
        model: str = "gemma3:4b",
        prompts_dir: Path = Path("prompts"),
        timeout_s: float = 30.0,
        max_retries: int = 2,
    ):
        ...
    
    async def generate_synthesis(
        self,
        issue: Issue,
        language: Literal["en", "es"] = "en",
    ) -> SynthesisResult:
        """Returns {synthesis, good_news} or raises on permanent failure."""
        ...
    
    async def generate_walkthrough(
        self,
        issue: Issue,
        language: Literal["en", "es"] = "en",
    ) -> list[WalkthroughStep]:
        """Returns 3-6 walkthrough steps for DIY-route issues."""
        ...
    
    async def generate_mechanic_draft(
        self,
        issue: Issue,
        language: Literal["en", "es"] = "en",
    ) -> str:
        """Returns a drafted text message to a mechanic."""
        ...
    
    async def find_history_patterns(
        self,
        recent_issues: list[HistoryEntry],
        language: Literal["en", "es"] = "en",
    ) -> list[HistoryPattern]:
        """Looks at past issues, returns 0-3 patterns."""
        ...
```

### 6.3 Prompt structure

Each method loads two files from `prompts/`: the system prompt (always `system.md`) and the task-specific prompt (e.g. `issue_synthesis.md`). The task prompt is a template with `{...}` placeholders.

Example for `generate_synthesis`:

```python
SYSTEM = (prompts_dir / "system.md").read_text()
TEMPLATE = (prompts_dir / "issue_synthesis.md").read_text()

user_prompt = TEMPLATE.format(
    vehicle=issue.vehicle.display_name,
    mileage=issue.vehicle.mileage or "unknown",
    severity=issue.severity,
    route=issue.route,
    title=issue.title,
    cost_min=issue.meta.cost_usd_min or "",
    cost_max=issue.meta.cost_usd_max or "",
    time_minutes=issue.meta.time_minutes or "",
    dtcs="\n".join(f"- {d.code}: {d.description}" for d in issue.dtcs),
    live_readings="\n".join(f"- {r.key}: {r.value} ({r.status})" for r in issue.live_readings),
    language=language,
)

response = await self._call_ollama(SYSTEM, user_prompt, schema={
    "type": "object",
    "properties": {
        "synthesis": {"type": "string"},
        "good_news": {"type": "string"},
    },
    "required": ["synthesis"],
})
```

### 6.4 JSON-constrained generation

Ollama supports a `format` parameter that constrains output to a JSON schema. **Always use it.** This eliminates the "Gemma returned broken JSON" failure mode at the model level.

```python
async def _call_ollama(self, system: str, user: str, schema: dict) -> dict:
    body = {
        "model": self.model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "format": schema,           # <-- this is the key
        "stream": False,
        "options": {"temperature": 0.3},
    }
    async with httpx.AsyncClient(timeout=self.timeout_s) as client:
        r = await client.post(f"{self.host}/api/chat", json=body)
        r.raise_for_status()
        content = r.json()["message"]["content"]
    return json.loads(content)
```

### 6.5 Retry & fallback

```python
async def generate_synthesis(self, issue, language="en"):
    for attempt in range(self.max_retries + 1):
        try:
            result = await self._call_ollama(...)
            # Validate beyond schema: e.g. non-empty, reasonable length
            if self._looks_sane(result):
                return SynthesisResult(**result)
        except (httpx.HTTPError, json.JSONDecodeError, ValidationError) as e:
            logger.warning(f"Gemma attempt {attempt} failed: {e}")
            continue
    
    # Permanent failure — caller handles fallback
    raise GemmaFailure("Could not generate synthesis after retries")
```

Callers (in `pipeline.py`) catch `GemmaFailure` and pull canned text from `fallback.py`. The `Issue.using_fallback_text` flag is set so we can log + display a subtle dev indicator.

### 6.6 Voice constraints

Voice rules live in `prompts/system.md`. See Appendix A. The key ones:

- One thought per sentence.
- No engineer vocabulary. No "polling," "telemetry," "diagnostic data."
- When you name a problem, say what to do about it.
- The user is Maria, not a developer. Imagine her on the phone with a friend who happens to know cars.

We version `prompts/` like code. Every change to a prompt is a commit with a clear message about what behavior we expected to change.

---

## 7. The pipeline

`src/carcopilot/pipeline.py` orchestrates everything. This is the entry point most code paths will hit.

```python
async def build_issues(
    snapshot: OBDSnapshot,
    gemma: GemmaAdapter,
    language: Literal["en", "es"] = "en",
) -> list[Issue]:
    """
    Takes a snapshot, returns a list of fully-populated Issues
    in priority order (severe first).
    """
    classifications = classify(snapshot)
    issues = []
    
    for cls in classifications:
        # Build the deterministic skeleton
        issue = _issue_from_classification(cls, snapshot, language)
        
        # Try to fill generative fields
        try:
            synth = await gemma.generate_synthesis(issue, language)
            issue.synthesis = synth.synthesis
            issue.good_news = synth.good_news
        except GemmaFailure:
            issue.synthesis = fallback.synthesis_for(issue)
            issue.using_fallback_text = True
        
        # Walkthrough only for DIY-route issues
        if issue.route == "diy":
            try:
                issue.walkthrough_steps = await gemma.generate_walkthrough(issue, language)
            except GemmaFailure:
                issue.walkthrough_steps = fallback.walkthrough_for(issue)
                issue.using_fallback_text = True
        
        # Mechanic draft for everything except safety route
        # (safety route doesn't surface a mechanic CTA — focus is on the procedure)
        if issue.route != "safety":
            try:
                issue.mechanic_draft = await gemma.generate_mechanic_draft(issue, language)
            except GemmaFailure:
                issue.mechanic_draft = fallback.mechanic_draft_for(issue)
                issue.using_fallback_text = True
        
        issues.append(issue)
    
    return issues
```

Two notes:

- **Caching.** Once an `Issue` is fully populated, cache it by `issue.id`. The id is deterministic from `(snapshot_id, primary_dtc)` so the same underlying problem produces the same id across runs. Don't regenerate text the user has already seen.
- **Concurrency.** The three Gemma calls per Issue can run concurrently (`asyncio.gather`). For a single Issue this isn't dramatic, but for a snapshot with 3 issues it cuts wall time by ~2x.

---

## 8. python-OBD integration

### 8.1 The source interface

Both real and mock sources implement the same interface:

```python
# src/carcopilot/obd_source.py

from abc import ABC, abstractmethod

class OBDSource(ABC):
    @abstractmethod
    async def take_snapshot(self) -> OBDSnapshot: ...
    
    @abstractmethod
    async def is_connected(self) -> bool: ...
    
    async def close(self) -> None: ...
```

### 8.2 Real source (python-OBD)

```python
import obd

class RealOBDSource(OBDSource):
    def __init__(self, port: str | None = None):
        # port=None lets python-OBD auto-detect (uses BT or USB)
        self.conn = obd.Async(port)
        self._watch_pids()
    
    def _watch_pids(self):
        # Watch the PIDs we care about
        self.conn.watch(obd.commands.RPM)
        self.conn.watch(obd.commands.COOLANT_TEMP)
        self.conn.watch(obd.commands.O2_B1S1)
        self.conn.watch(obd.commands.SHORT_FUEL_TRIM_1)
        self.conn.watch(obd.commands.CONTROL_MODULE_VOLTAGE)
        # ... see PIDS_OF_INTEREST below
        self.conn.start()
    
    async def take_snapshot(self) -> OBDSnapshot:
        # Read all watched PIDs
        live = {}
        for pid_key, pid_cmd in PIDS_OF_INTEREST.items():
            response = self.conn.query(pid_cmd)
            if response.value is not None:
                live[pid_key] = response.value.magnitude  # strip units
        
        # Read confirmed DTCs (Mode 03)
        dtcs_response = self.conn.query(obd.commands.GET_DTC)
        raw_dtcs = [code for code, _desc in (dtcs_response.value or [])]
        
        # Read pending DTCs (Mode 07)
        pending_response = self.conn.query(obd.commands.GET_CURRENT_DTC)
        pending_dtcs = [code for code, _desc in (pending_response.value or [])]
        
        # Vehicle info (Mode 09)
        vin = self.conn.query(obd.commands.VIN).value
        # ... decode VIN to year/make/model OR use stored vehicle config
        
        return OBDSnapshot(
            captured_at=datetime.now(),
            vehicle=self._vehicle_info(),
            raw_dtcs=raw_dtcs,
            pending_dtcs=pending_dtcs,
            live_data=live,
        )
```

PIDs of interest:

```python
PIDS_OF_INTEREST = {
    "rpm": obd.commands.RPM,
    "coolant_temp_c": obd.commands.COOLANT_TEMP,
    "o2_bank1_v": obd.commands.O2_B1S1,
    "fuel_trim_short_pct": obd.commands.SHORT_FUEL_TRIM_1,
    "fuel_trim_long_pct": obd.commands.LONG_FUEL_TRIM_1,
    "battery_v": obd.commands.CONTROL_MODULE_VOLTAGE,
    "intake_temp_c": obd.commands.INTAKE_TEMP,
    "maf_gps": obd.commands.MAF,
    "vehicle_speed_kph": obd.commands.SPEED,
    "throttle_pos_pct": obd.commands.THROTTLE_POS,
    # Misfire counts not exposed via standard PIDs — vendor-specific.
    # For demo: synthesize from DTC presence.
}
```

### 8.3 Mock source (for dev + demo)

```python
class MockOBDSource(OBDSource):
    def __init__(self, scenario_path: str | Path):
        """Load a fixture file. See demo/run_*.py for usage."""
        self.snapshot = OBDSnapshot.parse_file(scenario_path)
    
    async def take_snapshot(self) -> OBDSnapshot:
        return self.snapshot
    
    async def is_connected(self) -> bool:
        return True
```

Use `MockOBDSource` for all dev and all demo recording. It's deterministic and fast.

---

## 9. Mock data & demo scenarios

The demo car is a 2009 Toyota Corolla, 187k miles. All scenarios use this vehicle.

### 9.1 Scenario files

Fixtures live in `tests/fixtures/obd_snapshots/`. Each is a JSON file that deserializes to `OBDSnapshot`.

**`misfire.json`** — the primary demo scenario:

```json
{
  "captured_at": "2026-05-14T19:42:11Z",
  "vehicle": {
    "year": 2009,
    "make": "Toyota",
    "model": "Corolla",
    "mileage": 187000,
    "display_name": "2009 Corolla"
  },
  "raw_dtcs": ["P0301"],
  "pending_dtcs": [],
  "live_data": {
    "rpm": 740,
    "coolant_temp_c": 88,
    "o2_bank1_v": 0.92,
    "fuel_trim_short_pct": 14,
    "battery_v": 12.4,
    "vehicle_speed_kph": 0
  }
}
```

**`overheat.json`** — severe scenario:

```json
{
  "captured_at": "2026-05-14T19:42:11Z",
  "vehicle": {
    "year": 2009, "make": "Toyota", "model": "Corolla",
    "mileage": 187000, "display_name": "2009 Corolla"
  },
  "raw_dtcs": ["P0217", "P0301"],
  "pending_dtcs": [],
  "live_data": {
    "coolant_temp_c": 110,
    "rpm": 1800,
    "fan_duty_pct": 100,
    "ambient_temp_c": 28,
    "vehicle_speed_kph": 35
  }
}
```

**`healthy.json`** — no DTCs, all readings normal:

```json
{
  "captured_at": "2026-05-14T19:42:11Z",
  "vehicle": {
    "year": 2009, "make": "Toyota", "model": "Corolla",
    "mileage": 187000, "display_name": "2009 Corolla"
  },
  "raw_dtcs": [],
  "pending_dtcs": [],
  "live_data": {
    "rpm": 720,
    "coolant_temp_c": 89,
    "battery_v": 12.4,
    "fuel_trim_short_pct": 1
  }
}
```

### 9.2 Demo runners

```python
# demo/run_misfire.py
import asyncio
from rich import print
from carcopilot.obd_source import MockOBDSource
from carcopilot.gemma_adapter import GemmaAdapter
from carcopilot.pipeline import build_issues

async def main():
    source = MockOBDSource("tests/fixtures/obd_snapshots/misfire.json")
    gemma = GemmaAdapter()
    snapshot = await source.take_snapshot()
    issues = await build_issues(snapshot, gemma)
    for issue in issues:
        print(issue.model_dump_json(indent=2))

asyncio.run(main())
```

Running `python demo/run_misfire.py` should print a complete Issue JSON that matches what the UI's Issue page renders.

---

## 10. History & pattern detection

### 10.1 Storage

Hackathon-grade: append-only JSONL file at `data/history.jsonl`. One `HistoryEntry` per line.

```python
# src/carcopilot/history.py

class HistoryStore:
    def __init__(self, path: Path = Path("data/history.jsonl")):
        self.path = path
        self.path.parent.mkdir(exist_ok=True)
    
    def append(self, entry: HistoryEntry) -> None:
        with self.path.open("a") as f:
            f.write(entry.model_dump_json() + "\n")
    
    def load_all(self) -> list[HistoryEntry]:
        if not self.path.exists():
            return []
        return [HistoryEntry.model_validate_json(line) for line in self.path.read_text().splitlines() if line.strip()]
    
    def update_state(self, entry_id: str, **updates) -> None:
        # Read all, modify matching, rewrite. Fine for demo scale.
        ...
```

Pre-seed for demo: `demo/seed_history.py` writes 5-6 historical entries covering the recurrence story (October coil failure → today's coil failure). The History tab in the mockup is hardcoded to this story.

### 10.2 Pattern detection

Two layers, same as classification:

**Deterministic layer** — detect category recurrence:

```python
def find_recurrences(entries: list[HistoryEntry]) -> list[tuple[str, list[HistoryEntry]]]:
    """Returns (category, entries) for any category appearing 2+ times in last year."""
    by_category = defaultdict(list)
    cutoff = datetime.now() - timedelta(days=365)
    for entry in entries:
        if entry.issue.detected_at > cutoff:
            by_category[entry.issue.category].append(entry)
    return [(cat, items) for cat, items in by_category.items() if len(items) >= 2]
```

**Gemma layer** — explain the recurrence:

```python
async def synthesize_pattern(
    category: str,
    entries: list[HistoryEntry],
    gemma: GemmaAdapter,
) -> HistoryPattern:
    """Gemma sees the past entries and suggests a root cause."""
    return await gemma.find_history_patterns(entries)
```

For the demo, Gemma's job is to look at "two coil failures in seven months" and propose "oil leaking past the valve cover and fouling them." That root-cause hop is the differentiator. Make sure the prompt encourages it (see Appendix A, `history_pattern.md`).

---

## 11. UI contract

The backend produces `Issue` JSON. The UI consumes it. Here's how each field maps to the mockup screens.

### 11.1 Home screen

The home screen displays an *ordered list* of Issues for the current trip. The first issue's synthesis goes into the AI strip; the first issue's card data renders as the primary card; subsequent issues collapse into the "Also" section.

| Mockup element | Issue field |
|---|---|
| AI strip label | derived from `severity` (severe→"Pull over now", warning→"Today's drive", healthy→"Today's drive") |
| AI strip body | `issues[0].synthesis` (+ `good_news` as second paragraph if present) |
| Card title | `issues[0].title` |
| Card subtitle | `issues[0].subtitle` |
| Card meta line | composed from `issues[0].meta` (cost, time, drivability) |
| Card CTA | from `severity` + `route` (see decision table below) |
| "Also" rows | `issues[1:].title` (one line each) |

**CTA decision table** (for the home card — note that the home card CTA is *navigational*, not action-taking; the action verb lives on the Issue page):

| severity | route | Home card CTA |
|---|---|---|
| healthy | — | (no CTA, status card) |
| warning | diy | "Show me what's going on →" |
| warning | expert | "Show me what's going on →" |
| severe | safety | "Stop engine · walk me through it →" |

The severe case is the exception: it has a verb on the home card because the destination (Severe Issue page) renders the safety steps inline — tapping the card *does* walk the user through it, in one step. The DIY/Expert cases go to an Issue page that has its own CTAs, so duplicating "Walk me through the fix" on the home card would be misleading.

### 11.2 Issue page (DIY variant)

| Element | Source |
|---|---|
| AI strip | `issue.synthesis` + `issue.good_news` |
| Card title/sub/meta | `issue.title`, `issue.subtitle`, `issue.meta` |
| Primary CTA | "Walk me through the fix" → navigates to Walkthrough |
| Secondary CTA | "Send this to a mechanic instead" → navigates to Draft |
| Evidence DTCs | `issue.dtcs` |
| Evidence live readings | `issue.live_readings` |

### 11.3 Issue page (Severe variant)

| Element | Source |
|---|---|
| AI strip | `issue.synthesis` + `issue.good_news` (with severe tint) |
| Card title/sub | `issue.title`, `issue.subtitle` (severe tint) |
| Inline numbered steps | `issue.walkthrough_steps` rendered inline (not navigation) |
| Evidence | same as DIY |
| Deferred DTCs | `dtc.deferred == true` shown below primary |

Note: for severe route, the walkthrough steps render *inline on the Issue page* rather than on a separate Walkthrough screen. The backend doesn't care — it produces the same `walkthrough_steps` either way. The UI decides where to render based on `route`.

### 11.4 Pre-flight & Walkthrough

Pre-flight is currently not parameterized by Gemma — it's a deterministic template per category. We can add Gemma-generated pre-flight text later.

Walkthrough renders `issue.walkthrough_steps` one at a time with the step pill, progress dots, and a static SVG diagram (engine bay diagram for misfire scenarios; no diagram for safety scenarios).

### 11.5 Drafted message

Renders `issue.mechanic_draft` in the editable card. The CTA on tapping "Looks good" opens the system Messages app with the drafted text pre-populated.

### 11.6 History

Renders sorted `HistoryEntry` list with `HistoryPattern` synthesis as the AI strip at top.

### 11.7 Localization

The `Issue.language` field determines what language Gemma generated content in. The UI's static labels ("Today's drive", "Walk me through the fix") live in a separate locale dict on the UI side. The backend handles the generative text; the UI handles the chrome.

---

## 12. Development environment

### 12.1 docker-compose

```yaml
# docker-compose.yml
services:
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11434/api/tags"]
      interval: 10s
      timeout: 3s
      retries: 5

  app:
    build: .
    depends_on:
      ollama:
        condition: service_healthy
    environment:
      - OLLAMA_HOST=http://ollama:11434
      - GEMMA_MODEL=gemma3:4b
    volumes:
      - ./:/app
    working_dir: /app
    command: tail -f /dev/null   # keep running; exec demos manually
```

### 12.2 Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
RUN pip install --no-cache-dir -e ".[dev]"

COPY . .

CMD ["python", "-m", "carcopilot.cli"]
```

For real OBD use later, add `bluetooth bluez libbluetooth-dev` and pass the BT device through with `--privileged --net=host`. Skip for now.

### 12.3 First-run

```bash
# 1. Bring up Ollama + app container
docker compose up -d

# 2. Pull the model (one-time, takes a few minutes)
docker compose exec ollama ollama pull gemma3:4b

# 3. Run a demo
docker compose exec app python demo/run_misfire.py
```

Expected output: a pretty-printed Issue JSON with `synthesis`, `walkthrough_steps`, and `mechanic_draft` populated by Gemma.

### 12.4 .env.example

```
OLLAMA_HOST=http://localhost:11434
GEMMA_MODEL=gemma3:4b
GEMMA_TIMEOUT_S=30
GEMMA_MAX_RETRIES=2
LOG_LEVEL=INFO
HISTORY_PATH=data/history.jsonl
```

---

## 13. Testing strategy

### 13.1 Classifier tests — must be 100% deterministic

```python
# tests/test_classifier.py

def test_p0301_classifies_as_diy_misfire():
    snapshot = OBDSnapshot(
        captured_at=datetime(2026, 5, 14),
        vehicle=DEMO_VEHICLE,
        raw_dtcs=["P0301"],
        live_data={"rpm": 740, "coolant_temp_c": 88},
    )
    [classification] = classify(snapshot)
    assert classification.severity == "warning"
    assert classification.route == "diy"
    assert classification.category == "misfire"

def test_overheat_override_takes_priority_over_misfire():
    snapshot = OBDSnapshot(
        captured_at=datetime(2026, 5, 14),
        vehicle=DEMO_VEHICLE,
        raw_dtcs=["P0301", "P0217"],
        live_data={"coolant_temp_c": 110},
    )
    classifications = classify(snapshot)
    assert classifications[0].severity == "severe"
    assert classifications[0].category == "overheat"
    assert "P0301" in classifications[0].deferred_dtcs

def test_empty_snapshot_returns_healthy():
    snapshot = OBDSnapshot(
        captured_at=datetime(2026, 5, 14),
        vehicle=DEMO_VEHICLE,
        raw_dtcs=[],
        live_data={"coolant_temp_c": 88, "rpm": 720},
    )
    [classification] = classify(snapshot)
    assert classification.severity == "healthy"
```

Every demo fixture should have a corresponding classifier test.

### 13.2 Gemma adapter tests

Use a mock that returns canned JSON, and a separate integration test marked `@pytest.mark.integration` that hits a real Ollama. The mock-based tests run in CI; the integration test runs locally.

```python
# tests/test_gemma_adapter.py

@pytest.mark.asyncio
async def test_synthesis_falls_back_on_repeated_failure(monkeypatch):
    adapter = GemmaAdapter(host="http://nonexistent:99999", max_retries=1)
    with pytest.raises(GemmaFailure):
        await adapter.generate_synthesis(demo_issue)
```

### 13.3 Pipeline tests — golden-output style

```python
@pytest.mark.asyncio
async def test_misfire_pipeline_with_mock_gemma():
    gemma = MockGemmaAdapter(synthesis="Cylinder 1 keeps misfiring...")
    source = MockOBDSource("tests/fixtures/obd_snapshots/misfire.json")
    snapshot = await source.take_snapshot()
    issues = await build_issues(snapshot, gemma)
    
    assert len(issues) == 1
    assert issues[0].route == "diy"
    assert issues[0].synthesis == "Cylinder 1 keeps misfiring..."
    assert not issues[0].using_fallback_text
```

---

## 14. Phase plan & build order

You have Thursday–Monday. Tight. Build in this order:

### Phase 1 — skeleton (Thursday)
- [ ] Project structure + pyproject.toml + Dockerfile + docker-compose.yml
- [ ] `schema.py` — all dataclasses
- [ ] `dtc_table.py` — 10-15 entries (must include P0301, P0217, P0420, P0457; see Appendix B)
- [ ] `overrides.py` — overheat + critical oil pressure + low battery
- [ ] `classifier.py` — the classify() function
- [ ] `obd_source.py` — MockOBDSource
- [ ] Fixture files for misfire, overheat, healthy scenarios
- [ ] **Acceptance**: `pytest tests/test_classifier.py` passes for all three fixtures.

### Phase 2 — Gemma online (Friday)
- [ ] `prompts/` directory with system.md + per-task templates
- [ ] `gemma_adapter.py` — Ollama HTTP wrapper with format-constrained JSON
- [ ] `fallback.py` — canned synthesis/walkthrough/draft per DTC category
- [ ] `pipeline.py` — `build_issues()` with try/except → fallback
- [ ] `demo/run_misfire.py`, `run_overheat.py`, `run_healthy.py`
- [ ] **Acceptance**: `docker compose exec app python demo/run_misfire.py` prints a sensible Issue JSON with Gemma-generated synthesis matching the voice constraints in Appendix A. Then re-run with `OLLAMA_HOST=http://nonexistent` set and confirm the same Issue JSON renders with `using_fallback_text=true`.

### Phase 3 — History (Saturday morning)
- [ ] `history.py` — HistoryStore + find_recurrences()
- [ ] `demo/seed_history.py` — writes the canned recurrence story
- [ ] `find_history_patterns()` in Gemma adapter
- [ ] **Acceptance**: Running the seed script, then a pattern-detection demo, prints a `HistoryPattern` whose synthesis names a plausible root cause for repeated coil failures.

### Phase 4 — Demo prep & video (Saturday afternoon → Sunday)
- [ ] Voice/prompt iteration. Rerun all three demos. If Gemma is saying anything that sounds engineery, edit prompts/.
- [ ] Final dry-runs. Record fallback behavior — kill Ollama mid-demo and confirm the app keeps working.
- [ ] Demo video shoot using the HTML mockup as visual stand-in. The backend produces real Issue JSON in parallel for the writeup screenshots; the mockup is what's on camera.

### Phase 5 — Monday
- [ ] Writeup. Submit.

### Explicitly NOT in scope for any phase above
- Android app
- Real BT dongle integration (we use MockOBDSource for the entire hackathon)
- Mode 02 freeze frame
- Mode 04 clear codes
- Location services of any kind
- Voice input/output
- Camera-based engine bay annotation
- Push notification triggers (lockscreen mockup already exists; in-app trigger is roadmap)

---

## Appendix A: prompt.md content

### `prompts/system.md`

```
You are CAR·COPILOT, a friend who explains what's wrong with someone's car.

WHO YOU'RE TALKING TO
The user is not a mechanic. Imagine someone with a tight budget, an older car, and no technical vocabulary — but plenty of common sense. Talk to them the way a friend who happens to know cars would talk to them on the phone.

VOICE
- One thought per sentence.
- Concrete, not abstract. "Two cylinders aren't firing right" — not "ignition system anomaly detected."
- When you name a problem, say what to do about it.
- Numbers help. "About $45 and 30 minutes" beats "low-cost, low-effort repair."
- Never apologize, never hedge unnecessarily, never use phrases like "I think" or "it might be possible that."
- Never use engineer vocabulary: no "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."

WHAT YOU NEVER DO
- Never invent symptoms or readings the user didn't show you.
- Never speculate about cost beyond the range provided.
- Never recommend a specific shop, dealer, or product brand.
- Never reference the user's location or where they should drive to — you don't know where they are.

OUTPUT FORMAT
Always return valid JSON matching the schema provided. Never include explanatory text outside the JSON. Never include markdown formatting inside string values.
```

### `prompts/issue_synthesis.md`

```
Generate a synthesis for the following car issue.

VEHICLE: {vehicle} ({mileage} miles)
CLASSIFICATION: {severity} severity, {route} route
DIAGNOSED ISSUE: {title}
COST RANGE: ${cost_min}-${cost_max}
TIME ESTIMATE: {time_minutes} minutes

OBD CODES:
{dtcs}

LIVE READINGS:
{live_readings}

LANGUAGE: {language}

Generate:
- "synthesis": 1-3 sentences naming the problem concretely. If multiple readings point to the same cause, name the cause, not the readings.
- "good_news": optional. Only include if there is genuinely good news (cheap, fast, DIY-friendly). Single sentence. Otherwise omit.

JSON ONLY. No preamble, no markdown.
```

### `prompts/walkthrough.md`

```
Generate a step-by-step walkthrough for the following DIY repair.

VEHICLE: {vehicle}
REPAIR: {title}
TOOLS AVAILABLE: {tools}
DIFFICULTY: {difficulty}
LANGUAGE: {language}

Generate 3-6 steps. Each step has:
- "number": integer, starting at 1
- "title": 3-5 words, action-oriented ("Find the coils", "Disconnect the wire")
- "body": 2-4 sentences. Concrete, physical, where-to-look-and-what-to-do. Avoid "carefully" and "make sure" — use specifics instead ("the bolt should turn with hand pressure" not "be careful not to overtighten").

JSON: {"steps": [{"number": ..., "title": ..., "body": ...}]}
No preamble, no markdown.
```

### `prompts/mechanic_draft.md`

```
Draft a short text message the user can send to a mechanic.

VEHICLE: {vehicle} ({mileage} miles)
DIAGNOSED ISSUE: {title}
EVIDENCE:
{dtcs}
{live_readings}
LANGUAGE: {language}

The message should:
- Identify the car (year, model, mileage)
- Describe the symptom in plain English first, then the OBD code
- State the suspected cause but invite the mechanic to confirm
- Ask for a rough estimate (no specific shop)
- Mention cost-consciousness without sounding desperate
- Be 4-6 sentences total. SMS-friendly length.

Use "my current location" if a location would be needed; do not invent one.

JSON: {"draft": "..."}
No preamble, no markdown.
```

### `prompts/history_pattern.md`

```
Look at this user's repair history and identify any meaningful pattern.

VEHICLE: {vehicle}

HISTORY (most recent first):
{entries}

LANGUAGE: {language}

A "meaningful pattern" is:
- The same kind of failure happening more than once in a year (recurrence)
- A trend in a sensor reading that suggests something is degrading (trend)
- Two issues that, taken together, suggest a common root cause that wasn't named in either issue alone (correlation)

If you find a pattern, propose a single most-likely root cause. Be specific — name the part, not the system. For repeated coil failures, "oil leaking past the valve cover gasket and fouling the coils" is the kind of specificity we want. Generic answers like "wear and tear" are not useful.

If you don't see a meaningful pattern, return an empty patterns array.

JSON: {"patterns": [{"pattern_type": "...", "synthesis": "...", "suggested_root_cause": "..."}]}
No preamble, no markdown.
```

---

## Appendix B: DTC table seed

Minimum required entries for the demo scenarios to work. Add more after Phase 2 is green.

| Code | Category | Severity | Route | Cost | Time | Drivability |
|---|---|---|---|---|---|---|
| P0300 | misfire | warning | diy | $45-90 | 30m | safe for short trips |
| P0301 | misfire | warning | diy | $40-60 | 30m | safe for short trips |
| P0302 | misfire | warning | diy | $40-60 | 30m | safe for short trips |
| P0303 | misfire | warning | diy | $40-60 | 30m | safe for short trips |
| P0304 | misfire | warning | diy | $40-60 | 30m | safe for short trips |
| P0420 | catalytic | warning | expert | $400-1200 | — | safe, fails emissions |
| P0442 | evap | warning | diy | $0-30 | 2m | safe |
| P0457 | evap | warning | diy | $0 | 1m | safe |
| P0128 | thermostat | warning | expert | $150-300 | — | safe |
| P0171 | fuel/air | warning | expert | varies | — | safe |
| P0217 | overheat | severe | safety | — | — | stop within 5 mi |
| P0480 | cooling fan | warning | expert | $150-400 | — | safe in cool weather |
| P0700 | transmission | warning | expert | varies | — | drive gently |
| C0035 | brake | severe | safety | — | — | stop immediately |
| B1000 | airbag | warning | expert | varies | — | safe |

Add cylinder number to title for misfire codes using the template (P0301 → cylinder 1, P0302 → cylinder 2, etc.).

---

## How to begin (concretely)

If you're a Claude agent picking this up cold, here is the order:

1. Read this whole document.
2. Create the project scaffold per §3.
3. Implement §4 (schema.py). Run `python -c "from carcopilot.schema import Issue"` — should not error.
4. Implement Appendix B (dtc_table.py) and §5 (classifier.py + overrides.py).
5. Create the three fixture files from §9.
6. Write the classifier tests from §13.1. Run them. They must all pass before moving on.
7. Set up docker-compose per §12. Bring up Ollama. Pull the model.
8. Implement §6 (gemma_adapter.py) and Appendix A (prompts/).
9. Implement §7 (pipeline.py) and fallback.py.
10. Run the three demo scripts. Inspect the output. Iterate on prompts until the voice matches §2 + Appendix A.
11. Move to Phase 3.

That's the build order. The mockup at `carcopilot_mockup_v09.html` is the visual target — every Issue field in §4 maps to something in that file. When in doubt about UI behavior, open the mockup.
