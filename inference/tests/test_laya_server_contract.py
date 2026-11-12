"""Prueba opcional contra el código real del servidor Laya, sin cargar checkpoints."""

import json
import os
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient

from app.adapters.laya import LayaProvider, LayaSettings
from app.domain.models import DecisionState, Question, QuestionType


def test_contrato_con_servidor_laya_real_y_router_sintetico(monkeypatch):
    source = os.getenv("LAYA_SOURCE_DIR")
    if not source:
        pytest.skip("Configura LAYA_SOURCE_DIR para validar contra el checkout real de Laya")
    monkeypatch.syspath_prepend(str(Path(source).resolve()))
    monkeypatch.delenv("LAYA_API_KEY", raising=False)
    from laya.serve import create_app

    class Router:
        loaded = []

        def predict(self, state, questions, model=None, **kwargs):
            assert model == "multilingual"
            assert kwargs["max_len"] == 8192
            assert state["cv_text"] == "CV sintético con Java"
            assert questions["q1"]["criteria"] == {"Sí": "Java", "No": "Sin evidencia"}
            return {"model": "laya-rl-agent", "answers": {
                "q1": {"type": "choice", "choice": "Sí", "probabilities": {"Sí": 0.9, "No": 0.1},
                       "answer_confidence": 0.9, "confidence": 0.4}},
                "usage": {"input_tokens": 20, "output_tokens": 0, "truncated": False}}

    with TestClient(create_app(router=Router())) as server:
        def forward(req):
            res = server.post(req.url.path, json=json.loads(req.content))
            return httpx.Response(res.status_code, json=res.json())

        provider = LayaProvider(LayaSettings(base_url="http://laya.test", model_version="laya@prueba"),
                                transport=httpx.MockTransport(forward))
        result = provider.decide(DecisionState("CV sintético con Java", "Backend"),
                                 [Question("q1", QuestionType.CHOICE, "¿Conoce Java?", {"Sí": "Java", "No": "Sin evidencia"})])
    assert result.answers["q1"].choice == "Sí"
    assert result.answers["q1"].probability == 0.9
