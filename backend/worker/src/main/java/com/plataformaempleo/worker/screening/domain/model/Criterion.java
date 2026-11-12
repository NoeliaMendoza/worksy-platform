package com.plataformaempleo.worker.screening.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Criterio de la vacante tal como lo guardó la API. Las opciones van de la MÁS a la MENOS favorable
 * (en YES_NO: "Sí", "No"); el worker se apoya en ese orden para puntuar.
 */
public record Criterion(String key, String label, String question, Kind kind, List<String> options) {

    public enum Kind { YES_NO, CHOICE }

    public Criterion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(kind, "kind");
        options = List.copyOf(options);
        if (options.size() < 2) {
            throw new IllegalArgumentException("Un criterio necesita al menos dos opciones: " + key);
        }
    }

    /** 1 para la opción más favorable, 0 para la menos; proporcional en medio. Null si la opción no existe. */
    public Double favorability(String choice) {
        int index = options.indexOf(choice);
        if (index < 0) {
            return null;
        }
        return (double) (options.size() - 1 - index) / (options.size() - 1);
    }
}
