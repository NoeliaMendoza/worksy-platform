package com.plataformaempleo.worker.screening.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScreeningOutcomeTest {

    private final Criterion spring = new Criterion("q1", "Spring Boot", "¿Tiene experiencia con Spring Boot?",
            Criterion.Kind.YES_NO, List.of("Sí", "No"));
    private final Criterion years = new Criterion("q2", "Años en Java", "¿Cuántos años con Java?",
            Criterion.Kind.CHOICE, List.of("Más de 3", "1 a 3", "Menos de 1"));

    @Test
    void puntuaPorFavorabilidadYPromediaLaConfianza() {
        var decision = new Decision(Map.of(
                "q1", new Decision.Answer("Sí", 0.9),
                "q2", new Decision.Answer("1 a 3", 0.7)), "baseline-bow-1", 12);

        var outcome = ScreeningOutcome.of(List.of(spring, years), decision, 0.6);

        assertThat(outcome.results()).extracting(ScreeningOutcome.CriterionResult::score).containsExactly(1.0, 0.5);
        assertThat(outcome.overallScore()).isEqualTo(0.75);
        assertThat(outcome.confidence()).isEqualTo(0.8);
        assertThat(outcome.needsReview()).isFalse();
        assertThat(outcome.modelVersion()).isEqualTo("baseline-bow-1");
    }

    @Test
    void confianzaBajoElUmbralPideRevision() {
        var decision = new Decision(Map.of(
                "q1", new Decision.Answer("No", 0.55),
                "q2", new Decision.Answer("Menos de 1", 0.5)), "v", 1);

        var outcome = ScreeningOutcome.of(List.of(spring, years), decision, 0.6);

        assertThat(outcome.overallScore()).isZero();
        assertThat(outcome.needsReview()).isTrue();
    }

    @Test
    void rechazaRespuestasIncompletasOOpcionesInventadas() {
        assertThatThrownBy(() -> ScreeningOutcome.of(List.of(spring, years),
                new Decision(Map.of("q1", new Decision.Answer("Sí", 0.9)), "v", 1), 0.6))
                .hasMessageContaining("q2");
        assertThatThrownBy(() -> ScreeningOutcome.of(List.of(spring),
                new Decision(Map.of("q1", new Decision.Answer("Quizás", 0.9)), "v", 1), 0.6))
                .hasMessageContaining("inexistente");
    }
}
