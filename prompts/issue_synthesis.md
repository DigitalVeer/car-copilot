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
- "synthesis": 1-3 sentences naming the problem concretely. Reference at least two specific live readings or DTC signals that support the diagnosis. Show the connection between what you observed and what you concluded — "these readings + this car's age → this likely cause." Don't just name the problem; explain how you got there. Stay inside the cost and time figures above — don't invent numbers.
- "good_news": optional. Only include if there is genuinely good news (cheap, fast, DIY-friendly). Single sentence. Phrase as a friend reassuring a friend. "I can walk you through it" beats "This is fixable." Use "your car," not "the car." Otherwise omit the field entirely.

JSON ONLY. No preamble, no markdown.
