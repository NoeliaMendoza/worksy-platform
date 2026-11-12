"""Pruebas de contrato de /v1/decide, /healthz y /metrics."""

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture
def client(monkeypatch):
    monkeypatch.delenv("LAYA_BASE_URL", raising=False)
    monkeypatch.delenv("LAYA_MODEL_VERSION", raising=False)
    return TestClient(create_app())


def _request(provider="baseline", **overrides):
    body = {
        "provider": provider,
        "state": {
            "cv_text": "Desarrollador sintético con experiencia en Java y Spring Boot.",
            "job_description": "Vacante backend Java.",
        },
        "questions": {
            "cumple_java": {
                "type": "choice",
                "instructions": "¿El CV demuestra experiencia con Java?",
                "criteria": {"yes": "experiencia comprobable", "no": "sin evidencia"},
            },
            "compatibilidad": {"type": "score", "instructions": "Compatibilidad general con la vacante"},
        },
    }
    body.update(overrides)
    return body


def test_openapi_usa_la_marca(client):
    assert client.get("/openapi.json").json()["info"]["title"] == "Worksy Inference"


def test_healthz(client):
    res = client.get("/healthz")
    assert res.status_code == 200
    assert res.json()["status"] == "ok"
    assert "baseline" in res.json()["providers"]


def test_decide_respeta_el_contrato(client):
    res = client.post("/v1/decide", json=_request())

    assert res.status_code == 200
    assert res.headers["X-Model-Version"] == "baseline-bow-1"
    body = res.json()
    assert body["model_version"] == "baseline-bow-1"
    assert isinstance(body["latency_ms"], int)
    assert body["answers"]["cumple_java"]["choice"] in {"yes", "no"}
    assert 0.0 <= body["answers"]["cumple_java"]["probability"] <= 1.0
    assert 0.0 <= body["answers"]["compatibilidad"]["score"] <= 1.0


def test_proveedor_desconocido_devuelve_problem_detail(client):
    res = client.post("/v1/decide", json=_request(provider="inexistente"))
    assert res.status_code == 400
    assert res.headers["content-type"].startswith("application/problem+json")
    assert res.json()["status"] == 400


def test_laya_sin_configuracion_no_disponible(client):
    res = client.post("/v1/decide", json=_request(provider="laya"))
    assert res.status_code == 501
    assert res.json()["title"] == "Proveedor no disponible"


def test_sin_preguntas_es_invalido(client):
    assert client.post("/v1/decide", json=_request(questions={})).status_code == 422


def test_choice_con_una_sola_opcion_es_invalido(client):
    questions = {"q": {"type": "choice", "instructions": "x", "criteria": {"yes": "y"}}}
    assert client.post("/v1/decide", json=_request(questions=questions)).status_code == 422


def test_campos_desconocidos_se_rechazan(client):
    assert client.post("/v1/decide", json=_request(extra="x")).status_code == 422


def test_metrics_cuenta_decisiones(client):
    client.post("/v1/decide", json=_request())
    res = client.get("/metrics")
    assert res.status_code == 200
    assert 'inference_decisions_total{model_version="baseline-bow-1",outcome="ok",provider="baseline"} 1.0' in res.text
