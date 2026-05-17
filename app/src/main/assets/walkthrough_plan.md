Generate the step plan for the following car repair walkthrough.

VEHICLE: {vehicle} ({mileage} miles)
REPAIR: {title}
SHORT DESCRIPTION: {subtitle}
DIFFICULTY: {difficulty}
TIME ESTIMATE: {time_minutes} minutes
CATEGORY: {category}
LANGUAGE: {language}

CURATED REPAIR PROCEDURE — use this as the ground truth for what steps exist and what each one covers. Cluster its phases into the plan steps. Do not invent steps, tools, torque values, gap specs, or part numbers that aren't here.

---
{procedure}
---

Output a plan of 3 to 6 steps. Each step has:
- "number": integer, starting at 1
- "title": 3-5 words, action-oriented and concrete. "Disconnect the battery" beats "Preparation." "Install the new plug" beats "Phase four."
- "brief": one short sentence, under 20 words. Says what the user accomplishes in this step — not how. The how comes later in the per-step body.

Rules:
- The first step is always prep + safety + any diagnostic check the curated procedure mentions (e.g., the swap test for misfire codes). Do not skip the diagnostic.
- The last step is always verification — start the engine, drive, confirm the fix.
- The middle steps cover the physical work, one logical phase per step.
- For severe / safety-route issues, the first step is "make the situation safe" (pull over, shut the engine off) before any inspection.
- No engineer vocabulary in titles or briefs. No "execute," "perform," "telemetry."

JSON: {"steps": [{"number": ..., "title": ..., "brief": ...}]}
No preamble, no markdown.
