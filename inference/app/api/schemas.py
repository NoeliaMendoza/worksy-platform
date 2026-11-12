"""Esquemas HTTP de POST /v1/decide (sección 7 del diseño)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StateIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cv_text: str = Field(min_length=1, max_length=200_000)
    job_description: str = Field(default="", max_length=50_000)


class QuestionIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["choice", "score"]
    instructions: str = Field(min_length=1, max_length=2_000)
    criteria: dict[str, str] = Field(default_factory=dict)

    @model_validator(mode="after")
    def _choice_needs_options(self) -> "QuestionIn":
        if self.type == "choice" and len(self.criteria) < 2:
            raise ValueError("una pregunta 'choice' necesita al menos dos opciones en 'criteria'")
        if self.type == "score" and self.criteria and not 2 <= len(self.criteria) <= 10:
            raise ValueError("una escala 'score' necesita entre 2 y 10 niveles o criteria vacío")
        return self


class DecideRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    provider: str = Field(min_length=1, max_length=32)
    state: StateIn
    questions: dict[str, QuestionIn] = Field(min_length=1, max_length=50)


class AnswerOut(BaseModel):
    choice: str | None = None
    probability: float | None = None
    score: float | None = None


class DecideResponse(BaseModel):
    answers: dict[str, AnswerOut]
    model_version: str
    latency_ms: int
