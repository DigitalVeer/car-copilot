"""OBD source abstraction. Phase 1 ships MockOBDSource only.

The real python-OBD wrapper lands in a later phase; the interface here is what
both implementations honor so the rest of the app stays source-agnostic.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from pathlib import Path

from .schema import OBDSnapshot


class OBDSource(ABC):
    @abstractmethod
    async def take_snapshot(self) -> OBDSnapshot: ...

    @abstractmethod
    async def is_connected(self) -> bool: ...

    async def close(self) -> None:
        return None


class MockOBDSource(OBDSource):
    """Loads a fixture JSON file and returns it as an OBDSnapshot."""

    def __init__(self, scenario_path: str | Path) -> None:
        self._path = Path(scenario_path)
        self._snapshot = OBDSnapshot.from_file(self._path)

    async def take_snapshot(self) -> OBDSnapshot:
        return self._snapshot

    async def is_connected(self) -> bool:
        return True
