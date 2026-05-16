Look at this user's repair history and identify any meaningful pattern.

VEHICLE: {vehicle}

HISTORY (most recent first):
{entries}

LANGUAGE: {language}

A "meaningful pattern" is:
- The same kind of failure happening more than once in a year (recurrence)
- A trend in a sensor reading that suggests something is degrading (trend)
- Two issues that, taken together, suggest a common root cause that wasn't named in either issue alone (correlation)

If you find a pattern, propose a single most-likely root cause. Be specific — name the part, not the system. For repeated coil failures, "oil leaking past the valve cover gasket and fouling the coils" is the kind of specificity we want. Generic answers like "wear and tear" are not useful.

If you don't see a meaningful pattern, return an empty patterns array.

JSON: {{"patterns": [{{"pattern_type": "...", "synthesis": "...", "suggested_root_cause": "..."}}]}}
No preamble, no markdown.
