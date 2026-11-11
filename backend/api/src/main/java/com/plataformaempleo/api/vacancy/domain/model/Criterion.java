package com.plataformaempleo.api.vacancy.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pregunta que el modelo responde sobre cada CV de la vacante.
 * Tiene la forma del tipo "choice" del contrato de inferencia: la primera opción es la afirmativa.
 * VALORES SUPUESTOS (límites de longitud, 2 a 6 opciones): confirmar con el diseño técnico.
 */
public record Criterion(String key, String label, String question, Kind kind, List<String> options) {

    public static final List<String> YES_NO = List.of("Sí", "No");
    static final int LABEL_MAX = 60;
    static final int QUESTION_MIN = 5;
    static final int QUESTION_MAX = 2000;
    static final int OPTIONS_MIN = 2;
    static final int OPTIONS_MAX = 6;
    static final int OPTION_MAX = 80;

    public enum Kind { YES_NO, CHOICE }

    public Criterion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        label = text(label, 2, LABEL_MAX, "La etiqueta del criterio debe tener entre 2 y " + LABEL_MAX + " caracteres");
        question = text(question, QUESTION_MIN, QUESTION_MAX,
                "La pregunta del criterio debe tener entre " + QUESTION_MIN + " y " + QUESTION_MAX + " caracteres");
        options = kind == Kind.YES_NO ? YES_NO : choiceOptions(options);
    }

    private static List<String> choiceOptions(List<String> raw) {
        if (raw == null || raw.size() < OPTIONS_MIN || raw.size() > OPTIONS_MAX) {
            throw invalid("Un criterio de opciones necesita entre " + OPTIONS_MIN + " y " + OPTIONS_MAX + " opciones");
        }
        List<String> clean = raw.stream()
                .map(o -> text(o, 1, OPTION_MAX, "Cada opción debe tener entre 1 y " + OPTION_MAX + " caracteres"))
                .toList();
        var seen = new HashSet<String>();
        for (String o : clean) {
            if (!seen.add(o.toLowerCase(Locale.ROOT))) {
                throw invalid("Las opciones de un criterio no pueden repetirse");
            }
        }
        return clean;
    }

    private static String text(String raw, int min, int max, String message) {
        String value = raw == null ? "" : raw.strip();
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            throw invalid(message);
        }
        return value;
    }

    private static DomainException invalid(String message) {
        return new DomainException("vacancy.criterion.invalid", message);
    }
}
