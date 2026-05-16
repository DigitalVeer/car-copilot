"""Severity overrides driven by live data (not DTCs).

A live-data condition like "coolant temperature > 105°C" takes priority over any
trouble code — the car is overheating right now, regardless of what's stored in
memory. These rules run before the DTC lookup in the classifier.
"""

from __future__ import annotations

from collections.abc import Callable

from pydantic import BaseModel, ConfigDict

from .schema import IssueMeta, Route, Severity


class OverrideRule(BaseModel):
    """A live-data rule that produces a Classification when its condition fires."""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    name: str
    condition: Callable[[dict[str, float | str]], bool]
    severity: Severity
    route: Route
    category: str
    title: str
    subtitle: str
    meta: IssueMeta
    fallback_synthesis: str


def _coolant_above(threshold: float) -> Callable[[dict[str, float | str]], bool]:
    def check(d: dict[str, float | str]) -> bool:
        val = d.get("coolant_temp_c")
        return isinstance(val, (int, float)) and val > threshold

    return check


def _oil_pressure_below(threshold: float) -> Callable[[dict[str, float | str]], bool]:
    def check(d: dict[str, float | str]) -> bool:
        val = d.get("oil_pressure_psi")
        return isinstance(val, (int, float)) and val < threshold

    return check


def _battery_below(threshold: float) -> Callable[[dict[str, float | str]], bool]:
    def check(d: dict[str, float | str]) -> bool:
        val = d.get("battery_v")
        return isinstance(val, (int, float)) and val < threshold

    return check


OVERRIDES: list[OverrideRule] = [
    OverrideRule(
        name="engine_overheat",
        condition=_coolant_above(105),
        severity="severe",
        route="safety",
        category="overheat",
        title="Engine running too hot",
        subtitle="Coolant above safe range. Stop driving in the next few miles.",
        meta=IssueMeta(drivability="stop within 5 miles"),
        fallback_synthesis=(
            "The engine is running hotter than it should. "
            "Pull over within the next few miles and shut it off. "
            "Driving on a hot engine can warp the head, and that's a big bill."
        ),
    ),
    OverrideRule(
        name="critical_oil_pressure",
        condition=_oil_pressure_below(5),
        severity="severe",
        route="safety",
        category="oil_pressure",
        title="Critical oil pressure loss",
        subtitle="Engine could seize. Stop now.",
        meta=IssueMeta(drivability="stop immediately"),
        fallback_synthesis=(
            "Oil pressure is dangerously low. "
            "Shut the engine off as soon as it's safe to pull over. "
            "Every minute you keep driving raises the chance of seizing it."
        ),
    ),
    OverrideRule(
        name="low_battery_crank",
        condition=_battery_below(11.5),
        severity="warning",
        route="expert",
        category="battery",
        title="Battery weak at crank",
        subtitle="May not start next time. Get it tested.",
        meta=IssueMeta(
            cost_usd_min=120,
            cost_usd_max=180,
            drivability="may not restart",
        ),
        fallback_synthesis=(
            "The battery is reading low when you start the car. "
            "It may not start next time you turn the key. "
            "Most parts stores will test it for free."
        ),
    ),
]
