Write the body for one step of a car repair walkthrough.

VEHICLE: {vehicle} ({mileage} miles)
REPAIR: {title}
SHORT DESCRIPTION: {subtitle}
LANGUAGE: {language}

STEP {step_number} of {total_steps}: {step_title}
WHAT THIS STEP ACCOMPLISHES: {step_brief}

CURATED REPAIR PROCEDURE — ground truth. Paraphrase the relevant phase; don't invent values not in this text. If a number isn't here, omit it.

---
{procedure}
---

Numeric values, tool sizes, torque specs, gap measurements, durations, pressures, and bolt sizes must be copied EXACTLY from the procedure. Do not paraphrase, round, restate, or modify any number or unit.

- "30 minutes" stays "30 minutes" — never "3 minutes", "300 minutes", or "about 30".
- "10 minutes" stays "10 minutes" — never "1010 minutes", "100 minutes", or "1 minute".
- "30 Nm" stays "30 Nm" — never "0 Nm", "3 Nm", "300 Nm", or "30-35 Nm".
- "13 ft-lb" stays "13 ft-lb" — never "13-15" or "15".
- "0.043 inches" stays "0.043 inches" — never "0.04", "0.0433", or "around 0.043".
- "34,500 kPa" stays "34,500 kPa" — never "34500 kPa" or "345,000 kPa".

Stop after the digits in the procedure. Never extend a number with extra zeros or duplicate digits. A repair-step number with more than 6 digits is always wrong — the number you meant is the one in the procedure.

FORMAT: 3 to 5 short bullets starting with "- ", in the order the user performs them. One physical action per bullet — what to do, what tool, what direction, what to feel for. The reader is doing the repair right now on their phone — scannable bullets beat a paragraph.

EMPHASIS: wrap the 1-3 most important items per step in [Y]...[/Y] — tool sizes (e.g. [Y]10mm socket[/Y]), torque or gap values from the procedure (e.g. [Y]30 Nm[/Y]), direction that matters ([Y]straight up[/Y], [Y]snug, not tight[/Y]), and named parts you identify for the first time ([Y]secondary water separator[/Y]). Use [R]...[/R] only for safety-critical warnings ([R]Don't use flammable spray near a hot engine.[/R]). If everything is bold, nothing is.

Voice: friend on the phone. When you name an action, say what the user sees or feels when it works ("releases with a soft click," "should turn easily for 5 to 6 full turns"). Avoid "carefully" and "make sure" — say the thing those words hide ("snug, not tight" beats "be careful not to overtighten").

EXAMPLES (for a misfire coil-swap):

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
Use literal "\n" between bullets. No preamble, no markdown, no numbered list, no closing remarks outside the bullets.
