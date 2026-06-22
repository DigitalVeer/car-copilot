"""Deterministic DTC classification table — the server-side source of truth.

This is the cloud port of the principle locked in CLAUDE.md: *Gemma never
classifies; DTCTable is the source of truth for severity, route, cost, time.*
The frontier model only generates narrative text on top of these fields.

This file mirrors the three deep entries in
`app/src/main/java/com/example/carcopilot/data/DTCTable.kt` (P0301, P0087,
P0171). In production this would be backed by a database so the catalog updates
without an app release; here it's a literal dict to keep the thin slice
dependency-free and obviously correct.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Classification:
    """Deterministic verdict. The model never produces these fields."""

    title: str
    subtitle: str
    severity: str          # healthy | warning | severe | info
    route: str             # diy | expert | safety | info
    cost_usd_min: int
    cost_usd_max: int
    time_minutes: int
    drivability: str
    # Plain-language supporting signals: the evidence the synthesis reasons from.
    supporting_signals: list[str]


# Keyed by DTC code. Mirrors the deep DTCTable.DEFAULT entries.
_TABLE: dict[str, Classification] = {
    "P0301": Classification(
        title="Cylinder 1 is misfiring",
        subtitle="Likely a worn ignition coil or spark plug on cylinder 1",
        severity="warning",
        route="diy",
        cost_usd_min=40,
        cost_usd_max=120,
        time_minutes=30,
        drivability="Drivable, but don't ignore it — a steady misfire can damage the catalytic converter.",
        supporting_signals=[
            "Misfire flagged on cylinder 1 specifically (P0301).",
            "Short fuel trim running rich (+14%), consistent with one cylinder not burning fuel cleanly.",
            "Idle is rough at ~740 rpm — the stumble you'd feel at a stoplight.",
        ],
    ),
    "P0087": Classification(
        title="Fuel pressure is running too low",
        subtitle="The fuel rail isn't holding the pressure the engine needs",
        severity="severe",
        route="expert",
        cost_usd_min=300,
        cost_usd_max=900,
        time_minutes=180,
        drivability="Drive gently and get it looked at soon — low rail pressure can leave you stranded.",
        supporting_signals=[
            "Fuel rail pressure below the commanded target (P0087).",
            "Common on high-mileage diesels — usually a tired high-pressure pump or a clogged filter.",
            "Power loss under load is the symptom to expect.",
        ],
    ),
    "P0171": Classification(
        title="The engine is running lean on bank 1",
        subtitle="Too much air or too little fuel — often a vacuum leak or dirty MAF sensor",
        severity="warning",
        route="diy",
        cost_usd_min=20,
        cost_usd_max=200,
        time_minutes=60,
        drivability="Usually drivable. Worth fixing soon so it doesn't turn into a misfire.",
        supporting_signals=[
            "System too lean on bank 1 (P0171).",
            "Long-term fuel trim climbing as the engine adds fuel to compensate.",
            "Cheap to chase if it's a vacuum leak; a little more if it's the MAF sensor.",
        ],
    ),
}

# Used when a code isn't in the deep table. In production the thin 256-entry
# catalog (assets/dtc_codes.json) would back this; here we degrade gracefully.
_UNKNOWN = Classification(
    title="A fault code is set",
    subtitle="We can read the code, but this one isn't in our deep catalog yet",
    severity="info",
    route="info",
    cost_usd_min=0,
    cost_usd_max=0,
    time_minutes=0,
    drivability="Get it scanned by a shop to be sure.",
    supporting_signals=["A diagnostic trouble code is present but not yet curated."],
)


def classify(primary_code: str | None) -> Classification:
    """Return the deterministic classification for the primary DTC code."""
    if primary_code is None:
        return _UNKNOWN
    return _TABLE.get(primary_code.upper(), _UNKNOWN)
