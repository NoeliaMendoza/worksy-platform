"""Servicio de inferencia: POST /v1/decide, GET /healthz y GET /metrics."""

from __future__ import annotations

import time

from fastapi import FastAPI, Response
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, CollectorRegistry, Counter, Histogram, generate_latest

from app.adapters.baseline import BaselineProvider
from app.adapters.laya import LayaProvider
from app.api.schemas import AnswerOut, DecideRequest, DecideResponse
from app.domain.models import DecisionState, ProviderNotAvailable, Question, QuestionType
from app.domain.ports import DecisionProvider


def _problem(status: int, title: str, detail: str) -> JSONResponse:
    """Errores en formato ProblemDetail (RFC 7807), igual que la API Java."""
    return JSONResponse(
        status_code=status,
        content={"type": "about:blank", "title": title, "status": status, "detail": detail},
        media_type="application/problem+json",
    )


def create_app(providers: list[DecisionProvider] | None = None) -> FastAPI:
    registry_providers = {p.name: p for p in (providers or [BaselineProvider(), LayaProvider()])}

    metrics = CollectorRegistry()
    decisions = Counter(
        "inference_decisions_total", "Decisiones por proveedor, versión y resultado",
        ["provider", "model_version", "outcome"], registry=metrics,
    )
    latency = Histogram(
        "inference_decision_seconds", "Latencia de decisión por proveedor y versión",
        ["provider", "model_version"], registry=metrics,
    )

    app = FastAPI(title="Servicio de inferencia", version="0.1.0")

    @app.get("/healthz")
    def healthz() -> dict:
        return {"status": "ok", "providers": sorted(registry_providers)}

    @app.get("/metrics")
    def prometheus() -> Response:
        return Response(generate_latest(metrics), media_type=CONTENT_TYPE_LATEST)

    @app.post("/v1/decide", response_model=DecideResponse)
    def decide(body: DecideRequest, response: Response):
        provider = registry_providers.get(body.provider)
        if provider is None:
            return _problem(400, "Proveedor desconocido",
                            f"Proveedores disponibles: {', '.join(sorted(registry_providers))}")

        version = provider.model_version()
        state = DecisionState(cv_text=body.state.cv_text, job_description=body.state.job_description)
        questions = [
            Question(key=k, type=QuestionType(q.type), instructions=q.instructions, criteria=q.criteria)
            for k, q in body.questions.items()
        ]

        start = time.perf_counter()
        try:
            decision = provider.decide(state, questions)
        except ProviderNotAvailable as exc:
            decisions.labels(provider.name, version, "unavailable").inc()
            return _problem(501, "Proveedor no disponible", str(exc))
        elapsed = time.perf_counter() - start

        latency.labels(provider.name, decision.model_version).observe(elapsed)
        decisions.labels(provider.name, decision.model_version, "ok").inc()
        response.headers["X-Model-Version"] = decision.model_version
        return DecideResponse(
            answers={k: AnswerOut(choice=a.choice, probability=a.probability, score=a.score)
                     for k, a in decision.answers.items()},
            model_version=decision.model_version,
            latency_ms=round(elapsed * 1000),
        )

    return app


app = create_app()
