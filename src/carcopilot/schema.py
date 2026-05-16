"""Core schema for CAR·COPILOT.

The `Issue` type is the central UI contract. The classifier produces the
deterministic fields; the Gemma adapter fills the generative ones. Every
screen in the mockup is a render of an `Issue` (or a list of them).
"""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Severity = Literal["healthy", "warning", "severe"]
Route = Literal["diy", "expert", "safety", "none"]
Difficulty = Literal["easy", "moderate", "hard"]
LiveStatus = Literal["normal", "warning", "severe"]
ResolutionState = Literal["open", "in_progress", "resolved", "ignored"]
ResolutionMethod = Literal["diy", "shop", "self_corrected"]
Language = Literal["en", "es"]


class VehicleInfo(BaseModel):
    year: int
    make: str
    model: str
    mileage: int | None = None
    vin: str | None = None
    display_name: str


class DTC(BaseModel):
    code: str
    description: str
    first_seen_drives_ago: int | None = None
    confirmed: bool = True
    deferred: bool = False


class LiveReading(BaseModel):
    key: str
    value: str
    unit: str | None = None
    status: LiveStatus = "normal"
    note: str | None = None


class WalkthroughStep(BaseModel):
    number: int
    title: str
    body: str
    diagram_hint: str | None = None


class IssueMeta(BaseModel):
    cost_usd_min: int | None = None
    cost_usd_max: int | None = None
    time_minutes: int | None = None
    difficulty: Difficulty | None = None
    drivability: str | None = None


class Issue(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    detected_at: datetime
    vehicle: VehicleInfo

    severity: Severity
    route: Route
    category: str

    title: str
    subtitle: str
    meta: IssueMeta = Field(default_factory=IssueMeta)

    dtcs: list[DTC] = Field(default_factory=list)
    live_readings: list[LiveReading] = Field(default_factory=list)
    related_issue_ids: list[str] = Field(default_factory=list)

    synthesis: str | None = None
    good_news: str | None = None
    walkthrough_steps: list[WalkthroughStep] = Field(default_factory=list)
    mechanic_draft: str | None = None

    using_fallback_text: bool = False

    language: Language = "en"


class OBDSnapshot(BaseModel):
    """A point-in-time read of the OBD bus."""

    captured_at: datetime
    vehicle: VehicleInfo
    raw_dtcs: list[str] = Field(default_factory=list)
    pending_dtcs: list[str] = Field(default_factory=list)
    live_data: dict[str, float | str] = Field(default_factory=dict)

    @classmethod
    def from_file(cls, path: str | Path) -> OBDSnapshot:
        data = json.loads(Path(path).read_text())
        return cls.model_validate(data)


class Classification(BaseModel):
    """Output of `classify()`. Pure deterministic data — never touched by Gemma."""

    severity: Severity
    route: Route
    category: str
    title: str
    subtitle: str
    meta: IssueMeta = Field(default_factory=IssueMeta)
    primary_dtc: str | None = None
    contributing_dtcs: list[str] = Field(default_factory=list)
    deferred_dtcs: list[str] = Field(default_factory=list)
    fallback_synthesis: str = ""


class HistoryEntry(BaseModel):
    id: str
    issue: Issue
    state: ResolutionState = "open"
    resolved_at: datetime | None = None
    resolution_method: ResolutionMethod | None = None
    resolution_notes: str | None = None
    actual_cost_usd: int | None = None


class HistoryPattern(BaseModel):
    pattern_type: Literal["recurrence", "trend", "correlation"]
    related_entry_ids: list[str] = Field(default_factory=list)
    synthesis: str
    suggested_root_cause: str | None = None


class SynthesisResult(BaseModel):
    synthesis: str
    good_news: str | None = None
