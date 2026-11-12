package com.plataformaempleo.worker.screening.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Lo que el worker necesita para procesar una solicitud, leído de la base de datos
 * (el mensaje solo trae ids: el contenido nunca viaja por la cola).
 */
public record ScreeningContext(
        UUID requestId,
        UUID vacancyId,
        Status status,
        String provider,
        String cvStorageKey,
        String jobDescription,
        List<Criterion> criteria) {

    public enum Status { PENDING, PROCESSING, COMPLETED, NEEDS_REVIEW, FAILED }

    public ScreeningContext {
        criteria = List.copyOf(criteria);
    }

    /** Una solicitud ya resuelta no se vuelve a procesar (p. ej., un mensaje repetido con otro id). */
    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.NEEDS_REVIEW || status == Status.FAILED;
    }
}
