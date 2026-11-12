package com.plataformaempleo.worker.screening.domain.model;

import java.util.Map;

/**
 * Respuesta del proveedor de decisión (servicio de inferencia): una opción y su probabilidad por criterio.
 *
 * @param answers      clave del criterio → respuesta
 * @param modelVersion versión del modelo que respondió (cabecera X-Model-Version)
 * @param latencyMs    lo que tardó el proveedor
 */
public record Decision(Map<String, Answer> answers, String modelVersion, int latencyMs) {

    public record Answer(String choice, double probability) {
    }

    public Decision {
        answers = Map.copyOf(answers);
    }
}
