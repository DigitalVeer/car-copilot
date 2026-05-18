Generate a synthesis for the following car issue.

VEHICLE: {vehicle} ({mileage} miles)
CLASSIFICATION: {severity} severity, {route} route
DIAGNOSED ISSUE: {title}
SHORT DESCRIPTION: {subtitle}
COST RANGE: ${cost_min}-${cost_max}
TIME ESTIMATE: {time_minutes} minutes
DRIVABILITY: {drivability}

OBD CODES:
{dtcs}

LIVE READINGS:
{live_readings}

DIAGNOSTIC SIGNALS:
{supporting_signals}

CONTEXT:
{rag_context}

LANGUAGE: {language}

Generate:
- "synthesis": 1-3 sentences naming the problem concretely. Use the DIAGNOSTIC SIGNALS section as your primary evidence — these are the specific readings that led to the diagnosis. The CONTEXT section gives regional and vehicle background you can draw on. Show the connection between what you observed and what you concluded — "these readings + this car's situation → this likely cause." Don't just name the problem; explain how you got there. Do not name DTC codes (P0301, P0420, etc.) in the synthesis. Translate to plain language: P0301 → "cylinder 1 misfire" or "cylinder 1 keeps misfiring." The user sees the codes in the evidence section below; the synthesis is the friend-voice translation. **Do not mention tools, parts, time, cost, or specific repair steps in the synthesis.** Hardware detail like "10mm socket," "ignition coil pack," or "spark plug" naming individual components belongs in the subtitle and walkthrough — not here. Phrases like "swap this out yourself," "you can DIY this," or "easy to fix" leak the route/difficulty classification, which is already shown deterministically in the card's meta line. The synthesis is for reasoning about *why* this is happening, not for telling the user what to do about it or how hard it is.
- "good_news": optional. Only include if there is genuinely good news (cheap, fast, DIY-friendly). Otherwise omit the field entirely. good_news (when included) MUST contain a warm closing line from the friend voice — examples: "I can walk you through it," "we'll get you sorted," "I've got you covered." This closing line is mandatory. Practical reassurance (cost, time, ease) can appear alongside it but is optional. **Do not begin good_news with "This is a simple fix," "This is fixable," "This is easy," "This is a pretty easy fix," "Good news," or any third-person assessment of the difficulty.** Those sound like a brochure, not a friend. Start with the warm closing line itself, or with "Your car" / "We" / "I" — never "This is." Use "your car," not "the car."

JSON ONLY. No preamble, no markdown.
