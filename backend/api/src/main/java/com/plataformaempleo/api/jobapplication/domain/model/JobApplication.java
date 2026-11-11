package com.plataformaempleo.api.jobapplication.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Postulación de una persona candidata a una vacante, con el CV que eligió.
 * (Se llama JobApplication para no confundirla con la "capa de aplicación" de la arquitectura.)
 */
public record JobApplication(UUID id, UUID vacancyId, UUID candidateId, UUID cvId, Instant appliedAt) {

    public JobApplication {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vacancyId, "vacancyId");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(cvId, "cvId");
        Objects.requireNonNull(appliedAt, "appliedAt");
    }

    public static JobApplication submit(UUID vacancyId, UUID candidateId, UUID cvId, Instant now) {
        return new JobApplication(UUID.randomUUID(), vacancyId, candidateId, cvId, now);
    }

    public boolean belongsTo(UUID userId) {
        return candidateId.equals(userId);
    }
}
