"""Pipeline tests — full snapshot → Issue flow.

The classifier already has its own tests; here we verify the wiring between
classification, Gemma adapter, and fallback. The first test uses a stub
adapter that returns canned successful results. The second simulates the
Phase 2 acceptance condition: point the real adapter at a nonexistent host
and confirm every Issue renders with ``using_fallback_text=True`` while
keeping the deterministic fields intact.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from carcopilot.gemma_adapter import GemmaAdapter, GemmaFailure
from carcopilot.obd_source import MockOBDSource
from carcopilot.pipeline import build_issues
from carcopilot.schema import (
    Issue,
    SynthesisResult,
    WalkthroughStep,
)

FIXTURES = Path(__file__).parent / "fixtures" / "obd_snapshots"


class StubAdapter(GemmaAdapter):
    """Replaces every Gemma call with a canned, deterministic result.

    Inherits from ``GemmaAdapter`` so it satisfies the type contract but
    never reaches Ollama — every method short-circuits to canned text.
    """

    def __init__(self) -> None:
        # Skip parent __init__ — we don't want to read prompts or hit network.
        self.host = "http://stub"
        self.model = "stub"
        self.timeout_s = 1.0
        self.max_retries = 0
        self._system_prompt = ""

    async def generate_synthesis(self, issue, language="en"):
        return SynthesisResult(
            synthesis=(
                "Cylinder 1 keeps misfiring. On a Corolla this age, that almost "
                "always means a worn ignition coil — about $45 and half an hour."
            ),
            good_news="Easy enough to do in the driveway.",
        )

    async def generate_walkthrough(self, issue, language="en"):
        return [
            WalkthroughStep(
                number=1,
                title="Park and pop the hood",
                body="Pull in, turn the engine off, give it ten minutes to cool.",
            ),
            WalkthroughStep(
                number=2,
                title="Find the coil",
                body="It's the small black block on cylinder 1, timing-belt side of the engine.",
            ),
            WalkthroughStep(
                number=3,
                title="Swap and start",
                body="Pull the old coil out, drop the new one in, close the hood, start it up.",
            ),
        ]

    async def generate_mechanic_draft(self, issue, language="en"):
        return (
            "Hi — 2009 Corolla, 187k miles, showing a P0301 misfire. "
            "Looks like a coil. Could you give a quick estimate?"
        )


@pytest.mark.asyncio
async def test_pipeline_populates_issue_with_stub_adapter():
    source = MockOBDSource(FIXTURES / "misfire.json")
    snapshot = await source.take_snapshot()
    issues = await build_issues(snapshot, StubAdapter())

    assert len(issues) == 1
    issue = issues[0]
    assert isinstance(issue, Issue)
    assert issue.severity == "warning"
    assert issue.route == "diy"
    assert issue.category == "misfire"
    assert issue.synthesis is not None and "Cylinder 1" in issue.synthesis
    assert issue.good_news is not None
    assert len(issue.walkthrough_steps) == 3
    assert issue.mechanic_draft is not None
    assert issue.using_fallback_text is False
    # DTCs from classification are reflected onto the Issue.
    assert any(d.code == "P0301" and not d.deferred for d in issue.dtcs)
    # Live readings carry through and pick up statuses.
    rpm = next(r for r in issue.live_readings if "RPM" in r.key)
    assert rpm.status == "warning"
    assert rpm.note == "rough"


@pytest.mark.asyncio
async def test_pipeline_falls_back_when_host_unreachable():
    """Phase 2 acceptance check: bad host → same Issue, using_fallback_text=true."""
    source = MockOBDSource(FIXTURES / "misfire.json")
    snapshot = await source.take_snapshot()
    bad = GemmaAdapter(host="http://127.0.0.1:1", max_retries=0, timeout_s=1.0)

    issues = await build_issues(snapshot, bad)

    assert len(issues) == 1
    issue = issues[0]
    assert issue.severity == "warning"
    assert issue.route == "diy"
    assert issue.using_fallback_text is True
    assert issue.synthesis  # canned fallback synthesis, never None
    assert "Cylinder 1" in issue.synthesis
    assert issue.walkthrough_steps  # canned misfire walkthrough
    assert issue.mechanic_draft and "Corolla" in issue.mechanic_draft


@pytest.mark.asyncio
async def test_overheat_pipeline_skips_mechanic_draft_for_safety_route():
    source = MockOBDSource(FIXTURES / "overheat.json")
    snapshot = await source.take_snapshot()
    bad = GemmaAdapter(host="http://127.0.0.1:1", max_retries=0, timeout_s=1.0)

    issues = await build_issues(snapshot, bad)
    primary = issues[0]
    assert primary.severity == "severe"
    assert primary.route == "safety"
    # Safety route still has inline walkthrough steps (design doc §11.3).
    assert primary.walkthrough_steps
    # No mechanic draft for safety route — focus is on the procedure.
    assert primary.mechanic_draft is None
    # P0301 deferred under the overheat override.
    assert any(d.code == "P0301" and d.deferred for d in primary.dtcs)


@pytest.mark.asyncio
async def test_healthy_pipeline_skips_walkthrough_and_draft():
    source = MockOBDSource(FIXTURES / "healthy.json")
    snapshot = await source.take_snapshot()
    bad = GemmaAdapter(host="http://127.0.0.1:1", max_retries=0, timeout_s=1.0)

    issues = await build_issues(snapshot, bad)
    [issue] = issues
    assert issue.severity == "healthy"
    assert issue.route == "none"
    # No walkthrough or mechanic draft for healthy.
    assert issue.walkthrough_steps == []
    assert issue.mechanic_draft is None
    # Synthesis is the canned healthy fallback.
    assert issue.using_fallback_text is True
    assert issue.synthesis
