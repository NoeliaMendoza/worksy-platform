"""Contrato del adaptador HTTP de Laya con datos sintéticos; no necesita pesos."""

import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.adapters.laya import LayaProvider, LayaSettings
from app.domain.models import Question, QuestionType
from app.main import create_app


def settings(**overrides):
    return LayaSettings(**{"base_url": "http://laya.test", "model_version": "laya-ml@revision-prueba",
                           "api_key": "clave-sintetica", **overrides})


def questions():
    return [Question("java", QuestionType.CHOICE, "¿Conoce Java?", {"Sí": "Java", "No": "Sin evidencia"}),
            Question("match", QuestionType.SCORE, "Compatibilidad con la vacante")]


def payload():
    return {"model": "laya-rl-agent", "answers": {
        "java": {"type": "choice", "choice": "Sí", "probabilities": {"Sí": 0.9, "No": 0.1},
                 "answer_confidence": 0.85, "confidence": 0.4},
        "match": {"type": "score", "score": 3.0}},
        "usage": {"truncated": False, "state_tokens_dropped": 0, "truncated_questions": []}}


def client_for(handler, **overrides):
    provider = LayaProvider(settings(**overrides), transport=httpx.MockTransport(handler))
    return TestClient(create_app([provider]))


def request_body():
    return {"provider": "laya", "state": {"cv_text": "CV sintético con Java.", "job_description": "Backend"},
            "questions": {q.key: {"type": q.type.value, "instructions": q.instructions, "criteria": q.criteria}
                          for q in questions()}}


def test_traduce_contrato_y_usa_probabilidad_de_respuesta_no_entropia():
    seen = []

    def handler(req):
        seen.append(json.loads(req.content))
        assert str(req.url) == "http://laya.test/v1/systemone"
        assert req.headers["Authorization"] == "Bearer clave-sintetica"
        return httpx.Response(200, json=payload())

    with client_for(handler) as client:
        res = client.post("/v1/decide", json=request_body())
        assert res.status_code == 200
        body = res.json()
        assert body["model_version"] == "laya-ml@revision-prueba"
        assert res.headers["X-Model-Version"] == body["model_version"]
        assert body["answers"]["java"]["probability"] == 0.85
        assert body["answers"]["match"]["score"] == 0.75
        metrics = client.get("/metrics").text
        assert 'outcome="ok",provider="laya"' in metrics
    assert seen[0]["state"] == request_body()["state"]
    assert seen[0]["model"] == "multilingual"
    assert seen[0]["max_len"] == 8192
    assert seen[0]["questions"]["java"]["criteria"] == {"Sí": "Java", "No": "Sin evidencia"}
    assert isinstance(seen[0]["questions"]["match"]["criteria"], list)
    assert len(seen[0]["questions"]["match"]["criteria"]) == 5


def test_modo_estricto_sin_answer_confidence_usa_probabilities():
    raw = payload()
    del raw["answers"]["java"]["answer_confidence"]
    with client_for(lambda req: httpx.Response(200, json=raw)) as client:
        res = client.post("/v1/decide", json=request_body())
    assert res.json()["answers"]["java"]["probability"] == 0.9


def test_escala_personalizada_se_normaliza_segun_sus_niveles():
    raw = payload()
    raw["answers"]["match"]["score"] = 1.5
    body = request_body()
    body["questions"]["match"]["criteria"] = {"bajo": "Bajo", "medio": "Medio", "alto": "Alto"}
    with client_for(lambda req: httpx.Response(200, json=raw)) as client:
        res = client.post("/v1/decide", json=body)
    assert res.json()["answers"]["match"]["score"] == 0.75


@pytest.mark.parametrize("levels", [1, 11])
def test_escala_invalida_se_rechaza_antes_de_enviar_el_cv(levels):
    calls = []

    def handler(req):
        calls.append(req)
        return httpx.Response(200, json=payload())

    body = request_body()
    body["questions"]["match"]["criteria"] = {str(i): str(i) for i in range(levels)}
    with client_for(handler) as client:
        assert client.post("/v1/decide", json=body).status_code == 422
    assert calls == []


@pytest.mark.parametrize("raw", [
    {}, {"answers": []}, {"answers": {"java": None}},
    {"answers": {"java": {"choice": "Inventada"}}},
    {"answers": {"java": {"choice": "Sí", "answer_confidence": 2}}},
    {"answers": {"java": {"choice": "Sí", "answer_confidence": True}}},
    {"answers": {"java": {"choice": "Sí", "confidence": 0.9}}},
])
def test_respuesta_incompleta_o_invalida_no_fabrica_resultados(raw):
    with client_for(lambda req: httpx.Response(200, json=raw)) as client:
        res = client.post("/v1/decide", json=request_body())
    assert res.status_code == 502
    assert "answers" not in res.json()


@pytest.mark.parametrize("score", [-1, 5, True, "3"])
def test_score_fuera_de_escala_se_rechaza(score):
    raw = payload()
    raw["answers"]["match"]["score"] = score
    with client_for(lambda req: httpx.Response(200, json=raw)) as client:
        assert client.post("/v1/decide", json=request_body()).status_code == 502


@pytest.mark.parametrize("usage", [{"truncated": True}, {"state_tokens_dropped": 1}, {"truncated_questions": ["java"]}])
def test_cv_o_preguntas_truncadas_no_pasan_por_un_analisis_completo(usage):
    raw = payload()
    raw["usage"] = usage
    with client_for(lambda req: httpx.Response(200, json=raw)) as client:
        res = client.post("/v1/decide", json=request_body())
    assert res.status_code == 422
    assert "recortó" in res.json()["detail"]


@pytest.mark.parametrize("status,expected", [(400, 422), (413, 422), (422, 422), (401, 503), (403, 503), (500, 503), (503, 503)])
def test_fallos_del_servidor_son_seguros_y_no_se_convierten_en_baseline(status, expected):
    with client_for(lambda req: httpx.Response(status, text="Texto privado y clave-sintetica")) as client:
        res = client.post("/v1/decide", json=request_body())
        metrics = client.get("/metrics").text
    assert res.status_code == expected
    assert "privado" not in res.text and "clave-sintetica" not in res.text
    assert 'outcome="error",provider="laya"' in metrics
    assert "baseline" not in res.text


def test_timeout_y_redirecciones_no_filtran_el_cv():
    def timeout(req):
        raise httpx.ReadTimeout("Texto privado", request=req)

    with client_for(timeout) as client:
        res = client.post("/v1/decide", json=request_body())
    assert res.status_code == 503
    assert "privado" not in res.text
    calls = []

    def redirect(req):
        calls.append(str(req.url))
        return httpx.Response(307, headers={"Location": "http://otro.test"})

    with client_for(redirect) as client:
        assert client.post("/v1/decide", json=request_body()).status_code == 503
    assert calls == ["http://laya.test/v1/systemone"]


@pytest.mark.parametrize("overrides", [
    {"base_url": "ftp://laya.test"}, {"base_url": "http://user:password@laya.test"},
    {"model_version": ""}, {"model_version": "x" * 65},
    {"timeout_seconds": 0}, {"timeout_seconds": float("nan")}, {"max_len": 8193},
])
def test_configuracion_invalida_falla_antes_de_enviar_datos(overrides):
    with pytest.raises(ValueError):
        settings(**overrides)


def test_configuracion_se_lee_del_entorno(monkeypatch):
    monkeypatch.setenv("LAYA_BASE_URL", "http://laya.test/")
    monkeypatch.setenv("LAYA_MODEL_VERSION", "revision-local")
    config = LayaSettings.from_env()
    assert config.base_url == "http://laya.test"
    assert config.model_version == "revision-local"


def test_sin_configuracion_se_mantiene_no_disponible():
    with TestClient(create_app([LayaProvider(LayaSettings())])) as client:
        assert client.post("/v1/decide", json=request_body()).status_code == 501
