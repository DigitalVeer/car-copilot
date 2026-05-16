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

LANGUAGE: {language}

Generate:
- "synthesis": 1-3 sentences naming the problem concretely. Reference at least two specific live readings or DTC signals that support the diagnosis. Show the connection between what you observed and what you concluded — "these readings + this car's age → this likely cause." Don't just name the problem; explain how you got there. Do not name DTC codes (P0301, P0420, etc.) in the synthesis. Translate to plain language: P0301 → "cylinder 1 misfire" or "cylinder 1 keeps misfiring." The user sees the codes in the evidence section below; the synthesis is the friend-voice translation. Do not restate cost, time, or difficulty in the synthesis — those values already appear deterministically in the card's meta line. The synthesis is for reasoning about why this is happening, not for repeating numbers. Keep cost mentions in good_news only, if at all.
- "good_news": optional. Only include if there is genuinely good news (cheap, fast, DIY-friendly). Single sentence. Phrase as a friend reassuring a friend. "I can walk you through it" beats "This is fixable." Use "your car," not "the car." Otherwise omit the field entirely.

JSON ONLY. No preamble, no markdown.
