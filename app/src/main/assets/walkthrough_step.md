Write the body for one step of a car repair walkthrough.

VEHICLE: {vehicle} ({mileage} miles)
REPAIR: {title}
SHORT DESCRIPTION: {subtitle}
LANGUAGE: {language}

STEP {step_number} of {total_steps}: {step_title}
WHAT THIS STEP ACCOMPLISHES: {step_brief}

CURATED REPAIR PROCEDURE — use this as the ground truth. The body you write must paraphrase the relevant phase of this document. Do not invent torque values, gap specs, socket sizes, part numbers, cylinder locations, or safety-critical details that are not in this text. If a number isn't here, omit it rather than guessing.

---
{procedure}
---

Numeric values, tool sizes, torque specs, gap measurements, time durations, pressures, and bolt sizes must be copied EXACTLY from the procedure. Do not paraphrase, round, restate, or modify any number or unit.

- If the procedure says "30 minutes" say "30 minutes" — never "3 minutes" or "300 minutes" or "about 30".
- If the procedure says "10 minutes" say "10 minutes" — never "1010 minutes", "100 minutes", or "1 minute".
- If the procedure says "18 Nm" say "18 Nm" — never "0 Nm" or "15-20 Nm".
- If the procedure says "30 Nm" say "30 Nm" — never "0 Nm", "300 Nm", or "30-35 Nm".
- If the procedure says "13 ft-lb" say "13 ft-lb" — never "13-15" or "15".
- If the procedure says "0.043 inches" say "0.043 inches" — never "0.04", "0.0433", or "around 0.043".
- If the procedure says "34,500 kPa" say "34,500 kPa" — never "34500 kPa" or "345,000 kPa".

Never duplicate digits or extend a number with extra zeros. "10 minutes" must never become "100 minutes" or "1010 minutes" or "10000 minutes". When you write a digit, stop after the digits that appear in the procedure. A number with more than 6 digits in a repair step is always wrong — if you find yourself writing one, the number you meant is the one in the procedure.

FORMAT: write 3 to 5 short bullet lines, each starting with "- ". One physical action per bullet — what to do, what tool, what direction, what to feel for. Stack bullets in the order the user performs them. The reader is doing the repair right now and reading the body on their phone with one hand — short scannable bullets beat a wall-of-text paragraph.

EMPHASIS MARKERS: wrap the 1-3 most important pieces of information in each step with [Y]...[/Y]. Reserve emphasis for things the user must not miss: tool sizes (e.g. [Y]10mm socket[/Y]), torque or gap values when copied from the procedure (e.g. [Y]18 Nm[/Y]), direction or orientation that matters ([Y]straight up[/Y], [Y]snug, not tight[/Y]), and named parts you're identifying for the first time ([Y]secondary water separator[/Y]). Use [R]...[/R] for safety-critical warnings only ([R]Don't use flammable spray near a hot engine.[/R]) — those are rare. Do not over-emphasise; if everything is bold, nothing is.

Voice: friend on the phone. One thought per bullet. When you name an action, say what the user sees or feels when it works ("releases with a soft click," "should turn easily for 5 to 6 full turns"). Avoid "carefully" and "make sure" — say the thing those words are hiding ("snug, not tight" beats "be careful not to overtighten"). Never engineer vocabulary: no "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."

EXAMPLES of the right shape (for a misfire coil-swap walkthrough):

Step "Disconnect coil 1":
- Squeeze the plastic tab on the wire connector and pull [Y]straight up[/Y] — it releases with a soft click.
- Use a [Y]10mm socket[/Y] to remove the single bolt holding the coil down.
- Pocket the bolt so you don't lose it.

Step "Drop in the new one":
- Push the new coil [Y]straight down[/Y] until you feel it seat onto the spark plug.
- Bolt it back down — [Y]snug, not tight[/Y].
- Reconnect the wire until you hear the click.
- Close the hood and start the engine — the misfire should clear in a minute or two.

JSON OUTPUT: {"body": "- bullet one\n- bullet two\n- bullet three"}
Use literal "\n" between bullets in the JSON string. No preamble, no markdown headings, no numbered list, no closing remarks outside the bullets.
