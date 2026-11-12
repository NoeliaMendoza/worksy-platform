"""Adaptador del servidor local de Laya: POST /v1/systemone.

Contrato verificado en laya/laya/serve.py y laya/tests/test_serve.py del repositorio
local. No necesita importar torch ni descargar modelos en el servicio de inferencia.
"""

from __future__ import annotations

import math
import os
from dataclasses import dataclass
from urllib.parse import urlsplit

import httpx

from app.domain.models import (
    Answer, Decision, DecisionState, ProviderFailure, ProviderNotAvailable, Question, QuestionType,
)

_SCORE_LEVELS = ["Ninguna compatibilidad", "Compatibilidad baja", "Compatibilidad media",
                 "Compatibilidad alta", "Compatibilidad muy alta"]


@dataclass(frozen=True)
class LayaSettings:
    base_url: str = ""
    model: str = "multilingual"
    model_version: str = ""
    api_key: str = ""
    timeout_seconds: float = 4.0
    max_len: int = 8192

    def __post_init__(self):
        if self.base_url:
            url = urlsplit(self.base_url)
            if url.scheme not in {"http", "https"} or not url.hostname or url.username or url.password or url.query or url.fragment:
                raise ValueError("LAYA_BASE_URL debe ser una URL HTTP(S) sin credenciales, consulta ni fragmento")
            if not self.model.strip() or not self.model_version.strip() or len(self.model_version) > 64:
                raise ValueError("Configura LAYA_MODEL y LAYA_MODEL_VERSION (máximo 64 caracteres)")
        if not math.isfinite(self.timeout_seconds) or not 0 < self.timeout_seconds <= 120:
            raise ValueError("LAYA_TIMEOUT_SECONDS debe estar entre 0 y 120 segundos")
        if not 1 <= self.max_len <= 8192:
            raise ValueError("LAYA_MAX_LEN debe estar entre 1 y 8192")

    @classmethod
    def from_env(cls) -> LayaSettings:
        return cls(
            base_url=os.getenv("LAYA_BASE_URL", "").strip().rstrip("/"),
            model=os.getenv("LAYA_MODEL", "multilingual").strip(),
            model_version=os.getenv("LAYA_MODEL_VERSION", "").strip(),
            api_key=os.getenv("LAYA_API_KEY", ""),
            timeout_seconds=float(os.getenv("LAYA_TIMEOUT_SECONDS", "4")),
            max_len=int(os.getenv("LAYA_MAX_LEN", "8192")),
        )


class LayaProvider:
    name = "laya"

    def __init__(self, settings: LayaSettings | None = None, *, transport: httpx.BaseTransport | None = None):
        self.settings = settings if settings is not None else LayaSettings.from_env()
        self.transport = transport

    def model_version(self) -> str:
        return self.settings.model_version or "laya-no-configurado"

    def decide(self, state: DecisionState, questions: list[Question]) -> Decision:
        if not self.settings.base_url:
            raise ProviderNotAvailable("Configura LAYA_BASE_URL y LAYA_MODEL_VERSION para utilizar Laya")
        question_body = {}
        score_levels = {}
        for q in questions:
            criteria: dict[str, str] | list[str] = dict(q.criteria)
            if q.type is QuestionType.SCORE:
                criteria = list(q.criteria.values()) if q.criteria else list(_SCORE_LEVELS)
                score_levels[q.key] = len(criteria)
            question_body[q.key] = {"type": q.type.value, "instructions": q.instructions, "criteria": criteria}
        body = {
            "model": self.settings.model,
            "state": {"cv_text": state.cv_text, "job_description": state.job_description},
            "questions": question_body,
            "max_len": self.settings.max_len,
        }
        headers = {"Accept": "application/json"}
        if self.settings.api_key:
            headers["Authorization"] = f"Bearer {self.settings.api_key}"
        try:
            # Sin redirecciones ni proxies heredados: el PDF solo se envía al servidor configurado.
            with httpx.Client(timeout=self.settings.timeout_seconds, transport=self.transport,
                              follow_redirects=False, trust_env=False) as client:
                response = client.post(self.settings.base_url.rstrip("/") + "/v1/systemone", json=body, headers=headers)
        except httpx.RequestError:
            raise ProviderFailure(503, "No se pudo conectar con el servidor de Laya o se agotó el tiempo de espera") from None
        if response.status_code in {400, 413, 422}:
            raise ProviderFailure(422, "Laya rechazó el tamaño o el formato de las preguntas o del CV")
        if response.status_code != 200:
            raise ProviderFailure(503, "El servidor de Laya no está disponible o su autenticación no es válida")
        try:
            result = response.json()
            if not isinstance(result, dict) or not isinstance(result.get("answers"), dict):
                raise ValueError("answers")
            usage = result.get("usage", {})
            if not isinstance(usage, dict):
                raise ValueError("usage")
            if usage.get("truncated") or usage.get("state_tokens_dropped", 0) or usage.get("truncated_questions"):
                raise ProviderFailure(422, "Laya recortó el CV o sus preguntas; revisa el documento manualmente")
            answers = {}
            for q in questions:
                raw = result["answers"][q.key]
                if q.type is QuestionType.CHOICE:
                    choice = raw["choice"]
                    if choice not in q.criteria:
                        raise ValueError("choice")
                    # confidence es entropía normalizada en Laya, no probabilidad de la respuesta.
                    probability = raw.get("answer_confidence")
                    if probability is None:
                        probability = raw["probabilities"][choice]
                    answers[q.key] = Answer(choice=choice, probability=_number(probability, 1))
                else:
                    upper = score_levels[q.key] - 1
                    answers[q.key] = Answer(score=round(_number(raw["score"], upper) / upper, 4))
            return Decision(answers=answers, model_version=self.model_version())
        except (KeyError, TypeError, ValueError, ZeroDivisionError):
            raise ProviderFailure(502, "Laya devolvió una respuesta incompleta o inválida") from None


def _number(value: object, upper: int) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("number")
    if not 0 <= value <= upper:
        raise ValueError("range")
    number = float(value)
    if not math.isfinite(number):
        raise ValueError("range")
    return number
