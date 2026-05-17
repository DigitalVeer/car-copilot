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

Write the body as 2 to 4 sentences covering only this step — not the whole repair. Concrete, physical, where-to-look-and-what-to-do. Use specifics from the curated procedure: tool sizes, torque numbers, gap, cylinder positions. Avoid "carefully" and "make sure" — say the thing those words are hiding ("snug, not tight" beats "be careful not to overtighten"; "the bolt should turn with hand pressure" beats "make sure it goes in straight").

Friend-on-the-phone voice. One thought per sentence. When you name an action, say what the user sees or feels when it works ("releases with a soft click," "should turn easily for 5 to 6 full turns"). Never engineer vocabulary: no "polling," "telemetry," "diagnostic data," "execution," "edge," "agentic."

JSON: {"body": "..."}
No preamble, no markdown.
