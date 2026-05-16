"""End-to-end demo: healthy snapshot (no DTCs, normal live readings)."""

from __future__ import annotations

from _runner import run_scenario

if __name__ == "__main__":
    run_scenario("healthy.json")
