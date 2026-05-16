"""Gemma adapter tests.

The mock-based tests run in CI and exercise the success path, the retry
budget, and the sanity-check failure path. A second test class verifies the
no-network fallback: pointed at a nonexistent host with a tiny retry budget
and short timeout, the adapter must raise ``GemmaFailure`` rather than hang
or throw a raw network error.

The Phase 2 acceptance case — "re-run with OLLAMA_HOST pointed at a
nonexistent host and confirm the same Issue renders with using_fallback_text
true" — is covered by ``test_pipeline.py::test_pipeline_falls_back_when_host_unreachable``
which uses ``build_issues`` end-to-end.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime

import httpx
import pytest

from carcopilot.gemma_adapter import GemmaAdapter, GemmaFailure
from carcopilot.schema import (
    DTC,
    Issue,
    IssueMeta,
    LiveReading,
    VehicleInfo,
    WalkthroughStep,
)

DEMO_VEHICLE = VehicleInfo(
    year=2009,
    make="Toyota",
    model="Corolla",
    mileage=187000,
    display_name="2009 Corolla",
)


def _issue() -> Issue:
    return Issue(
        id="20260514T194211Z-P0301",
        detected_at=datetime(2026, 5, 14, 19, 42, 11, tzinfo=UTC),
        vehicle=DEMO_VEHICLE,
        severity="warning",
        route="diy",
        category="misfire",
        title="Replace ignition coil — cylinder 1",
        subtitle="A small black block on top of the engine.",
        meta=IssueMeta(cost_usd_min=40, cost_usd_max=60, time_minutes=30, difficulty="easy"),
        dtcs=[DTC(code="P0301", description="Cylinder 1 misfire detected")],
        live_readings=[LiveReading(key="RPM (idle)", value="740", unit="rpm", status="warning")],
    )


# ---------------------------------------------------------------------------
# Synthesis: success + sanity-check + retry exhaustion
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_generate_synthesis_returns_valid_result(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=0)
    response = {
        "synthesis": (
            "Cylinder 1 keeps misfiring. Most often that's a worn coil pack — "
            "about $45 and 30 minutes to swap."
        ),
        "good_news": "Easy fix you can do in the driveway.",
    }
    _patch_chat(monkeypatch, response)

    result = await adapter.generate_synthesis(_issue())

    assert "Cylinder 1" in result.synthesis
    assert result.good_news is not None


@pytest.mark.asyncio
async def test_generate_synthesis_rejects_too_short_output(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=0)
    _patch_chat(monkeypatch, {"synthesis": "bad."})

    with pytest.raises(GemmaFailure):
        await adapter.generate_synthesis(_issue())


@pytest.mark.asyncio
async def test_generate_synthesis_rejects_assistant_preamble(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=0)
    _patch_chat(
        monkeypatch,
        {"synthesis": "Here is the synthesis for the misfire problem you described."},
    )

    with pytest.raises(GemmaFailure):
        await adapter.generate_synthesis(_issue())


@pytest.mark.asyncio
async def test_retry_then_succeed(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=1)

    sequence = [
        _RaiseOnce(httpx.ConnectError("boom")),
        {
            "synthesis": (
                "Cylinder 1 keeps misfiring. Most often that's a worn coil pack — "
                "about $45 and 30 minutes to swap."
            ),
        },
    ]
    _patch_chat_sequence(monkeypatch, sequence)

    result = await adapter.generate_synthesis(_issue())
    assert "Cylinder 1" in result.synthesis


@pytest.mark.asyncio
async def test_retries_exhausted_raises_gemma_failure(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=1)
    _patch_chat_sequence(
        monkeypatch,
        [
            _RaiseOnce(httpx.ConnectError("boom")),
            _RaiseOnce(httpx.ConnectError("boom again")),
        ],
    )

    with pytest.raises(GemmaFailure):
        await adapter.generate_synthesis(_issue())


# ---------------------------------------------------------------------------
# Walkthrough
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_generate_walkthrough_validates_step_shape(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=0)
    _patch_chat(
        monkeypatch,
        {
            "steps": [
                {
                    "number": 1,
                    "title": "Find the coil",
                    "body": "Pop the hood and look for the coil on cylinder 1, nearest the timing-belt side.",
                },
                {
                    "number": 2,
                    "title": "Swap the coil",
                    "body": "Unclip the wire, unbolt the 10mm, pull it out and put the new one in.",
                },
                {
                    "number": 3,
                    "title": "Start it up",
                    "body": "Close the hood and start the engine — it should clear within a drive.",
                },
            ]
        },
    )

    steps = await adapter.generate_walkthrough(_issue())
    assert len(steps) == 3
    assert all(isinstance(s, WalkthroughStep) for s in steps)


# ---------------------------------------------------------------------------
# Mechanic draft
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_generate_mechanic_draft_returns_trimmed_string(monkeypatch):
    adapter = GemmaAdapter(host="http://stub", max_retries=0)
    _patch_chat(
        monkeypatch,
        {
            "draft": (
                "  Hi — I've got a 2009 Corolla with about 187k miles. "
                "The check-engine light came on with a P0301 — cylinder 1 misfire. "
                "Likely a worn ignition coil. "
                "Could you give a rough quote before I bring it in?  "
            )
        },
    )

    draft = await adapter.generate_mechanic_draft(_issue())
    assert draft.startswith("Hi")
    assert "P0301" in draft
    assert not draft.startswith(" ")


# ---------------------------------------------------------------------------
# Bad-host fallback path
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_unreachable_host_raises_gemma_failure():
    adapter = GemmaAdapter(
        host="http://127.0.0.1:1",  # nothing listens here
        max_retries=0,
        timeout_s=1.0,
    )
    with pytest.raises(GemmaFailure):
        await adapter.generate_synthesis(_issue())


# ---------------------------------------------------------------------------
# Test helpers — patch httpx.AsyncClient.post on the adapter's instance.
# ---------------------------------------------------------------------------


class _RaiseOnce:
    """Sentinel: when the patched post hits one of these, it raises ``exc``."""

    def __init__(self, exc: Exception) -> None:
        self.exc = exc


def _patch_chat(monkeypatch, response_json: dict) -> None:
    """Patch httpx.AsyncClient.post to return a fixed response."""
    _patch_chat_sequence(monkeypatch, [response_json])


def _patch_chat_sequence(monkeypatch, sequence: list) -> None:
    """Patch httpx.AsyncClient.post to walk through ``sequence`` once per call.

    A dict element becomes a 200 response whose ``message.content`` is the
    JSON-serialized dict (mimicking Ollama). A ``_RaiseOnce`` element raises
    its ``exc`` instead, simulating network failure.
    """
    calls = iter(sequence)

    async def fake_post(self, url, **kwargs):  # type: ignore[override]
        try:
            item = next(calls)
        except StopIteration as e:
            raise AssertionError("unexpected extra call to ollama") from e
        if isinstance(item, _RaiseOnce):
            raise item.exc
        body = {"message": {"content": json.dumps(item)}}
        request = httpx.Request("POST", url)
        return httpx.Response(
            200,
            content=json.dumps(body).encode(),
            headers={"content-type": "application/json"},
            request=request,
        )

    monkeypatch.setattr(httpx.AsyncClient, "post", fake_post)
