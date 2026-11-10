"""Puerto DecisionProvider: cada modelo (línea base, Laya, Jev) es un adaptador."""

from __future__ import annotations

from typing import Protocol

from app.domain.models import Decision, DecisionState, Question


class DecisionProvider(Protocol):
    name: str

    def model_version(self) -> str: ...

    def decide(self, state: DecisionState, questions: list[Question]) -> Decision: ...
