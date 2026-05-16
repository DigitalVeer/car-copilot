"""Deterministic classification: OBDSnapshot → list[Classification].

This file owns structure. Gemma owns voice. The two are deliberately kept apart
so that a Gemma failure can never produce a wrong severity or route — only a
less-friendly synthesis.
"""

from __future__ import annotations

from .dtc_table import DTC_TABLE, DTCEntry
from .overrides import OVERRIDES, OverrideRule
from .schema import Classification, IssueMeta, OBDSnapshot

_SEVERITY_ORDER = {"severe": 0, "warning": 1, "healthy": 2}


def classify(snapshot: OBDSnapshot) -> list[Classification]:
    """Return Classifications sorted severe-first. Index 0 is the primary issue.

    Algorithm:
      1. Run live-data overrides. A *severe* override becomes the primary
         classification and absorbs every raw DTC into its deferred_dtcs.
         Non-severe overrides register as their own classifications.
      2. If no severe override fired, build one classification per DTC
         (unknown codes still surface — they classify as warning/expert).
      3. Sort by severity.
      4. If nothing fired, return a single healthy classification.
    """
    classifications: list[Classification] = []
    severe_override_fired = False

    for rule in OVERRIDES:
        if not rule.condition(snapshot.live_data):
            continue
        if rule.severity == "severe" and not severe_override_fired:
            contributing, deferred = _split_dtcs_by_category(snapshot.raw_dtcs, rule.category)
            classifications.append(
                _classification_from_override(
                    rule,
                    contributing_dtcs=contributing,
                    deferred_dtcs=deferred,
                )
            )
            severe_override_fired = True
        else:
            classifications.append(_classification_from_override(rule))

    if not severe_override_fired:
        for code in snapshot.raw_dtcs:
            entry = DTC_TABLE.get(code)
            if entry is not None:
                classifications.append(_classification_from_dtc(entry))
            else:
                classifications.append(_unknown_dtc_classification(code))

    classifications.sort(key=lambda c: _SEVERITY_ORDER[c.severity])

    if not classifications:
        classifications.append(_healthy_classification())

    return classifications


def _classification_from_dtc(entry: DTCEntry) -> Classification:
    return Classification(
        severity=entry.severity,
        route=entry.route,
        category=entry.category,
        title=entry.title_template,
        subtitle=entry.subtitle_template,
        meta=IssueMeta(
            cost_usd_min=entry.cost_usd_min,
            cost_usd_max=entry.cost_usd_max,
            time_minutes=entry.time_minutes,
            difficulty=entry.difficulty,
            drivability=entry.drivability,
        ),
        primary_dtc=entry.code,
        contributing_dtcs=[],
        deferred_dtcs=[],
        fallback_synthesis=entry.fallback_synthesis,
    )


def _classification_from_override(
    rule: OverrideRule,
    contributing_dtcs: list[str] | None = None,
    deferred_dtcs: list[str] | None = None,
) -> Classification:
    return Classification(
        severity=rule.severity,
        route=rule.route,
        category=rule.category,
        title=rule.title,
        subtitle=rule.subtitle,
        meta=rule.meta,
        primary_dtc=None,
        contributing_dtcs=contributing_dtcs or [],
        deferred_dtcs=deferred_dtcs or [],
        fallback_synthesis=rule.fallback_synthesis,
    )


def _split_dtcs_by_category(
    codes: list[str], override_category: str
) -> tuple[list[str], list[str]]:
    """Split DTCs into (contributing, deferred) relative to a fired override.

    A DTC contributes to the override when its DTC_TABLE category matches —
    those codes are evidence for the same problem the live-data rule caught.
    Anything else (or unknown codes) is deferred until the primary issue clears.
    """
    contributing: list[str] = []
    deferred: list[str] = []
    for code in codes:
        entry = DTC_TABLE.get(code)
        if entry is not None and entry.category == override_category:
            contributing.append(code)
        else:
            deferred.append(code)
    return contributing, deferred


def _unknown_dtc_classification(code: str) -> Classification:
    return Classification(
        severity="warning",
        route="expert",
        category="unknown",
        title=f"Unknown trouble code: {code}",
        subtitle="The car logged a code we don't have a play for. A shop can read it.",
        meta=IssueMeta(),
        primary_dtc=code,
        contributing_dtcs=[],
        deferred_dtcs=[],
        fallback_synthesis=(
            f"The car logged trouble code {code}. "
            "We don't have a specific recommendation for this one. "
            "A mechanic with a scanner can tell you what it means."
        ),
    )


def _healthy_classification() -> Classification:
    return Classification(
        severity="healthy",
        route="none",
        category="healthy",
        title="Everything checks out",
        subtitle="No trouble codes. Live readings look normal.",
        meta=IssueMeta(),
        primary_dtc=None,
        contributing_dtcs=[],
        deferred_dtcs=[],
        fallback_synthesis=(
            "No problems showed up on this drive. "
            "All your readings are in the normal range. "
            "Keep an eye on the dashboard like always."
        ),
    )
