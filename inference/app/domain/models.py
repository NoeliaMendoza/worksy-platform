"""Modelo de dominio del servicio de inferencia. Sin dependencias de FastAPI."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class QuestionType(str, Enum):
    CHOICE = "choice"
    SCORE = "score"


@dataclass(frozen=True)
class Question:
    key: str
    type: QuestionType
    instructions: str
    # Para CHOICE: opción -> descripción del criterio. Para SCORE puede ir vacío.
    criteria: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class DecisionState:
    cv_text: str
    job_description: str


@dataclass(frozen=True)
class Answer:
    """Respuesta a una pregunta. CHOICE usa choice y probability; SCORE usa score."""

    choice: str | None = None
    probability: float | None = None
    score: float | None = None


@dataclass(frozen=True)
class Decision:
    answers: dict[str, Answer]
    model_version: str


class ProviderNotAvailable(Exception):
    """El proveedor existe en el contrato pero todavía no está implementado o configurado."""


class ProviderFailure(Exception):
    """Fallo de un proveedor configurado, con un mensaje seguro para la respuesta HTTP."""

    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
