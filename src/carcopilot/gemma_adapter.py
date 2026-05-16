"""Local Gemma access via Ollama's HTTP API.

This is the only file that talks to the LLM. Every caller wraps Gemma calls in
``try/except GemmaFailure`` and pulls canned text from ``fallback.py`` when the
adapter gives up. Two retry budgets — network/parse errors and "looks insane"
output — both fold into a single ``GemmaFailure`` so callers never branch on
the reason. The contract is: success → structured Pydantic, failure → raise.

Ollama's ``format`` parameter is set to a JSON schema on every call. That
eliminates the "model returned broken JSON" failure mode at the model level.
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any

import httpx
from pydantic import ValidationError

from .schema import (
    DTC,
    HistoryEntry,
    HistoryPattern,
    Issue,
    Language,
    LiveReading,
    SynthesisResult,
    WalkthroughStep,
)

logger = logging.getLogger(__name__)

DEFAULT_HOST = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
DEFAULT_MODEL = os.environ.get("GEMMA_MODEL", "gemma4:e4b")
# Gemma 4 generations of synthesis/walkthrough/draft each take ~30–60s on
# commodity hardware. 120s gives one call comfortable headroom; the pipeline
# runs the three calls concurrently so wall time is bounded by the slowest.
DEFAULT_TIMEOUT_S = float(os.environ.get("GEMMA_TIMEOUT_S", "120"))
DEFAULT_MAX_RETRIES = int(os.environ.get("GEMMA_MAX_RETRIES", "2"))

_PROMPTS_DIR_DEFAULT = Path(__file__).resolve().parent.parent.parent / "prompts"


class GemmaFailure(RuntimeError):
    """Raised when the adapter has exhausted retries. Callers fall back."""


_SYNTHESIS_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "synthesis": {"type": "string"},
        "good_news": {"type": "string"},
    },
    "required": ["synthesis"],
}

_WALKTHROUGH_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "steps": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "number": {"type": "integer"},
                    "title": {"type": "string"},
                    "body": {"type": "string"},
                },
                "required": ["number", "title", "body"],
            },
        }
    },
    "required": ["steps"],
}

_MECHANIC_DRAFT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {"draft": {"type": "string"}},
    "required": ["draft"],
}

_HISTORY_PATTERN_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "patterns": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "pattern_type": {
                        "type": "string",
                        "enum": ["recurrence", "trend", "correlation"],
                    },
                    "synthesis": {"type": "string"},
                    "suggested_root_cause": {"type": "string"},
                },
                "required": ["pattern_type", "synthesis"],
            },
        }
    },
    "required": ["patterns"],
}


class GemmaAdapter:
    def __init__(
        self,
        host: str | None = None,
        model: str | None = None,
        prompts_dir: Path | None = None,
        timeout_s: float | None = None,
        max_retries: int | None = None,
    ) -> None:
        self.host = (host or DEFAULT_HOST).rstrip("/")
        self.model = model or DEFAULT_MODEL
        self.prompts_dir = Path(prompts_dir) if prompts_dir else _PROMPTS_DIR_DEFAULT
        self.timeout_s = timeout_s if timeout_s is not None else DEFAULT_TIMEOUT_S
        self.max_retries = max_retries if max_retries is not None else DEFAULT_MAX_RETRIES

        self._system_prompt = (self.prompts_dir / "system.md").read_text()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def generate_synthesis(
        self,
        issue: Issue,
        language: Language = "en",
    ) -> SynthesisResult:
        user_prompt = self._render_template(
            "issue_synthesis.md",
            vehicle=issue.vehicle.display_name,
            mileage=issue.vehicle.mileage if issue.vehicle.mileage is not None else "unknown",
            severity=issue.severity,
            route=issue.route,
            title=issue.title,
            subtitle=issue.subtitle,
            cost_min=issue.meta.cost_usd_min if issue.meta.cost_usd_min is not None else "—",
            cost_max=issue.meta.cost_usd_max if issue.meta.cost_usd_max is not None else "—",
            time_minutes=issue.meta.time_minutes if issue.meta.time_minutes is not None else "—",
            drivability=issue.meta.drivability or "—",
            dtcs=_format_dtcs(issue.dtcs),
            live_readings=_format_readings(issue.live_readings),
            language=language,
        )

        data = await self._call_with_retry(user_prompt, _SYNTHESIS_SCHEMA, num_predict=300)
        try:
            result = SynthesisResult.model_validate(data)
        except ValidationError as e:
            raise GemmaFailure(f"synthesis: pydantic validation failed: {e}") from e
        if not _looks_sane_synthesis(result):
            raise GemmaFailure("synthesis: output failed sanity check")
        return result

    async def generate_walkthrough(
        self,
        issue: Issue,
        language: Language = "en",
    ) -> list[WalkthroughStep]:
        user_prompt = self._render_template(
            "walkthrough.md",
            vehicle=issue.vehicle.display_name,
            title=issue.title,
            subtitle=issue.subtitle,
            difficulty=issue.meta.difficulty or "moderate",
            time_minutes=issue.meta.time_minutes if issue.meta.time_minutes is not None else "—",
            category=issue.category,
            language=language,
        )

        data = await self._call_with_retry(user_prompt, _WALKTHROUGH_SCHEMA, num_predict=900)
        raw_steps = data.get("steps", [])
        if not isinstance(raw_steps, list) or not raw_steps:
            raise GemmaFailure("walkthrough: no steps returned")
        steps: list[WalkthroughStep] = []
        for raw in raw_steps:
            try:
                steps.append(WalkthroughStep.model_validate(raw))
            except ValidationError as e:
                raise GemmaFailure(f"walkthrough: invalid step {raw!r}: {e}") from e
        if not _looks_sane_walkthrough(steps):
            raise GemmaFailure("walkthrough: output failed sanity check")
        return steps

    async def generate_mechanic_draft(
        self,
        issue: Issue,
        language: Language = "en",
    ) -> str:
        user_prompt = self._render_template(
            "mechanic_draft.md",
            vehicle=issue.vehicle.display_name,
            mileage=issue.vehicle.mileage if issue.vehicle.mileage is not None else "unknown",
            title=issue.title,
            subtitle=issue.subtitle,
            dtcs=_format_dtcs(issue.dtcs),
            live_readings=_format_readings(issue.live_readings),
            language=language,
        )

        data = await self._call_with_retry(user_prompt, _MECHANIC_DRAFT_SCHEMA, num_predict=400)
        draft = data.get("draft")
        if not isinstance(draft, str) or len(draft.strip()) < 30:
            raise GemmaFailure("mechanic_draft: output failed sanity check")
        return draft.strip()

    async def find_history_patterns(
        self,
        recent_issues: list[HistoryEntry],
        vehicle_display_name: str,
        language: Language = "en",
    ) -> list[HistoryPattern]:
        entries_block = "\n".join(
            f"- {e.issue.detected_at.date()} · {e.issue.category} · "
            f"{e.issue.title} ({e.state})"
            for e in recent_issues
        )
        user_prompt = self._render_template(
            "history_pattern.md",
            vehicle=vehicle_display_name,
            entries=entries_block or "(no entries)",
            language=language,
        )

        data = await self._call_with_retry(user_prompt, _HISTORY_PATTERN_SCHEMA, num_predict=500)
        raw_patterns = data.get("patterns", [])
        patterns: list[HistoryPattern] = []
        for raw in raw_patterns:
            try:
                patterns.append(HistoryPattern.model_validate(raw))
            except ValidationError as e:
                logger.warning("history_pattern: dropping invalid pattern %r: %s", raw, e)
        return patterns

    # ------------------------------------------------------------------
    # Internals
    # ------------------------------------------------------------------

    def _render_template(self, filename: str, **fields: object) -> str:
        template = (self.prompts_dir / filename).read_text()
        return template.format(**fields)

    async def _call_with_retry(
        self,
        user_prompt: str,
        schema: dict[str, Any],
        num_predict: int | None = None,
    ) -> dict[str, Any]:
        last_err: Exception | None = None
        for attempt in range(self.max_retries + 1):
            try:
                return await self._call_ollama(user_prompt, schema, num_predict)
            except (httpx.HTTPError, json.JSONDecodeError) as e:
                last_err = e
                logger.warning(
                    "Gemma attempt %d/%d failed: %s",
                    attempt + 1,
                    self.max_retries + 1,
                    e,
                )
        raise GemmaFailure(f"all {self.max_retries + 1} attempts failed: {last_err}")

    async def _call_ollama(
        self,
        user_prompt: str,
        schema: dict[str, Any],
        num_predict: int | None = None,
    ) -> dict[str, Any]:
        # ``think: false`` disables Gemma 4's chain-of-thought block, which would
        # otherwise add tens of seconds and tokens we never read. Output is
        # constrained by ``format`` (JSON schema) and bounded by ``num_predict``
        # so a chatty model can't blow past the timeout.
        options: dict[str, Any] = {"temperature": 0.3}
        if num_predict is not None:
            options["num_predict"] = num_predict
        body = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": self._system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "format": schema,
            "stream": False,
            "think": False,
            "options": options,
        }
        async with httpx.AsyncClient(timeout=self.timeout_s) as client:
            r = await client.post(f"{self.host}/api/chat", json=body)
            r.raise_for_status()
            payload = r.json()
        content = payload.get("message", {}).get("content", "")
        if not content:
            raise json.JSONDecodeError("empty content from Ollama", "", 0)
        return json.loads(content)


# ---------------------------------------------------------------------------
# Formatting helpers (module-level so tests can exercise them directly).
# ---------------------------------------------------------------------------


def _format_dtcs(dtcs: list[DTC]) -> str:
    if not dtcs:
        return "(none)"
    return "\n".join(
        f"- {d.code}: {d.description}" + (" [deferred]" if d.deferred else "")
        for d in dtcs
    )


def _format_readings(readings: list[LiveReading]) -> str:
    if not readings:
        return "(none)"
    parts = []
    for r in readings:
        unit = f" {r.unit}" if r.unit else ""
        note = f" — {r.note}" if r.note else ""
        parts.append(f"- {r.key}: {r.value}{unit} ({r.status}){note}")
    return "\n".join(parts)


def _looks_sane_synthesis(result: SynthesisResult) -> bool:
    text = result.synthesis.strip()
    if len(text) < 20 or len(text) > 1200:
        return False
    if text.lower().startswith(("here is", "here's", "as an ai", "i think")):
        return False
    return True


def _looks_sane_walkthrough(steps: list[WalkthroughStep]) -> bool:
    if not (2 <= len(steps) <= 8):
        return False
    for s in steps:
        if not s.title.strip() or len(s.body.strip()) < 15:
            return False
    return True
