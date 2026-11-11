package com.plataformaempleo.api.vacancy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyTest {

    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");

    static Vacancy.CriterionInput yesNo(String label) {
        return new Vacancy.CriterionInput(label, "¿Tiene experiencia con " + label + "?", Criterion.Kind.YES_NO, null);
    }

    static Vacancy.Content content(List<Vacancy.CriterionInput> criteria) {
        return Vacancy.Content.of("Desarrollador/a backend Java", "Equipo de pagos con Spring Boot y PostgreSQL.",
                "Lima", Vacancy.Modality.HIBRIDO, criteria);
    }

    @Test
    void asignaClavesPorPosicionYUsaSiNoComoOpcionesFijas() {
        var choice = new Vacancy.CriterionInput("Años en Java", "¿Cuántos años de experiencia tiene con Java?",
                Criterion.Kind.CHOICE, List.of(" Más de 3 ", "1 a 3", "Menos de 1"));
        var c = content(List.of(yesNo("Spring Boot"), choice));

        assertThat(c.criteria()).extracting(Criterion::key).containsExactly("q1", "q2");
        assertThat(c.criteria().get(0).options()).containsExactly("Sí", "No");
        assertThat(c.criteria().get(1).options()).containsExactly("Más de 3", "1 a 3", "Menos de 1");
    }

    @Test
    void cicloDeVidaBorradorPublicadaCerrada() {
        var draft = Vacancy.draft(UUID.randomUUID(), UUID.randomUUID(), content(List.of(yesNo("Kafka"))), now);
        var published = draft.publish(now.plusSeconds(60));
        var closed = published.close(now.plusSeconds(120));

        assertThat(published.status()).isEqualTo(Vacancy.Status.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(closed.closedAt()).isEqualTo(now.plusSeconds(120));
        assertThat(code(() -> published.edit(content(List.of()), now))).isEqualTo("vacancy.status.invalid");
        assertThat(code(() -> closed.publish(now))).isEqualTo("vacancy.status.invalid");
        assertThat(code(() -> draft.close(now))).isEqualTo("vacancy.status.invalid");
    }

    @Test
    void noSePublicaSinCriterios() {
        var draft = Vacancy.draft(UUID.randomUUID(), UUID.randomUUID(), content(List.of()), now);
        assertThat(code(() -> draft.publish(now))).isEqualTo("vacancy.criteria.required");
    }

    @Test
    void validaContenidoYCriterios() {
        assertThat(code(() -> Vacancy.Content.of("A", "Descripción suficientemente larga.", "Lima",
                Vacancy.Modality.REMOTO, List.of()))).isEqualTo("vacancy.invalid");
        assertThat(code(() -> Vacancy.Content.of("Título", "corta", "Lima", Vacancy.Modality.REMOTO, List.of())))
                .isEqualTo("vacancy.invalid");
        assertThat(code(() -> content(List.of(new Vacancy.CriterionInput("X", "¿Pregunta válida?", Criterion.Kind.YES_NO, null)))))
                .isEqualTo("vacancy.criterion.invalid");
        assertThat(code(() -> content(List.of(new Vacancy.CriterionInput("Inglés", "¿Qué nivel de inglés tiene?",
                Criterion.Kind.CHOICE, List.of("B2"))))))
                .isEqualTo("vacancy.criterion.invalid");
        assertThat(code(() -> content(List.of(new Vacancy.CriterionInput("Inglés", "¿Qué nivel de inglés tiene?",
                Criterion.Kind.CHOICE, List.of("B2", "b2"))))))
                .isEqualTo("vacancy.criterion.invalid");
        assertThat(code(() -> content(Collections.nCopies(Vacancy.MAX_CRITERIA + 1, yesNo("Java")))))
                .isEqualTo("vacancy.invalid");
    }

    private static String code(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class);
        try {
            action.run();
        } catch (DomainException e) {
            return e.code();
        }
        return null;
    }
}
