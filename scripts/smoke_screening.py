"""Prueba HTTP del flujo completo usando exclusivamente cuentas y un PDF sintéticos.

Requiere httpx (incluido en la imagen de inferencia). No imprime contraseñas ni tokens.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import secrets
import time
import uuid

import httpx


class SmokeFailure(Exception):
    pass


def synthetic_pdf() -> bytes:
    lines = ["CV SINTETICO - prueba de integracion, no representa a una persona real.",
             "Experiencia profesional: desarrollo backend con Java, Spring Boot y PostgreSQL.",
             "Tres anos creando APIs REST con Java y pruebas automatizadas."]
    content = "BT /F1 11 Tf 40 760 Td " + " 0 -18 Td ".join(f"({line}) Tj" for line in lines) + " ET"
    stream = content.encode("ascii")
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>",
               b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
               b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
               b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
               b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream"]
    pdf = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(pdf))
        pdf.extend(f"{index} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = len(pdf)
    pdf.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode())
    pdf.extend(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return bytes(pdf)


def run(api_url: str, provider: str, timeout: float) -> dict:
    started = time.monotonic()
    with httpx.Client(base_url=api_url.rstrip("/") + "/api/v1/", timeout=15, trust_env=False) as client:
        def call(method, path, token=None, expected=200, **kwargs):
            headers = kwargs.pop("headers", {})
            if token:
                headers["Authorization"] = f"Bearer {token}"
            res = client.request(method, path, headers=headers, **kwargs)
            if res.status_code != expected:
                try:
                    code = res.json().get("code", f"http.{res.status_code}")
                except (ValueError, AttributeError):
                    code = f"http.{res.status_code}"
                raise SmokeFailure(f"{method} {path}: esperaba {expected}, recibió {res.status_code} ({code})")
            return None if expected == 204 else res.json()

        def login(email, password):
            return call("POST", "auth/login", json={"email": email, "password": password})["accessToken"]

        deadline = time.monotonic() + timeout
        while True:
            try:
                health = client.get(api_url.rstrip("/") + "/actuator/health")
                if health.status_code == 200:
                    break
            except httpx.RequestError:
                pass
            if time.monotonic() >= deadline:
                raise SmokeFailure("La API no estuvo lista dentro del tiempo de espera")
            time.sleep(1)

        run_id = uuid.uuid4().hex
        password = "Sintetico-" + secrets.token_urlsafe(18)
        admin_email = f"empresa-{run_id}@ejemplo.test"
        candidate_email = f"candidato-{run_id}@ejemplo.test"
        call("POST", "companies", expected=201,
             json={"companyName": f"Empresa sintetica {run_id[:8]}", "email": admin_email, "password": password})
        admin = login(admin_email, password)
        call("POST", "auth/register", expected=201, json={"email": candidate_email, "password": password})
        candidate = login(candidate_email, password)
        vacancy = call("POST", "companies/me/vacancies", admin, expected=201, json={
            "title": "Backend Java - prueba sintetica", "description": "Desarrollo backend con Java y Spring Boot.",
            "location": "Remoto", "modality": "REMOTO", "criteria": [
                {"label": "Java", "question": "¿Tiene experiencia profesional con Java?", "kind": "YES_NO"}]})
        vacancy_id = vacancy["id"]
        base = f"companies/me/vacancies/{vacancy_id}"
        call("POST", base + "/publish", admin)
        pdf = synthetic_pdf()
        cv = call("POST", "candidates/me/cv", candidate, expected=201,
                  files={"file": ("cv-sintetico.pdf", pdf, "application/pdf")})
        call("PUT", "candidates/me/consents", candidate, json={"shareWithRecruiters": True, "githubLink": False})
        application = call("POST", f"vacancies/{vacancy_id}/applications", candidate, expected=201, json={"cvId": cv["id"]})
        application_id = application["id"]
        cv_link = call("GET", base + f"/applications/{application_id}/cv", admin)
        # El enlace presignado se usa sin el token del reclutador y no se imprime.
        downloaded = client.get(cv_link["url"])
        if downloaded.status_code != 200 or hashlib.sha256(downloaded.content).digest() != hashlib.sha256(pdf).digest():
            raise SmokeFailure("El enlace temporal no devolvió el PDF original")

        key = str(uuid.uuid4())
        batch = call("POST", base + "/screenings", admin, expected=202, json={}, headers={"Idempotency-Key": key})
        if len(batch["requests"]) != 1 or batch["requests"][0]["provider"] != provider:
            raise SmokeFailure("La solicitud no utilizó el proveedor esperado")
        replay = call("POST", base + "/screenings", admin, expected=202, json={}, headers={"Idempotency-Key": key})
        if not replay["replayed"] or replay["requests"][0]["id"] != batch["requests"][0]["id"]:
            raise SmokeFailure("El reintento idempotente no conservó la solicitud original")
        analysis_started = time.monotonic()
        deadline = analysis_started + timeout
        while True:
            ranking = call("GET", base + "/ranking", admin)
            if ranking["failed"]:
                raise SmokeFailure("El worker marcó el análisis FAILED; revisa sus registros y los de inferencia")
            if ranking["candidates"]:
                result = ranking["candidates"][0]
                break
            if time.monotonic() >= deadline:
                raise SmokeFailure("El análisis no terminó dentro del tiempo de espera")
            time.sleep(1)
        analysis_seconds = round(time.monotonic() - analysis_started, 3)
        if result["applicationId"] != application_id or not result["answers"] or not result["modelVersion"]:
            raise SmokeFailure("El ranking no contiene el análisis completo de la postulación")
        if not 0 <= result["overallScore"] <= 1 or not 0 <= result["confidence"] <= 1:
            raise SmokeFailure("El resultado contiene puntuaciones fuera de rango")
        decision_path = base + f"/ranking/{application_id}/decision"
        call("PUT", decision_path, admin, expected=204, json={"decision": "preseleccionado"})
        persisted = call("GET", base + "/ranking", admin)["candidates"][0]
        if persisted["decision"] != "preseleccionado":
            raise SmokeFailure("La decisión humana no se recuperó desde la API")
        call("PUT", "candidates/me/consents", candidate, json={"shareWithRecruiters": False, "githubLink": False})
        if call("GET", base + "/ranking", admin)["candidates"]:
            raise SmokeFailure("El ranking expuso el resultado después de retirar el consentimiento")
        call("GET", base + f"/applications/{application_id}/cv", admin, expected=403)
        # Solo se limpian los datos creados por esta ejecución.
        call("DELETE", f"candidates/me/applications/{application_id}", candidate, expected=204)
        call("DELETE", f"candidates/me/cv/{cv['id']}", candidate, expected=204)
        call("POST", base + "/close", admin)
        return {"status": "ok", "provider": provider, "model_version": result["modelVersion"],
                "analysis_status": result["status"], "score": result["overallScore"],
                "confidence": result["confidence"], "answers": result["answers"],
                "analysis_seconds": analysis_seconds, "total_seconds": round(time.monotonic() - started, 3),
                "verified": ["pdf_upload_and_download", "idempotency", "worker_to_ranking", "persisted_decision", "consent_revocation"],
                "cleanup": "PDF y postulación eliminados; vacante cerrada. Las dos cuentas sintéticas permanecen."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--provider", choices=["baseline", "laya"], default="baseline")
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    if not math.isfinite(args.timeout) or args.timeout <= 0:
        parser.error("--timeout debe ser positivo")
    try:
        print(json.dumps(run(args.api_url, args.provider, args.timeout), ensure_ascii=False, indent=2))
    except (SmokeFailure, httpx.RequestError, KeyError, ValueError) as error:
        # Los errores HTTP no incluyen los cuerpos ni las cabeceras de autenticación.
        detail = str(error) if isinstance(error, SmokeFailure) else "Error de conexión o contrato inesperado"
        print(json.dumps({"status": "failed", "detail": detail}, ensure_ascii=False))
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
