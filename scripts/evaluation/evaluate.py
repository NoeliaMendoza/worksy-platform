"""Evalúa el contrato de inferencia con CV sintéticos; solo biblioteca estándar."""
from __future__ import annotations

import argparse
import csv
import hashlib
import http.client
import json
import math
import platform
import statistics
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
YES, NO = "Sí", "No"


class EvaluationError(Exception):
    """Diagnóstico seguro: no contiene cuerpos de solicitudes o respuestas."""


def load_dataset(path):
    raw = path.read_bytes()
    data = json.loads(raw)
    if not isinstance(data, dict) or data.get("schema_version") != 1 or not data.get("name") or not data.get("job_description"):
        raise ValueError("Dataset sin versión, nombre o descripción de la vacante")
    criteria = data.get("criteria", [])
    keys = [c["id"] for c in criteria]
    if not keys or len(keys) != len(set(keys)) or len(keys) > 50:
        raise ValueError("Criterios vacíos, duplicados o superiores al contrato")
    if any(not c.get("label") or not c.get("question") for c in criteria):
        raise ValueError("Criterios sin etiqueta o pregunta")
    cases = data.get("cases", [])
    ids = [c["id"] for c in cases]
    if not ids or len(ids) != len(set(ids)):
        raise ValueError("Casos vacíos o duplicados")
    for case in cases:
        text = case.get("cv_text", "")
        if not case.get("category") or not any(marker in text for marker in ("CV SINTÉTICO", "SYNTHETIC CV")):
            raise ValueError("Cada caso debe declarar su categoría y contener una marca de CV sintético")
        if len(text) > 100_000:
            raise ValueError("El caso supera el límite de texto del worker; no debe enviarse directamente al modelo")
        if set(case["expected"]) != set(keys) or any(type(v) is not bool for v in case["expected"].values()):
            raise ValueError("Etiquetas ausentes o no booleanas")
    return data, hashlib.sha256(raw).hexdigest()


def request_body(data, case, provider):
    # Misma traducción YES_NO que HttpDecisionProvider.questions; etiquetas nunca se envían.
    return {"provider": provider, "state": {"cv_text": case["cv_text"], "job_description": data["job_description"]},
            "questions": {c["id"]: {"type": "choice", "instructions": c["question"],
                                      "criteria": {YES: c["label"], NO: "Sin evidencia en el CV"}}
                          for c in data["criteria"]}}


def endpoint(url):
    parsed = urlsplit(url)
    parsed.port  # Valida el puerto antes de empezar una ejecución.
    if (parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment):
        raise ValueError("La URL debe ser HTTP(S), sin credenciales, consulta ni fragmento")
    return parsed


def post_decide(url, body, timeout):
    target = endpoint(url)
    connection_type = http.client.HTTPSConnection if target.scheme == "https" else http.client.HTTPConnection
    connection = connection_type(target.hostname, target.port, timeout=timeout)
    try:
        # Sin proxies heredados, redirecciones ni reintentos que oculten fallos o alteren tiempos.
        connection.request("POST", target.path.rstrip("/") + "/v1/decide",
                           json.dumps(body, ensure_ascii=False).encode("utf-8"),
                           {"Content-Type": "application/json", "Accept": "application/json"})
        response = connection.getresponse()
        raw = response.read(2_000_001)
        if response.status != 200:
            raise EvaluationError(f"http_{response.status}")
        if len(raw) > 2_000_000:
            raise EvaluationError("response_too_large")
        try:
            return json.loads(raw)
        except (ValueError, UnicodeError):
            raise EvaluationError("invalid_json") from None
    except (OSError, http.client.HTTPException):
        raise EvaluationError("connection_or_timeout") from None
    finally:
        connection.close()


def validate_response(response, keys):
    if not isinstance(response, dict) or not isinstance(response.get("model_version"), str) or not response["model_version"].strip():
        raise EvaluationError("missing_model_version")
    answers = response.get("answers")
    if not isinstance(answers, dict) or set(answers) != set(keys):
        raise EvaluationError("incomplete_answers")
    for answer in answers.values():
        if not isinstance(answer, dict) or not isinstance(answer.get("choice"), str) or answer["choice"] not in {YES, NO}:
            raise EvaluationError("invalid_choice")
        probability = answer.get("probability")
        if type(probability) not in (int, float) or not math.isfinite(probability) or not 0 <= probability <= 1:
            raise EvaluationError("invalid_probability")
    return response


def ratio(numerator, denominator):
    return round(numerator / denominator, 6) if denominator else None


def quality(rows):
    valid = [r for r in rows if r["error"] is None]
    tp = sum(r["expected"] and r["predicted"] for r in valid)
    tn = sum(not r["expected"] and not r["predicted"] for r in valid)
    fp = sum(not r["expected"] and r["predicted"] for r in valid)
    fn = sum(r["expected"] and not r["predicted"] for r in valid)
    sensitivity, specificity = ratio(tp, tp + fn), ratio(tn, tn + fp)
    return {"planned_answers": len(rows), "valid_answers": len(valid), "coverage": ratio(len(valid), len(rows)),
            "accuracy_on_valid": ratio(tp + tn, len(valid)), "correct_over_planned": ratio(tp + tn, len(rows)),
            "confusion": {"tp": tp, "tn": tn, "fp": fp, "fn": fn},
            "precision": ratio(tp, tp + fp), "recall": sensitivity, "specificity": specificity,
            "f1": ratio(2 * tp, 2 * tp + fp + fn),
            "balanced_accuracy": round((sensitivity + specificity) / 2, 6)
            if sensitivity is not None and specificity is not None else None,
            "brier": round(statistics.mean((r["p_yes"] - int(r["expected"])) ** 2 for r in valid), 6) if valid else None,
            "high_confidence_errors": sum(r["confidence"] >= 0.8 and r["expected"] != r["predicted"] for r in valid)}


def latency(values):
    values = sorted(values)
    return {"samples": len(values), "median_ms": round(statistics.median(values), 3) if values else None,
            "p95_ms": round(values[max(0, math.ceil(0.95 * len(values)) - 1)], 3) if values else None,
            "max_ms": round(max(values), 3) if values else None}


def paired_comparison(rows, providers, planned):
    """Compara exactamente los mismos caso/repetición/criterio cuando ambos respondieron."""
    if len(providers) != 2:
        return None
    indexed = {p: {(r["repetition"], r["case_id"], r["criterion"]): r for r in rows
                   if r["provider"] == p and r["error"] is None} for p in providers}
    left, right = providers
    common = sorted(set(indexed[left]) & set(indexed[right]))
    left_only, right_only, both, neither = 0, 0, 0, 0
    for key in common:
        a, b = indexed[left][key], indexed[right][key]
        if a["expected"] != b["expected"]:
            raise ValueError("Etiquetas incompatibles entre proveedores")
        ca, cb = a["predicted"] == a["expected"], b["predicted"] == b["expected"]
        both += ca and cb
        neither += not ca and not cb
        left_only += ca and not cb
        right_only += cb and not ca
    return {"planned_pairs": planned, "valid_pairs": len(common), "coverage": ratio(len(common), planned),
            "providers": {p: quality([indexed[p][key] for key in common]) for p in providers},
            "both_correct": both, "both_wrong": neither,
            "only_correct": {left: left_only, right: right_only}}


def summarize(rows, requests, review_threshold):
    valid_requests = [r for r in requests if r["error"] is None]
    case_errors = [r for r in valid_requests if not r["exact_match"]]
    versions = sorted({r["model_version"] for r in valid_requests})
    predictions = {}
    for row in rows:
        if row["error"] is None:
            predictions.setdefault((row["case_id"], row["criterion"]), set()).add(row["predicted"])
    return {"quality": quality(rows), "requests": {"attempted": len(requests), "valid": len(valid_requests),
            "failed": len(requests) - len(valid_requests)}, "model_versions": versions,
            "version_consistent": len(versions) == 1,
            "by_criterion": {key: quality([r for r in rows if r["criterion"] == key]) for key in sorted({r["criterion"] for r in rows})},
            "by_category": {key: quality([r for r in rows if r["category"] == key]) for key in sorted({r["category"] for r in rows})},
            "exact_match_rate_on_valid_cases": ratio(sum(r["exact_match"] for r in valid_requests), len(valid_requests)),
            "cases_with_errors": len(case_errors),
            "review_fraction_on_valid_cases": ratio(sum(r["needs_review"] for r in valid_requests), len(valid_requests)),
            "error_cases_flagged_for_review": ratio(sum(r["needs_review"] for r in case_errors), len(case_errors)),
            "review_threshold": review_threshold,
            "unstable_case_criteria": sum(len(values) > 1 for values in predictions.values()),
            "latency_all_requests": latency([r["elapsed_ms"] for r in requests]),
            "latency_valid_requests": latency([r["elapsed_ms"] for r in valid_requests])}


def evaluate(data, providers, url, *, repeats=1, warmup=1, timeout=30, review_threshold=0.6, call=post_decide):
    keys = [c["id"] for c in data["criteria"]]
    rows, requests, warming, blocked = [], [], [], {}
    for provider in providers:
        for _ in range(warmup):
            start = time.perf_counter()
            error = None
            try:
                validate_response(call(url, request_body(data, data["cases"][0], provider), timeout), keys)
            except EvaluationError as exc:
                error = str(exc)
                blocked[provider] = error
            warming.append({"provider": provider, "error": error, "elapsed_ms": round((time.perf_counter() - start) * 1000, 3)})
            if error:
                break
    for repetition in range(repeats):
        for index, case in enumerate(data["cases"]):
            # Alterna el orden de proveedores para no ejecutar siempre uno primero.
            offset = (index + repetition) % len(providers)
            for provider in providers[offset:] + providers[:offset]:
                if provider in blocked:
                    continue
                response, error = None, None
                start = time.perf_counter()
                try:
                    response = validate_response(call(url, request_body(data, case, provider), timeout), keys)
                except EvaluationError as exc:
                    error = str(exc)
                elapsed = round((time.perf_counter() - start) * 1000, 3)
                correct, confidence = [], []
                for key in keys:
                    answer = response["answers"][key] if error is None else None
                    predicted = answer["choice"] == YES if answer else None
                    probability = answer["probability"] if answer else None
                    rows.append({"provider": provider, "repetition": repetition + 1, "case_id": case["id"],
                                 "category": case["category"], "characters": len(case["cv_text"]), "criterion": key,
                                 "expected": case["expected"][key], "predicted": predicted, "confidence": probability,
                                 "p_yes": probability if predicted else 1 - probability if answer else None,
                                 "model_version": response["model_version"] if error is None else None, "error": error})
                    if answer:
                        correct.append(predicted == case["expected"][key])
                        confidence.append(probability)
                requests.append({"provider": provider, "repetition": repetition + 1, "case_id": case["id"],
                                 "category": case["category"], "characters": len(case["cv_text"]), "elapsed_ms": elapsed,
                                 "model_version": response["model_version"] if error is None else None, "error": error,
                                 "exact_match": all(correct) if not error else None,
                                 "needs_review": statistics.mean(confidence) < review_threshold if confidence else None})
    summaries = {}
    for provider in providers:
        summaries[provider] = summarize([r for r in rows if r["provider"] == provider],
                                        [r for r in requests if r["provider"] == provider], review_threshold)
        summaries[provider]["warmup_error"] = blocked.get(provider)
        summaries[provider]["planned_requests"] = len(data["cases"]) * repeats
        planned = len(data["cases"]) * repeats * len(keys)
        q = summaries[provider]["quality"]
        q["planned_answers"] = planned
        q["coverage"] = ratio(q["valid_answers"], planned)
        q["correct_over_planned"] = ratio(q["confusion"]["tp"] + q["confusion"]["tn"], planned)
        summaries[provider]["complete"] = (not blocked.get(provider)
            and summaries[provider]["requests"]["valid"] == len(data["cases"]) * repeats
            and summaries[provider]["version_consistent"])
    return {"providers": summaries, "warmup": warming, "requests": requests, "answers": rows,
            "paired": paired_comparison(rows, providers, len(data["cases"]) * repeats * len(keys)),
            "comparison_complete": all(s["complete"] for s in summaries.values())}


def markdown(report):
    lines = ["# Evaluación sintética de inferencia", "", "Solo criterios explícitos en textos sintéticos; no mide desempeño sobre candidatos reales.", "",
             "| Proveedor | Respuestas válidas | Acierto en válidas | FP | FN | Mediana HTTP (ms) | p95 HTTP (ms) |",
             "|---|---:|---:|---:|---:|---:|---:|"]
    for provider, summary in report["providers"].items():
        q, times = summary["quality"], summary["latency_all_requests"]
        accuracy = f'{q["accuracy_on_valid"]:.1%}' if q["accuracy_on_valid"] is not None else "sin medición"
        lines.append(f'| {provider} | {q["valid_answers"]}/{q["planned_answers"]} | {accuracy} | {q["confusion"]["fp"]} | {q["confusion"]["fn"]} | {times["median_ms"]} | {times["p95_ms"]} |')
        if summary["warmup_error"]:
            lines.extend(["", f'Proveedor {provider} sin medición: falló el calentamiento ({summary["warmup_error"]}).'])
    lines.extend(["", f'Comparación completa: {report["comparison_complete"]}.', "",
                  "Los fallos se separan de las respuestas incorrectas; consultar cobertura y errores en report.json.",
                  "La latencia incluye HTTP e inferencia, excluye cola, extracción de PDF y warmup. p95 es descriptivo de esta muestra.",
                  "Brier y confianza son diagnósticos; no demuestran calibración ni validez para decidir contrataciones.",
                  "Las repeticiones reutilizan los mismos casos: no son muestras independientes ni amplían la variedad del conjunto.", ""])
    wrong = [r for r in report["answers"] if r["error"] is None and r["expected"] != r["predicted"]]
    if report.get("paired"):
        paired = report["paired"]
        lines.extend(["## Comparación sobre respuestas comunes", "",
                      f'Pares válidos: {paired["valid_pairs"]}/{paired["planned_pairs"]}. Solo se comparan estos mismos casos, criterios y repeticiones.', ""])
        for provider, q in paired["providers"].items():
            accuracy = f'{q["accuracy_on_valid"]:.1%}' if q["accuracy_on_valid"] is not None else "sin medición"
            lines.append(f'- {provider}: acierto {accuracy} sobre respuestas comunes.')
        lines.append("")
    lines.extend(["## Respuestas incorrectas", "", "| Proveedor | Repetición | Caso | Criterio | Esperado | Predicho | Confianza elegida |",
                  "|---|---:|---|---|---|---|---:|"])
    for row in wrong:
        lines.append(f'| {row["provider"]} | {row["repetition"]} | {row["case_id"]} | {row["criterion"]} | {row["expected"]} | {row["predicted"]} | {row["confidence"]:.4f} |')
    return "\n".join(lines) + "\n"


def write_report(report, output):
    output.mkdir(parents=True, exist_ok=False)
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    (output / "report.md").write_text(markdown(report), encoding="utf-8")
    for key in ("answers", "requests"):
        with (output / f"{key}.csv").open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(report[key][0]) if report[key] else ["provider", "error"])
            writer.writeheader()
            writer.writerows(report[key])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=HERE / "synthetic-cv-v1.json")
    parser.add_argument("--providers", nargs="+", choices=("baseline", "laya"), default=["baseline", "laya"])
    parser.add_argument("--url", default="http://localhost:8000")
    parser.add_argument("--repeats", type=int, default=1)
    parser.add_argument("--warmup", type=int, default=1)
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--review-threshold", type=float, default=0.6)
    parser.add_argument("--target-label", default="sin especificar; platform describe el ejecutor, no el servidor")
    parser.add_argument("--output-root", type=Path, default=HERE.parent.parent / "bench" / "results")
    args = parser.parse_args(argv)
    if (args.repeats < 1 or args.warmup < 0 or not math.isfinite(args.timeout) or args.timeout <= 0
            or not math.isfinite(args.review_threshold) or not 0 <= args.review_threshold <= 1
            or len(set(args.providers)) != len(args.providers)):
        parser.error("Repeticiones, warmup, tiempo, umbral o proveedores inválidos")
    try:
        endpoint(args.url)
        data, digest = load_dataset(args.dataset)
    except (OSError, ValueError, KeyError, TypeError):
        parser.error("Dataset o URL inválidos; revisa el contrato documentado")
    started = datetime.now(timezone.utc)
    report = evaluate(data, args.providers, args.url, repeats=args.repeats, warmup=args.warmup,
                      timeout=args.timeout, review_threshold=args.review_threshold)
    report["metadata"] = {"started_at_utc": started.isoformat(), "finished_at_utc": datetime.now(timezone.utc).isoformat(),
                          "dataset": data["name"], "dataset_sha256": digest, "cases": len(data["cases"]),
                          "criteria": len(data["criteria"]), "repeats": args.repeats, "warmup_per_provider": args.warmup,
                          "url": args.url, "timeout_seconds": args.timeout, "target_label": args.target_label,
                          "python": sys.version, "runner_platform": platform.platform(),
                          "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    output = args.output_root / ("evaluation-" + started.strftime("%Y%m%dT%H%M%S%fZ"))
    write_report(report, output)
    print(output / "report.md")
    for provider, summary in report["providers"].items():
        print(provider, json.dumps({"complete": summary["complete"], "quality": summary["quality"],
                                    "warmup_error": summary["warmup_error"]}, ensure_ascii=False))
    return 0 if report["comparison_complete"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
