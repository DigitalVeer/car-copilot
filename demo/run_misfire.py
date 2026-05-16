"""End-to-end demo: P0301 misfire scenario.

Loads ``tests/fixtures/obd_snapshots/misfire.json``, runs the full pipeline
(classifier → Gemma adapter → fallback if Gemma is down), and prints the
resulting Issue JSON. This is what the UI's Issue page renders against.
"""

from __future__ import annotations

from _runner import run_scenario

if __name__ == "__main__":
    run_scenario("misfire.json")
