package com.plataformaempleo.api.screening.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitud de preselección de un CV para una vacante. La crea la empresa (PENDING); el trabajador
 * de preselección la procesa y la deja en COMPLETED, NEEDS_REVIEW (confianza baja) o FAILED.
 * Ninguna solicitud decide nada: la decisión siempre es humana.
 */
public record ScreeningRequest(
        UUID id,
        UUID vacancyId,
        UUID applicationId,
        UUID cvId,
        String provider,
        String modelVersion,
        Status status,
        int attempts,
        BigDecimal overallScore,
        BigDecimal confidence,
        UUID requestedBy,
        Instant requestedAt,
        Instant updatedAt) {

    public enum Status { PENDING, PROCESSING, COMPLETED, NEEDS_REVIEW, FAILED }

    public ScreeningRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vacancyId, "vacancyId");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(cvId, "cvId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ScreeningRequest pending(UUID vacancyId, UUID applicationId, UUID cvId, String provider,
                                           UUID requestedBy, Instant now) {
        return new ScreeningRequest(UUID.randomUUID(), vacancyId, applicationId, cvId, provider, null, Status.PENDING,
                0, null, null, requestedBy, now, now);
    }
}
