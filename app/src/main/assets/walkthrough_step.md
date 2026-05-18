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

Write the body as 2 to 4 sentences covering only this step — not the whole repair. Concrete, physical, where-to-look-and-what-to-do. Use specifics from the curated procedure: tool sizes, torque numbers, gap, cylinder positions. Avoid "carefully" and "make sure" — say the thing those words are hiding ("snug, not tight" beats "be careful not to overtighten"; "the bolt should turn with hand pressure" beats "make sure it goes in straight").

Friend-on-the-phone voice. One thought per sentence. When you name an action, say what the user sees or feels when it works ("releases with a soft click," "should turn easily for 5 to 6 full turns"). Never engineer vocabulary: no "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."

JSON: {"body": "..."}
No preamble, no markdown.
