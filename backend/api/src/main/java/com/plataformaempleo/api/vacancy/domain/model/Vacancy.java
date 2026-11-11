package com.plataformaempleo.api.vacancy.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vacante de una empresa. Ciclo de vida: borrador → publicada → cerrada.
 * Solo se edita en borrador: cambiar los criterios de una vacante publicada invalidaría
 * el análisis de los CV ya recibidos. VALORES SUPUESTOS (límites, máximo de criterios): confirmar.
 */
public record Vacancy(
        UUID id,
        UUID companyId,
        UUID createdBy,
        Content content,
        Status status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant closedAt) {

    /** Límite de preguntas por petición del servicio de inferencia. */
    public static final int MAX_CRITERIA = 50;

    public enum Status { DRAFT, PUBLISHED, CLOSED }

    public enum Modality { PRESENCIAL, HIBRIDO, REMOTO }

    public Vacancy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Vacancy draft(UUID companyId, UUID createdBy, Content content, Instant now) {
        return new Vacancy(UUID.randomUUID(), companyId, createdBy, content, Status.DRAFT, now, now, null, null);
    }

    public Vacancy edit(Content newContent, Instant now) {
        requireStatus(Status.DRAFT, "Solo se puede editar una vacante en borrador");
        return new Vacancy(id, companyId, createdBy, newContent, status, createdAt, now, publishedAt, closedAt);
    }

    public Vacancy publish(Instant now) {
        requireStatus(Status.DRAFT, "Solo se puede publicar una vacante en borrador");
        if (content.criteria().isEmpty()) {
            throw new DomainException("vacancy.criteria.required",
                    "Agrega al menos un criterio antes de publicar la vacante");
        }
        return new Vacancy(id, companyId, createdBy, content, Status.PUBLISHED, createdAt, now, now, closedAt);
    }

    public Vacancy close(Instant now) {
        requireStatus(Status.PUBLISHED, "Solo se puede cerrar una vacante publicada");
        return new Vacancy(id, companyId, createdBy, content, Status.CLOSED, createdAt, now, publishedAt, now);
    }

    private void requireStatus(Status expected, String message) {
        if (status != expected) {
            throw new DomainException("vacancy.status.invalid", message);
        }
    }

    /** Lo que la empresa escribe. Las claves de los criterios (q1, q2, ...) se asignan por posición. */
    public record Content(String title, String description, String location, Modality modality,
                          List<Criterion> criteria) {

        public Content {
            title = text(title, 3, 160, "El título debe tener entre 3 y 160 caracteres");
            description = text(description, 20, 50_000, "La descripción debe tener entre 20 y 50 000 caracteres");
            location = text(location, 2, 120, "La ubicación debe tener entre 2 y 120 caracteres");
            if (modality == null) {
                throw new DomainException("vacancy.invalid", "Indica la modalidad de trabajo");
            }
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            if (criteria.size() > MAX_CRITERIA) {
                throw new DomainException("vacancy.invalid", "Una vacante admite como máximo " + MAX_CRITERIA + " criterios");
            }
        }

        /** Crea el contenido asignando claves estables por posición a los criterios. */
        public static Content of(String title, String description, String location, Modality modality,
                                 List<CriterionInput> criteria) {
            List<Criterion> keyed = new ArrayList<>();
            List<CriterionInput> inputs = criteria == null ? List.of() : criteria;
            for (int i = 0; i < inputs.size(); i++) {
                CriterionInput c = inputs.get(i);
                if (c == null || c.kind() == null) {
                    throw new DomainException("vacancy.criterion.invalid", "Indica el tipo de cada criterio");
                }
                keyed.add(new Criterion("q" + (i + 1), c.label(), c.question(), c.kind(), c.options()));
            }
            return new Content(title, description, location, modality, keyed);
        }

        private static String text(String raw, int min, int max, String message) {
            String value = raw == null ? "" : raw.strip();
            int length = value.codePointCount(0, value.length());
            if (length < min || length > max) {
                throw new DomainException("vacancy.invalid", message);
            }
            return value;
        }
    }

    /** Criterio tal como llega, antes de asignarle su clave. */
    public record CriterionInput(String label, String question, Criterion.Kind kind, List<String> options) {
    }
}
