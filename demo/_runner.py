"""Shared demo plumbing.

Each ``demo/run_*.py`` is a thin wrapper over ``run_scenario`` — load a
fixture, build issues, pretty-print the resulting JSON. The goal is that
``docker compose exec app python demo/run_misfire.py`` prints the same JSON
the UI would consume.
"""

from __future__ import annotations

import asyncio
import json
from pathlib import Path

from rich import print as rprint

from carcopilot.gemma_adapter import GemmaAdapter
from carcopilot.obd_source import MockOBDSource
from carcopilot.pipeline import build_issues

FIXTURES = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "obd_snapshots"


async def _run(fixture_name: str) -> None:
    source = MockOBDSource(FIXTURES / fixture_name)
    snapshot = await source.take_snapshot()
    gemma = GemmaAdapter()
    issues = await build_issues(snapshot, gemma)

    payload = [json.loads(issue.model_dump_json()) for issue in issues]
    rprint(payload)

    fallbacks = [issue.id for issue in issues if issue.using_fallback_text]
    if fallbacks:
        rprint(f"[yellow]using_fallback_text=true for: {fallbacks}[/yellow]")


def run_scenario(fixture_name: str) -> None:
    asyncio.run(_run(fixture_name))
