package com.plataformaempleo.worker.screening.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de una preselección: puntuación por criterio, puntuación global y confianza.
 * No decide nada sobre la persona: ordena y señala dudas para la revisión humana.
 *
 * <ul>
 *   <li>Puntuación de un criterio: favorabilidad de la opción elegida (1 = la más favorable).</li>
 *   <li>Puntuación global: media de los criterios (todos pesan igual; el diseño prevé pesos más adelante).</li>
 *   <li>Confianza: media de las probabilidades del proveedor.</li>
 *   <li>NEEDS_REVIEW si la confianza queda bajo el umbral (sección 5 del diseño, paso 7).</li>
 * </ul>
 */
public record ScreeningOutcome(List<CriterionResult> results, double overallScore, double confidence,
                               boolean needsReview, String modelVersion) {

    public record CriterionResult(String questionKey, String choice, double probability, double score, int latencyMs) {
    }

    public ScreeningOutcome {
        results = List.copyOf(results);
    }

    public static ScreeningOutcome of(List<Criterion> criteria, Decision decision, double reviewThreshold) {
        Objects.requireNonNull(decision, "decision");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("La vacante no tiene criterios");
        }
        List<CriterionResult> results = new ArrayList<>();
        for (Criterion c : criteria) {
            Decision.Answer answer = decision.answers().get(c.key());
            if (answer == null) {
                throw new IllegalArgumentException("El proveedor no respondió el criterio " + c.key());
            }
            Double score = c.favorability(answer.choice());
            if (score == null) {
                throw new IllegalArgumentException("El proveedor respondió una opción inexistente en " + c.key());
            }
            results.add(new CriterionResult(c.key(), answer.choice(), clamp(answer.probability()), score,
                    decision.latencyMs()));
        }
        double overall = results.stream().mapToDouble(CriterionResult::score).average().orElse(0);
        double confidence = results.stream().mapToDouble(CriterionResult::probability).average().orElse(0);
        return new ScreeningOutcome(results, round(overall), round(confidence), confidence < reviewThreshold,
                decision.modelVersion());
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
