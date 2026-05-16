"""Canned text for when Gemma is unreachable, slow, or produces junk.

Every Gemma method has a fallback here. The voice rules in ``prompts/system.md``
apply just as much to these strings — when Gemma fails, the user is still
talking to CAR·COPILOT.

Synthesis text is owned by the DTC table (or override rule); we only re-glue
it onto the Issue. Walkthroughs and mechanic drafts are templated per category
since the DTC table doesn't carry that shape of content.
"""

from __future__ import annotations

from .schema import Issue, WalkthroughStep


def synthesis_for(issue: Issue, fallback_synthesis: str | None = None) -> str:
    """Return canned synthesis text for an Issue.

    The classifier hands us a ``fallback_synthesis`` string from the DTC table
    or override rule. If the caller didn't carry it through (test, or a code
    path we haven't wired yet), fall back to the subtitle.
    """
    if fallback_synthesis:
        return fallback_synthesis.strip()
    return issue.subtitle.strip()


# ---------------------------------------------------------------------------
# Walkthrough templates — keyed by Issue.category.
# ---------------------------------------------------------------------------


_MISFIRE_WALKTHROUGH: list[WalkthroughStep] = [
    WalkthroughStep(
        number=1,
        title="Park and let it cool",
        body=(
            "Pull into the driveway and shut the engine off. "
            "Wait fifteen minutes so the coils on top of the engine aren't hot to the touch."
        ),
        diagram_hint="engine_bay_overview",
    ),
    WalkthroughStep(
        number=2,
        title="Find cylinder 1's coil",
        body=(
            "Pop the hood. Cylinder 1 is the coil closest to the timing-belt side of the engine. "
            "It's a small black block with a wire clipped into the side and a single 10mm bolt holding it down."
        ),
        diagram_hint="engine_bay_coil1",
    ),
    WalkthroughStep(
        number=3,
        title="Swap in the new coil",
        body=(
            "Unclip the wire by squeezing the tab. Unbolt the coil with a 10mm socket. "
            "Pull straight up — it should come out with the spark plug boot. "
            "Slide the new coil into the same hole until it seats, bolt it back down snug, reclip the wire."
        ),
        diagram_hint="engine_bay_coil1",
    ),
    WalkthroughStep(
        number=4,
        title="Start it up",
        body=(
            "Close the hood and start the engine. "
            "It may run rough for ten seconds while the computer relearns. "
            "The misfire light usually clears on its own within a drive or two."
        ),
        diagram_hint=None,
    ),
]


_OVERHEAT_WALKTHROUGH: list[WalkthroughStep] = [
    WalkthroughStep(
        number=1,
        title="Pull over now",
        body=(
            "Get to the right shoulder or a parking lot in the next mile or two. "
            "Keep moving until you can stop safely — idling in traffic makes it worse, not better."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=2,
        title="Shut the engine off",
        body=(
            "Once stopped, turn the engine off but leave the key in accessory if you can. "
            "That keeps the gauge live so you can watch the temperature fall."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=3,
        title="Wait thirty minutes",
        body=(
            "Don't open the hood right away — steam under the cap is hot enough to burn. "
            "Walk around, drink water, give it half an hour. "
            "When you can rest a hand on the hood, it's safe to look."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=4,
        title="Check the coolant overflow",
        body=(
            "The overflow tank is a translucent plastic bottle near the front of the engine bay. "
            "If it's below the MIN line or empty, that's likely your problem. "
            "Top it up with water in a pinch, but get coolant on the way home."
        ),
        diagram_hint="coolant_overflow",
    ),
    WalkthroughStep(
        number=5,
        title="Call before driving",
        body=(
            "Even if it cools off and seems fine, call a mechanic before driving more than a few miles. "
            "Overheating can warp the head, and that's the kind of damage that doesn't show up until the next start."
        ),
        diagram_hint=None,
    ),
]


_GAS_CAP_WALKTHROUGH: list[WalkthroughStep] = [
    WalkthroughStep(
        number=1,
        title="Open the fuel door",
        body=(
            "Pull the release inside the cabin or push the door open if your car has the push-to-open kind. "
            "Twist the gas cap counter-clockwise to remove it."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=2,
        title="Check the seal",
        body=(
            "Look at the rubber gasket on the cap. "
            "If it's cracked or torn, the cap is done — pick up a new one at any parts store for under $20."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=3,
        title="Tighten until it clicks",
        body=(
            "Put the cap back on and twist clockwise. "
            "Keep going past where it seems tight — you should hear or feel two or three clicks. "
            "That's the cap telling you it's sealed."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=4,
        title="Drive a day or two",
        body=(
            "The check-engine light won't go out the instant you tighten it. "
            "Drive normally for a day or two and it should clear on its own."
        ),
        diagram_hint=None,
    ),
]


_GENERIC_DIY_WALKTHROUGH: list[WalkthroughStep] = [
    WalkthroughStep(
        number=1,
        title="Look up the part",
        body=(
            "Search the OBD code with your car's year and model. "
            "Forums for older cars often show photos of the exact part on the engine."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=2,
        title="Compare prices",
        body=(
            "Check the parts store website and a couple of online sellers. "
            "Aftermarket is usually fine for a car this age — name brand isn't worth double the price."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=3,
        title="Set aside the time",
        body=(
            "Plan for the time estimate plus thirty minutes the first time you do it. "
            "Watch one video start-to-finish before you pick up a wrench."
        ),
        diagram_hint=None,
    ),
]


_GENERIC_SAFETY_WALKTHROUGH: list[WalkthroughStep] = [
    WalkthroughStep(
        number=1,
        title="Pull over safely",
        body=(
            "Take the next exit or pull onto the shoulder when it's safe. "
            "Hazard lights on. Don't keep driving and hope it clears."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=2,
        title="Shut the engine off",
        body=(
            "Turn the key off once you're parked. "
            "Whatever's wrong will keep getting worse with the engine running."
        ),
        diagram_hint=None,
    ),
    WalkthroughStep(
        number=3,
        title="Call for help",
        body=(
            "Call someone who can come get you or arrange a tow. "
            "Don't try to drive home — the savings on a tow are nothing compared to a destroyed engine."
        ),
        diagram_hint=None,
    ),
]


_WALKTHROUGH_BY_CATEGORY: dict[str, list[WalkthroughStep]] = {
    "misfire": _MISFIRE_WALKTHROUGH,
    "overheat": _OVERHEAT_WALKTHROUGH,
    "evap": _GAS_CAP_WALKTHROUGH,
    "oil_pressure": _GENERIC_SAFETY_WALKTHROUGH,
    "brake": _GENERIC_SAFETY_WALKTHROUGH,
}


def walkthrough_for(issue: Issue) -> list[WalkthroughStep]:
    """Return canned walkthrough steps for an Issue's category."""
    steps = _WALKTHROUGH_BY_CATEGORY.get(issue.category)
    if steps is not None:
        return [s.model_copy() for s in steps]
    if issue.route == "safety":
        return [s.model_copy() for s in _GENERIC_SAFETY_WALKTHROUGH]
    return [s.model_copy() for s in _GENERIC_DIY_WALKTHROUGH]


# ---------------------------------------------------------------------------
# Mechanic draft templates.
# ---------------------------------------------------------------------------


def mechanic_draft_for(issue: Issue) -> str:
    """Return a canned text-to-mechanic draft for an Issue."""
    symptom_line: str
    if issue.dtcs:
        primary = issue.dtcs[0]
        symptom_line = (
            f"It's showing a {primary.code} — {primary.description.lower()}."
        )
    else:
        symptom_line = f"It's showing what looks like {issue.title.lower()}."

    diagnosis_line = f"Best guess is {issue.title.lower()}, but I'd like you to confirm."

    if issue.meta.cost_usd_min is not None and issue.meta.cost_usd_max is not None:
        cost_line = (
            f"I've seen ${issue.meta.cost_usd_min}–${issue.meta.cost_usd_max} quoted online "
            "but I'd value your take."
        )
    else:
        cost_line = "Could you give me a rough estimate before I bring it in?"

    return (
        f"Hi — I've got a {issue.vehicle.display_name} with about "
        f"{issue.vehicle.mileage or 'unknown'} miles. "
        f"{symptom_line} "
        f"{diagnosis_line} "
        "Money's a bit tight so I'm trying to plan ahead. "
        f"{cost_line} "
        "Happy to drop it off at my current location whenever works for you."
    ).strip()
