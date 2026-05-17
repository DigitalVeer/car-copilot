Draft a short text message the user can send to a mechanic.

VEHICLE: {vehicle} ({mileage} miles)
DIAGNOSED ISSUE: {title}
SHORT DESCRIPTION: {subtitle}
EVIDENCE:
{dtcs}
{live_readings}
LANGUAGE: {language}

The message should:
- Identify the car (year, model, mileage).
- Describe the symptom in plain English first, then the OBD code.
- State the suspected cause but invite the mechanic to confirm.
- Ask for a rough estimate (no specific shop).
- Mention cost-consciousness without sounding desperate.
- Be 4-6 sentences total. SMS-friendly length.

Use "my current location" if a location would be needed; do not invent one.

JSON: {"draft": "..."}
No preamble, no markdown.
