"""Adaptador de Laya: pendiente.

El documento de diseño exige verificar la API real de Laya en el repositorio instalado antes
de escribir este adaptador. Hasta entonces responde que el proveedor no está disponible, para
no inventar un contrato.
"""

from __future__ import annotations

from app.domain.models import Decision, DecisionState, ProviderNotAvailable, Question


class LayaProvider:
    name = "laya"

    def model_version(self) -> str:
        return "laya-pendiente"

    def decide(self, state: DecisionState, questions: list[Question]) -> Decision:
        raise ProviderNotAvailable(
            "El adaptador de Laya aún no está implementado: falta verificar su API real."
        )
