"""Línea base: similitud coseno de bolsa de palabras entre el CV y cada pregunta.

Es deliberadamente simple y determinista. Sirve como referencia en los experimentos y
para probar el flujo completo sin depender de un modelo pesado.
"""

from __future__ import annotations

import math
import re
import unicodedata
from collections import Counter

from app.domain.models import Answer, Decision, DecisionState, Question, QuestionType

_TOKEN = re.compile(r"[a-z0-9+#]{2,}")

_STOPWORDS = frozenset(
    """
    de la el los las un una unos unas y o u en con por para del al que se su sus es son
    como mas pero sin sobre entre este esta estos estas lo le les ya muy tambien
    the and or of to in with for on at by an is are be as from that this it
    """.split()
)

# Temperatura del softmax para convertir similitudes en probabilidades (preguntas de 3+ opciones).
_TEMPERATURE = 8.0
# Preguntas binarias: cobertura de términos a partir de la cual se responde la opción afirmativa.
_COVERAGE_THRESHOLD = 0.3
_SLOPE = 12.0


def _normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text.lower())
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def tokenize(text: str) -> Counter[str]:
    tokens = _TOKEN.findall(_normalize(text or ""))
    return Counter(t for t in tokens if t not in _STOPWORDS)


def cosine(a: Counter[str], b: Counter[str]) -> float:
    if not a or not b:
        return 0.0
    dot = sum(a[t] * b[t] for t in a.keys() & b.keys())
    norm = math.sqrt(sum(v * v for v in a.values())) * math.sqrt(sum(v * v for v in b.values()))
    return dot / norm if norm else 0.0


def _softmax(values: list[float]) -> list[float]:
    m = max(values)
    exps = [math.exp((v - m) * _TEMPERATURE) for v in values]
    total = sum(exps)
    return [e / total for e in exps]


class BaselineProvider:
    name = "baseline"
    _VERSION = "baseline-bow-1"

    def model_version(self) -> str:
        return self._VERSION

    def decide(self, state: DecisionState, questions: list[Question]) -> Decision:
        cv = tokenize(state.cv_text)
        answers: dict[str, Answer] = {}
        for q in questions:
            if q.type is QuestionType.CHOICE:
                answers[q.key] = self._choice(cv, q)
            else:
                answers[q.key] = self._score(cv, state, q)
        return Decision(answers=answers, model_version=self._VERSION)

    @staticmethod
    def _choice(cv: Counter[str], q: Question) -> Answer:
        options = list(q.criteria.keys())
        if len(options) == 2:
            # Convención binaria: la PRIMERA opción es la afirmativa ("yes").
            # La similitud no puede detectar ausencia de evidencia, así que se mide qué parte de
            # los términos de la pregunta y del criterio afirmativo aparecen en el CV.
            terms = set(tokenize(f"{q.instructions} {q.criteria[options[0]]}"))
            coverage = len(terms & cv.keys()) / len(terms) if terms else 0.0
            p_yes = 1.0 / (1.0 + math.exp(-_SLOPE * (coverage - _COVERAGE_THRESHOLD)))
            if p_yes >= 0.5:
                return Answer(choice=options[0], probability=round(p_yes, 4))
            return Answer(choice=options[1], probability=round(1.0 - p_yes, 4))

        sims = [cosine(cv, tokenize(f"{q.instructions} {opt} {q.criteria[opt]}")) for opt in options]
        probs = _softmax(sims)
        best = max(range(len(options)), key=lambda i: probs[i])
        return Answer(choice=options[best], probability=round(probs[best], 4))

    @staticmethod
    def _score(cv: Counter[str], state: DecisionState, q: Question) -> Answer:
        target = tokenize(f"{state.job_description} {q.instructions}")
        return Answer(score=round(min(1.0, max(0.0, cosine(cv, target))), 4))
