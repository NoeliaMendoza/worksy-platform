"""Pruebas del evaluador con resultados controlados; no necesitan pesos ni servicios."""
import copy
import json
import tempfile
import unittest
from unittest import mock
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path

import evaluate as ev


DATA = {"schema_version": 1, "name": "synthetic-test", "job_description": "Backend",
        "criteria": [{"id": "java", "label": "Java", "question": "¿Declara experiencia con Java?"}],
        "cases": [{"id": "absent", "category": "test", "cv_text": "CV SINTÉTICO. Atención al público.", "expected": {"java": False}},
                  {"id": "present", "category": "test", "cv_text": "CV SINTÉTICO. Trabajo con Java.", "expected": {"java": True}}]}


def response(choice=ev.YES, probability=0.9, version="synthetic-1"):
    return {"model_version": version, "answers": {"java": {"choice": choice, "probability": probability}}}


class EvaluationTests(unittest.TestCase):
    def test_cli_works_when_script_is_mounted_at_a_shallow_container_path(self):
        report = ev.evaluate(DATA, ["baseline"], "http://inference", warmup=0, call=lambda *args: response())
        with tempfile.TemporaryDirectory() as folder:
            data_path = Path(folder) / "data.json"
            data_path.write_text(json.dumps(DATA), encoding="utf-8")
            with mock.patch.object(ev, "HERE", Path("/evaluation")), mock.patch.object(ev, "evaluate", return_value=report):
                with redirect_stdout(StringIO()):
                    code = ev.main(["--providers", "baseline", "--dataset", str(data_path), "--output-root", folder])
            self.assertEqual(code, 0)
            self.assertEqual(len(list(Path(folder).glob("evaluation-*/report.json"))), 1)

    def test_known_confusion_brier_and_failures_do_not_inflate_accuracy(self):
        rows = []
        for gold, prediction, confidence in [(True, True, .8), (False, True, .9), (True, False, .75), (False, False, .6)]:
            rows.append({"expected": gold, "predicted": prediction, "confidence": confidence,
                         "p_yes": confidence if prediction else 1 - confidence, "error": None})
        rows.append({"error": "http_503"})
        q = ev.quality(rows)
        self.assertEqual(q["confusion"], {"tp": 1, "tn": 1, "fp": 1, "fn": 1})
        self.assertEqual(q["accuracy_on_valid"], .5)
        self.assertEqual(q["coverage"], .8)
        self.assertEqual(q["correct_over_planned"], .4)
        self.assertEqual(q["balanced_accuracy"], .5)
        self.assertEqual(q["f1"], .5)
        self.assertEqual(q["brier"], .393125)
        self.assertEqual(q["high_confidence_errors"], 1)

    def test_no_valid_predictions_produces_no_accuracy(self):
        q = ev.quality([{"error": "http_503"}])
        self.assertIsNone(q["accuracy_on_valid"])
        self.assertIsNone(q["precision"])
        self.assertIsNone(q["brier"])
        self.assertEqual(q["coverage"], 0)

    def test_request_uses_worker_binary_contract_without_gold_labels(self):
        body = ev.request_body(DATA, DATA["cases"][0], "baseline")
        self.assertNotIn("expected", json.dumps(body))
        self.assertEqual(body["questions"]["java"]["criteria"], {"Sí": "Java", "No": "Sin evidencia en el CV"})
        self.assertEqual(body["questions"]["java"]["instructions"], DATA["criteria"][0]["question"])

    def test_rejects_missing_answers_invalid_choices_and_probabilities(self):
        invalid = [{}, {"model_version": "x", "answers": {}}, response("maybe"), response([]), response({}), response(probability=True),
                   response(probability=float("nan")), response(probability=float("inf")), response(probability=1.1)]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ev.EvaluationError):
                ev.validate_response(value, ["java"])

    def test_measured_http_failure_has_no_synthetic_negative_predictions(self):
        count = 0

        def call(url, body, timeout):
            nonlocal count
            count += 1
            if count == 2:
                raise ev.EvaluationError("http_503")
            return response()

        report = ev.evaluate(DATA, ["laya"], "http://inference", call=call)
        summary = report["providers"]["laya"]
        self.assertFalse(report["comparison_complete"])
        self.assertEqual(summary["requests"], {"attempted": 2, "valid": 1, "failed": 1})
        self.assertEqual(summary["quality"]["accuracy_on_valid"], 1)
        self.assertEqual(summary["quality"]["correct_over_planned"], .5)
        self.assertIsNone(report["answers"][0]["predicted"])
        self.assertEqual(summary["latency_all_requests"]["samples"], 2)
        self.assertEqual(summary["latency_valid_requests"]["samples"], 1)

    def test_unavailable_provider_stays_unmeasured_without_fallback(self):
        seen = []

        def call(url, body, timeout):
            seen.append(body["provider"])
            if body["provider"] == "laya":
                raise ev.EvaluationError("http_501")
            return response()

        report = ev.evaluate(DATA, ["baseline", "laya"], "http://inference", call=call)
        laya = report["providers"]["laya"]
        self.assertEqual(seen.count("laya"), 1)
        self.assertEqual(laya["requests"]["attempted"], 0)
        self.assertIsNone(laya["quality"]["accuracy_on_valid"])
        self.assertFalse(report["comparison_complete"])
        self.assertIn("sin medición", ev.markdown(report))

    def test_repetitions_detect_unstable_answers_and_mixed_model_versions(self):
        data = copy.deepcopy(DATA)
        data["cases"] = data["cases"][:1]
        answers = iter([response(version="v1"), response(ev.NO, version="v2")])
        report = ev.evaluate(data, ["laya"], "http://inference", repeats=2, warmup=0, call=lambda *args: next(answers))
        summary = report["providers"]["laya"]
        self.assertEqual(summary["unstable_case_criteria"], 1)
        self.assertFalse(summary["version_consistent"])
        self.assertFalse(report["comparison_complete"])

    def test_binary_no_probability_is_converted_to_yes_probability(self):
        report = ev.evaluate(DATA, ["baseline"], "http://inference", warmup=0, call=lambda *args: response(ev.NO, .8))
        self.assertAlmostEqual(report["answers"][0]["p_yes"], .2)

    def test_validates_dataset_duplicates_and_boolean_labels(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "data.json"
            path.write_text(json.dumps(DATA), encoding="utf-8")
            data, digest = ev.load_dataset(path)
            self.assertEqual(len(digest), 64)
            for modify in (lambda d: d["cases"].append(d["cases"][0]),
                           lambda d: d["cases"][0]["expected"].update(java=1),
                           lambda d: d["cases"][0].update(cv_text="Texto sin marca sintética")):
                changed = copy.deepcopy(data)
                modify(changed)
                path.write_text(json.dumps(changed), encoding="utf-8")
                with self.assertRaises(ValueError):
                    ev.load_dataset(path)

    def test_default_dataset_is_valid_and_reports_do_not_copy_cv_text(self):
        data, _ = ev.load_dataset(ev.HERE / "synthetic-cv-v1.json")
        self.assertEqual(len(data["cases"]), 24)
        report = ev.evaluate(DATA, ["baseline"], "http://inference", warmup=0, call=lambda *args: response())
        with tempfile.TemporaryDirectory() as folder:
            ev.write_report(report, Path(folder) / "result")
            saved = json.loads((Path(folder) / "result" / "report.json").read_text(encoding="utf-8"))
            self.assertNotIn("cv_text", json.dumps(saved))
            self.assertTrue((Path(folder) / "result" / "answers.csv").exists())
            self.assertIn("Respuestas incorrectas", (Path(folder) / "result" / "report.md").read_text(encoding="utf-8"))

    def test_endpoint_disallows_embedded_credentials_and_query(self):
        for url in ("file:///secret", "http://user:pass@localhost", "http://localhost?token=secret", "http://localhost/#x"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                ev.endpoint(url)

    def test_latency_counts_requests_once_and_preserves_tail(self):
        self.assertEqual(ev.latency([1, 3, 2, 100])["p95_ms"], 100)
        self.assertEqual(ev.latency([1, 3, 2, 100])["median_ms"], 2.5)
        self.assertIsNone(ev.latency([])["p95_ms"])

    def test_paired_comparison_excludes_cases_only_one_provider_answered(self):
        def row(provider, case_id, prediction, error=None):
            return {"provider": provider, "repetition": 1, "case_id": case_id, "criterion": "java",
                    "expected": True, "predicted": prediction, "error": error, "confidence": .8, "p_yes": .8}
        rows = [row("baseline", "a", True), row("baseline", "b", False),
                row("laya", "a", False), row("laya", "b", None, "http_503")]
        paired = ev.paired_comparison(rows, ["baseline", "laya"], 2)
        self.assertEqual(paired["valid_pairs"], 1)
        self.assertEqual(paired["coverage"], .5)
        self.assertEqual(paired["providers"]["baseline"]["accuracy_on_valid"], 1)
        self.assertEqual(paired["providers"]["laya"]["accuracy_on_valid"], 0)
        self.assertEqual(paired["only_correct"], {"baseline": 1, "laya": 0})
        self.assertIsNone(ev.paired_comparison(rows, ["baseline"], 2))


if __name__ == "__main__":
    unittest.main()
