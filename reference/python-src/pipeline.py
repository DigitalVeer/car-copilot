"""Snapshot → list[Issue]. Orchestrates classifier, Gemma adapter, and fallback.

The classifier owns the deterministic fields (severity, route, title, cost,
time). Gemma owns synthesis, walkthrough, and mechanic draft. When Gemma
fails — connection refused, timeout, sanity check fails — we substitute
canned text from ``fallback.py`` and mark ``Issue.using_fallback_text = True``
so the UI can show a subtle dev indicator without changing layout.

Concurrency: the three Gemma calls for a single Issue run via
``asyncio.gather`` so a snapshot with multiple Issues doesn't pay 3x latency
per issue. ``return_exceptions=True`` keeps one failing call from cancelling
its siblings.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime

from . import fallback
from .classifier import classify
from .dtc_table import DTC_TABLE
from .gemma_adapter import GemmaAdapter, GemmaFailure
from .schema import (
    DTC,
    Classification,
    Issue,
    IssueMeta,
    Language,
    LiveReading,
    LiveStatus,
    OBDSnapshot,
    VehicleInfo,
)

logger = logging.getLogger(__name__)


async def build_issues(
    snapshot: OBDSnapshot,
    gemma: GemmaAdapter,
    language: Language = "en",
) -> list[Issue]:
    """Build a list of fully-populated Issues, severe-first.

    Each Classification yields one Issue. The Issue's deterministic fields
    come from the Classification; the generative fields come from Gemma, with
    a graceful fall back to canned text on any error.
    """
    classifications = classify(snapshot)
    issues: list[Issue] = []
    for cls in classifications:
        issue = _issue_skeleton(cls, snapshot, language)
        await _fill_generative(issue, cls.fallback_synthesis, gemma, language)
        issues.append(issue)
    return issues


# ---------------------------------------------------------------------------
# Skeleton construction (deterministic; never touches Gemma).
# ---------------------------------------------------------------------------


def _issue_skeleton(
    cls: Classification,
    snapshot: OBDSnapshot,
    language: Language,
) -> Issue:
    issue_id = _issue_id(snapshot.captured_at, cls)
    return Issue(
        id=issue_id,
        detected_at=snapshot.captured_at,
        vehicle=snapshot.vehicle,
        severity=cls.severity,
        route=cls.route,
        category=cls.category,
        title=cls.title,
        subtitle=cls.subtitle,
        meta=cls.meta,
        dtcs=_build_dtcs(cls, snapshot),
        live_readings=_build_readings(snapshot.live_data),
        language=language,
    )


def _issue_id(captured_at: datetime, cls: Classification) -> str:
    stamp = captured_at.strftime("%Y%m%dT%H%M%SZ")
    suffix = cls.primary_dtc or cls.category
    return f"{stamp}-{suffix}"


def _build_dtcs(cls: Classification, snapshot: OBDSnapshot) -> list[DTC]:
    """Build DTC objects for an Issue.

    Primary first, then contributing codes (also tied to this Issue), then
    deferred codes (flagged so the UI can show them under "handle once
    primary clears"). Pending DTCs are not yet exposed on the Issue —
    Mode 07 has its own UI surface in a later phase.
    """
    seen: set[str] = set()
    out: list[DTC] = []

    def add(code: str, deferred: bool) -> None:
        if code in seen:
            return
        seen.add(code)
        entry = DTC_TABLE.get(code)
        description = entry.description if entry is not None else f"Trouble code {code}"
        out.append(DTC(code=code, description=description, deferred=deferred))

    if cls.primary_dtc:
        add(cls.primary_dtc, deferred=False)
    for code in cls.contributing_dtcs:
        add(code, deferred=False)
    for code in cls.deferred_dtcs:
        add(code, deferred=True)
    return out


# ---------------------------------------------------------------------------
# Live-reading rendering — turns raw PID dict into UI-ready LiveReadings.
# ---------------------------------------------------------------------------


def _coolant_status(v: float) -> tuple[LiveStatus, str | None]:
    if v > 105:
        return "severe", "overheating"
    if v > 100:
        return "warning", "hot"
    if v < 60:
        return "warning", "cold"
    return "normal", None


def _battery_status(v: float) -> tuple[LiveStatus, str | None]:
    if v < 11.5:
        return "severe", "low"
    if v < 12.2:
        return "warning", "weak"
    return "normal", None


def _rpm_status(v: float) -> tuple[LiveStatus, str | None]:
    if v < 600:
        return "warning", "stalling"
    if v < 800:
        return "warning", "rough"
    return "normal", None


def _o2_status(v: float) -> tuple[LiveStatus, str | None]:
    if v > 0.85:
        return "warning", "rich"
    if v < 0.1:
        return "warning", "lean"
    return "normal", None


def _fuel_trim_status(v: float) -> tuple[LiveStatus, str | None]:
    if abs(v) >= 10:
        return "warning", "compensating"
    return "normal", None


def _oil_pressure_status(v: float) -> tuple[LiveStatus, str | None]:
    if v < 5:
        return "severe", "critical"
    if v < 15:
        return "warning", "low"
    return "normal", None


# (display_key, unit, status_fn)
_READING_SPEC: dict[str, tuple[str, str | None, object]] = {
    "rpm": ("RPM (idle)", "rpm", _rpm_status),
    "coolant_temp_c": ("Coolant temperature", "°C", _coolant_status),
    "battery_v": ("Battery voltage", "V", _battery_status),
    "o2_bank1_v": ("O₂ sensor (bank 1)", "V", _o2_status),
    "fuel_trim_short_pct": ("Short-term fuel trim", "%", _fuel_trim_status),
    "fuel_trim_long_pct": ("Long-term fuel trim", "%", _fuel_trim_status),
    "oil_pressure_psi": ("Oil pressure", "psi", _oil_pressure_status),
    "intake_temp_c": ("Intake temperature", "°C", None),
    "maf_gps": ("Mass airflow", "g/s", None),
    "vehicle_speed_kph": ("Vehicle speed", "kph", None),
    "throttle_pos_pct": ("Throttle position", "%", None),
    "fan_duty_pct": ("Cooling fan duty", "%", None),
    "ambient_temp_c": ("Ambient temperature", "°C", None),
}


def _build_readings(live_data: dict[str, float | str]) -> list[LiveReading]:
    readings: list[LiveReading] = []
    for key, raw in live_data.items():
        spec = _READING_SPEC.get(key)
        if spec is None:
            readings.append(LiveReading(key=key, value=str(raw)))
            continue
        display_key, unit, status_fn = spec
        status: LiveStatus = "normal"
        note: str | None = None
        if status_fn is not None and isinstance(raw, (int, float)):
            status, note = status_fn(float(raw))  # type: ignore[misc]
        readings.append(
            LiveReading(
                key=display_key,
                value=_format_value(raw),
                unit=unit,
                status=status,
                note=note,
            )
        )
    return readings


def _format_value(raw: float | str) -> str:
    if isinstance(raw, float):
        if raw.is_integer():
            return str(int(raw))
        return f"{raw:.2f}".rstrip("0").rstrip(".")
    return str(raw)


# ---------------------------------------------------------------------------
# Generative fields — Gemma calls with fallback.
# ---------------------------------------------------------------------------


async def _fill_generative(
    issue: Issue,
    fallback_synthesis: str,
    gemma: GemmaAdapter,
    language: Language,
) -> None:
    """Fill synthesis, walkthrough, and mechanic_draft on the Issue.

    Each Gemma call is independent — we gather them, catch failures
    individually, and substitute canned text where needed. ``walkthrough``
    runs for DIY and safety routes (per design doc §11.3: severe Issue pages
    render the steps inline). ``mechanic_draft`` runs for everything except
    safety, where the focus is the procedure and a mechanic CTA would be
    misleading.
    """
    want_walkthrough = issue.route in ("diy", "safety")
    want_mechanic = issue.route in ("diy", "expert")

    tasks: list[asyncio.Future] = []
    labels: list[str] = []

    tasks.append(asyncio.ensure_future(gemma.generate_synthesis(issue, language)))
    labels.append("synthesis")

    if want_walkthrough:
        tasks.append(asyncio.ensure_future(gemma.generate_walkthrough(issue, language)))
        labels.append("walkthrough")

    if want_mechanic:
        tasks.append(asyncio.ensure_future(gemma.generate_mechanic_draft(issue, language)))
        labels.append("mechanic_draft")

    results = await asyncio.gather(*tasks, return_exceptions=True)

    for label, result in zip(labels, results, strict=True):
        if isinstance(result, Exception):
            if not isinstance(result, GemmaFailure):
                logger.warning("%s raised unexpected %s: %s", label, type(result).__name__, result)
            _apply_fallback(issue, label, fallback_synthesis)
            issue.using_fallback_text = True
        else:
            _apply_success(issue, label, result)


def _apply_success(issue: Issue, label: str, result: object) -> None:
    if label == "synthesis":
        from .schema import SynthesisResult

        assert isinstance(result, SynthesisResult)
        issue.synthesis = result.synthesis
        issue.good_news = result.good_news
    elif label == "walkthrough":
        from .schema import WalkthroughStep

        assert isinstance(result, list) and all(isinstance(s, WalkthroughStep) for s in result)
        issue.walkthrough_steps = result
    elif label == "mechanic_draft":
        assert isinstance(result, str)
        issue.mechanic_draft = result


def _apply_fallback(issue: Issue, label: str, fallback_synthesis: str) -> None:
    if label == "synthesis":
        issue.synthesis = fallback.synthesis_for(issue, fallback_synthesis)
    elif label == "walkthrough":
        issue.walkthrough_steps = fallback.walkthrough_for(issue)
    elif label == "mechanic_draft":
        issue.mechanic_draft = fallback.mechanic_draft_for(issue)


__all__ = ["build_issues"]


# ---------------------------------------------------------------------------
# Vehicle helper, kept for downstream code that wants a default.
# ---------------------------------------------------------------------------


DEMO_VEHICLE = VehicleInfo(
    year=2009,
    make="Toyota",
    model="Corolla",
    mileage=187000,
    display_name="2009 Corolla",
)


def _ensure_meta(meta: IssueMeta | None) -> IssueMeta:
    return meta or IssueMeta()
