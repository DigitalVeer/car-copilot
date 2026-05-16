"""Classifier tests — phase 1 acceptance gate.

These tests must be 100% deterministic. No Gemma, no I/O beyond reading fixture
JSON. Every fixture in tests/fixtures/obd_snapshots/ has at least one test here.

Coverage:
- The three demo fixtures (misfire, overheat, healthy).
- The CTA decision table from design doc §11.1: every classification produced
  must map to one of the four valid (severity, route) UI variants.
- Edge cases: unknown DTC, low-battery override, severe override absorbing
  every raw DTC into deferred_dtcs.
"""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path

import pytest

from carcopilot.classifier import classify
from carcopilot.schema import Classification, OBDSnapshot, VehicleInfo

FIXTURES = Path(__file__).parent / "fixtures" / "obd_snapshots"

DEMO_VEHICLE = VehicleInfo(
    year=2009,
    make="Toyota",
    model="Corolla",
    mileage=187000,
    display_name="2009 Corolla",
)


# CTA decision table from design doc §11.1. The primary classification's
# (severity, route) must be a key in this map.
CTA_FOR = {
    ("healthy", "none"): None,
    ("warning", "diy"): "Show me what's going on →",
    ("warning", "expert"): "Show me what's going on →",
    ("severe", "safety"): "Stop engine · walk me through it →",
}


def home_cta(c: Classification) -> str | None:
    key = (c.severity, c.route)
    if key not in CTA_FOR:
        raise AssertionError(
            f"Classification ({c.severity!r}, {c.route!r}) is not a valid CTA row. "
            f"Valid rows: {sorted(CTA_FOR.keys())}"
        )
    return CTA_FOR[key]


def _snapshot(
    raw_dtcs: list[str] | None = None,
    live_data: dict[str, float | str] | None = None,
) -> OBDSnapshot:
    return OBDSnapshot(
        captured_at=datetime(2026, 5, 14, tzinfo=UTC),
        vehicle=DEMO_VEHICLE,
        raw_dtcs=raw_dtcs or [],
        live_data=live_data or {},
    )


# ---------------------------------------------------------------------------
# Fixture-driven tests (the Phase 1 acceptance criteria)
# ---------------------------------------------------------------------------


def test_misfire_fixture_classifies_as_diy_misfire():
    snapshot = OBDSnapshot.from_file(FIXTURES / "misfire.json")
    classifications = classify(snapshot)

    assert len(classifications) == 1
    c = classifications[0]
    assert c.severity == "warning"
    assert c.route == "diy"
    assert c.category == "misfire"
    assert c.primary_dtc == "P0301"
    assert "cylinder 1" in c.title.lower()
    assert c.meta.cost_usd_min == 40
    assert c.meta.cost_usd_max == 60
    assert c.meta.time_minutes == 30
    assert c.meta.drivability == "safe for short trips"
    assert home_cta(c) == "Show me what's going on →"


def test_overheat_fixture_promotes_override_above_misfire():
    snapshot = OBDSnapshot.from_file(FIXTURES / "overheat.json")
    classifications = classify(snapshot)

    primary = classifications[0]
    assert primary.severity == "severe"
    assert primary.route == "safety"
    assert primary.category == "overheat"
    # P0217 is in the "overheat" category — same as the override — so it
    # contributes. P0301 is "misfire" — a different problem deferred until
    # the overheat clears.
    assert primary.contributing_dtcs == ["P0217"]
    assert primary.deferred_dtcs == ["P0301"]
    assert home_cta(primary) == "Stop engine · walk me through it →"
    # No DTC classifications got through (severe override fired).
    assert all(c.severity == "severe" for c in classifications)


def test_healthy_fixture_returns_single_healthy_classification():
    snapshot = OBDSnapshot.from_file(FIXTURES / "healthy.json")
    classifications = classify(snapshot)

    assert len(classifications) == 1
    c = classifications[0]
    assert c.severity == "healthy"
    assert c.category == "healthy"
    # Healthy has no CTA, but its (severity, route) must still be a known row.
    assert home_cta(c) is None


# ---------------------------------------------------------------------------
# CTA decision table — every classify() result must map to a known row.
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "fixture_name",
    ["misfire.json", "overheat.json", "healthy.json"],
)
def test_every_classification_has_a_valid_cta_row(fixture_name: str):
    snapshot = OBDSnapshot.from_file(FIXTURES / fixture_name)
    for c in classify(snapshot):
        # raises AssertionError if (severity, route) isn't in CTA_FOR
        home_cta(c)


# ---------------------------------------------------------------------------
# Unit tests covering the algorithm steps directly (no fixture I/O)
# ---------------------------------------------------------------------------


def test_p0301_classifies_as_diy_misfire():
    [c] = classify(_snapshot(raw_dtcs=["P0301"], live_data={"rpm": 740, "coolant_temp_c": 88}))
    assert c.severity == "warning"
    assert c.route == "diy"
    assert c.category == "misfire"


def test_overheat_override_takes_priority_over_misfire():
    classifications = classify(
        _snapshot(raw_dtcs=["P0301", "P0217"], live_data={"coolant_temp_c": 110})
    )
    assert classifications[0].severity == "severe"
    assert classifications[0].category == "overheat"
    assert "P0301" in classifications[0].deferred_dtcs


def test_empty_snapshot_returns_healthy():
    [c] = classify(_snapshot(raw_dtcs=[], live_data={"coolant_temp_c": 88, "rpm": 720}))
    assert c.severity == "healthy"


def test_unknown_dtc_falls_through_as_warning_expert():
    [c] = classify(_snapshot(raw_dtcs=["P9999"]))
    assert c.severity == "warning"
    assert c.route == "expert"
    assert c.category == "unknown"
    assert c.primary_dtc == "P9999"
    assert home_cta(c) == "Show me what's going on →"


def test_low_battery_override_fires_as_warning_expert():
    classifications = classify(_snapshot(live_data={"battery_v": 11.0}))
    # No DTCs, only a warning override → exactly one classification.
    assert len(classifications) == 1
    c = classifications[0]
    assert c.severity == "warning"
    assert c.route == "expert"
    assert c.category == "battery"
    assert home_cta(c) == "Show me what's going on →"


def test_severe_override_absorbs_dtcs_even_when_battery_also_low():
    classifications = classify(
        _snapshot(
            raw_dtcs=["P0301"],
            live_data={"coolant_temp_c": 110, "battery_v": 11.0},
        )
    )
    # Severe overheat is primary; battery warning still surfaces separately.
    assert classifications[0].severity == "severe"
    assert classifications[0].category == "overheat"
    assert "P0301" in classifications[0].deferred_dtcs

    categories = [c.category for c in classifications]
    assert "battery" in categories
    # Severe must come before warning in the sort order.
    assert categories.index("overheat") < categories.index("battery")


def test_classifications_are_sorted_severe_first():
    snapshot = _snapshot(
        raw_dtcs=["P0457", "P0301"],  # both warning/diy
        live_data={"coolant_temp_c": 110},  # severe overheat override
    )
    classifications = classify(snapshot)
    severities = [c.severity for c in classifications]
    # severe before any warning/healthy
    assert severities == sorted(severities, key={"severe": 0, "warning": 1, "healthy": 2}.get)


# ---------------------------------------------------------------------------
# Schema guarantees we depend on.
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "fixture_name", ["misfire.json", "overheat.json", "healthy.json"]
)
def test_fixture_round_trips_through_pydantic(fixture_name: str):
    """If a fixture stops parsing, the rest of the suite is meaningless."""
    snapshot = OBDSnapshot.from_file(FIXTURES / fixture_name)
    assert isinstance(snapshot, OBDSnapshot)
    assert snapshot.vehicle.display_name == "2009 Corolla"
