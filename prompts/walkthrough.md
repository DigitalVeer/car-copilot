Generate a step-by-step walkthrough for the following repair.

VEHICLE: {vehicle}
REPAIR: {title}
SHORT DESCRIPTION: {subtitle}
DIFFICULTY: {difficulty}
TIME ESTIMATE: {time_minutes} minutes
CATEGORY: {category}
LANGUAGE: {language}

Generate 3-6 steps. Each step has:
- "number": integer, starting at 1
- "title": 3-5 words, action-oriented ("Find the coils", "Disconnect the wire")
- "body": 2-4 sentences. Concrete, physical, where-to-look-and-what-to-do. Avoid "carefully" and "make sure" — use specifics instead ("the bolt should turn with hand pressure" not "be careful not to overtighten").

For severe / safety-route issues, the first step is always to make the situation safe (pull over, shut the engine off, etc.) before any inspection.

JSON: {{"steps": [{{"number": ..., "title": ..., "body": ...}}]}}
No preamble, no markdown.
