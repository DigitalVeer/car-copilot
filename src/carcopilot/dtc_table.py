"""DTC lookup table — the deterministic source of truth for severity/route/cost/time.

Gemma never sees this. The classifier reads it; the fallback writer reads it.
Adding a new DTC means appending an entry here — no other code change required.

Title templates may include ``{cylinder}`` for cylinder-specific misfires.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from .schema import Difficulty, Route, Severity


class DTCEntry(BaseModel):
    code: str
    category: str
    title_template: str
    subtitle_template: str
    description: str
    severity: Severity
    route: Route
    cost_usd_min: int | None = None
    cost_usd_max: int | None = None
    time_minutes: int | None = None
    difficulty: Difficulty | None = None
    drivability: str | None = None
    fallback_synthesis: str = Field(
        ..., description="Canned synthesis when Gemma fails. Must follow voice rules."
    )


_MISFIRE_SUBTITLE = (
    "A small black block on top of the engine. No lift needed. Just a 10mm socket."
)


def _misfire_entry(code: str, cylinder: int) -> DTCEntry:
    return DTCEntry(
        code=code,
        category="misfire",
        title_template=f"Replace ignition coil — cylinder {cylinder}",
        subtitle_template=_MISFIRE_SUBTITLE,
        description=f"Cylinder {cylinder} misfire detected",
        severity="warning",
        route="diy",
        cost_usd_min=40,
        cost_usd_max=60,
        time_minutes=30,
        difficulty="easy",
        drivability="safe for short trips",
        fallback_synthesis=(
            f"Cylinder {cylinder} keeps misfiring. "
            "On older Corollas, this is almost always a worn ignition coil. "
            "About $45 and 30 minutes to fix."
        ),
    )


DTC_TABLE: dict[str, DTCEntry] = {
    "P0300": DTCEntry(
        code="P0300",
        category="misfire",
        title_template="Multiple cylinders misfiring",
        subtitle_template=(
            "More than one cylinder isn't firing right. "
            "Start with new coils and plugs."
        ),
        description="Random or multiple cylinder misfire detected",
        severity="warning",
        route="diy",
        cost_usd_min=45,
        cost_usd_max=90,
        time_minutes=30,
        difficulty="easy",
        drivability="safe for short trips",
        fallback_synthesis=(
            "More than one cylinder is misfiring. "
            "Replacing all four coils and plugs usually clears it. "
            "About $80 in parts and half an hour of work."
        ),
    ),
    "P0301": _misfire_entry("P0301", 1),
    "P0302": _misfire_entry("P0302", 2),
    "P0303": _misfire_entry("P0303", 3),
    "P0304": _misfire_entry("P0304", 4),
    "P0420": DTCEntry(
        code="P0420",
        category="catalytic",
        title_template="Catalytic converter wearing out",
        subtitle_template="The car still runs fine. It will fail an emissions test.",
        description="Catalyst system efficiency below threshold (bank 1)",
        severity="warning",
        route="expert",
        cost_usd_min=400,
        cost_usd_max=1200,
        difficulty="hard",
        drivability="safe, fails emissions",
        fallback_synthesis=(
            "The catalytic converter is past its prime. "
            "The car drives fine, but it will fail an emissions test. "
            "A shop can quote the swap — usually $400 to $1,200."
        ),
    ),
    "P0442": DTCEntry(
        code="P0442",
        category="evap",
        title_template="Small fuel-system leak",
        subtitle_template="Often just a loose gas cap. Tighten it and drive a day or two.",
        description="EVAP system small leak detected",
        severity="warning",
        route="diy",
        cost_usd_min=0,
        cost_usd_max=30,
        time_minutes=2,
        difficulty="easy",
        drivability="safe",
        fallback_synthesis=(
            "There's a small leak in the fuel vapor system. "
            "Nine times out of ten it's a loose gas cap. "
            "Tighten it firmly and the light should clear in a day or two."
        ),
    ),
    "P0457": DTCEntry(
        code="P0457",
        category="evap",
        title_template="Gas cap loose or missing",
        subtitle_template="Tighten the cap until it clicks. Light clears in a day or two.",
        description="EVAP system leak detected (fuel cap)",
        severity="warning",
        route="diy",
        cost_usd_min=0,
        cost_usd_max=0,
        time_minutes=1,
        difficulty="easy",
        drivability="safe",
        fallback_synthesis=(
            "The gas cap isn't sealing. "
            "Twist it until it clicks a couple of times. "
            "The light will clear on its own after a drive or two."
        ),
    ),
    "P0128": DTCEntry(
        code="P0128",
        category="thermostat",
        title_template="Thermostat stuck open",
        subtitle_template="The engine is taking too long to warm up.",
        description="Coolant temperature below thermostat regulating temperature",
        severity="warning",
        route="expert",
        cost_usd_min=150,
        cost_usd_max=300,
        difficulty="moderate",
        drivability="safe",
        fallback_synthesis=(
            "The thermostat is stuck open, so the engine never gets fully warm. "
            "It still drives fine, but the heater will feel weak and fuel mileage drops. "
            "A shop can swap the thermostat for $150 to $300."
        ),
    ),
    "P0171": DTCEntry(
        code="P0171",
        category="fuel_air",
        title_template="Engine running lean",
        subtitle_template="Too much air, not enough fuel. Could be a leak in an intake hose.",
        description="System too lean (bank 1)",
        severity="warning",
        route="expert",
        difficulty="moderate",
        drivability="safe",
        fallback_synthesis=(
            "The engine is getting too much air for the fuel it's burning. "
            "Most often that's a cracked intake hose or a tired oxygen sensor. "
            "A shop can pin it down in under an hour."
        ),
    ),
    "P0217": DTCEntry(
        code="P0217",
        category="overheat",
        title_template="Engine running too hot",
        subtitle_template="Coolant above safe range. Stop driving in the next few miles.",
        description="Engine over-temperature condition",
        severity="severe",
        route="safety",
        drivability="stop within 5 miles",
        fallback_synthesis=(
            "The engine is running hotter than it should. "
            "Pull over within the next few miles and shut it off. "
            "Driving on a hot engine can warp the head, and that's a big bill."
        ),
    ),
    "P0480": DTCEntry(
        code="P0480",
        category="cooling_fan",
        title_template="Cooling fan circuit problem",
        subtitle_template="The fan may not kick on in traffic. Watch your temperature gauge.",
        description="Cooling fan 1 control circuit malfunction",
        severity="warning",
        route="expert",
        cost_usd_min=150,
        cost_usd_max=400,
        difficulty="moderate",
        drivability="safe in cool weather",
        fallback_synthesis=(
            "The cooling fan isn't getting the signal it should. "
            "On the highway you're fine, but sitting in traffic the engine could overheat. "
            "A shop can test the relay and motor in about an hour."
        ),
    ),
    "P0700": DTCEntry(
        code="P0700",
        category="transmission",
        title_template="Transmission needs a closer look",
        subtitle_template=(
            "The transmission computer flagged a problem. Drive gently until checked."
        ),
        description="Transmission control system malfunction",
        severity="warning",
        route="expert",
        difficulty="hard",
        drivability="drive gently",
        fallback_synthesis=(
            "The transmission's computer set a code. "
            "It can mean anything from a worn sensor to something more serious. "
            "Drive gently and get it scanned by a transmission shop."
        ),
    ),
    "C0035": DTCEntry(
        code="C0035",
        category="brake",
        title_template="Wheel-speed sensor failure",
        subtitle_template="Antilock brakes and traction control are off. Stop driving.",
        description="Left front wheel speed sensor circuit malfunction",
        severity="severe",
        route="safety",
        drivability="stop immediately",
        fallback_synthesis=(
            "A wheel-speed sensor stopped reporting. "
            "Your antilock brakes and traction control are offline until it's fixed. "
            "Pull over somewhere safe and call for a tow or a ride."
        ),
    ),
    "B1000": DTCEntry(
        code="B1000",
        category="airbag",
        title_template="Airbag system fault",
        subtitle_template="A bag may not fire in a crash. Get it checked soon.",
        description="ECU malfunction (airbag system)",
        severity="warning",
        route="expert",
        difficulty="hard",
        drivability="safe",
        fallback_synthesis=(
            "The airbag computer set a fault. "
            "The car drives fine, but a bag may not deploy if you crash. "
            "A dealer or specialist can read the exact fault and quote a fix."
        ),
    ),
}
