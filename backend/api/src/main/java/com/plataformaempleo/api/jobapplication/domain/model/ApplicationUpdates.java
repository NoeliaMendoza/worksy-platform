package com.plataformaempleo.api.jobapplication.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pendientes agrupados por postulación. La cantidad total cuenta cambios, no postulaciones. */
public record ApplicationUpdates(long unread, List<Update> updates) {
    public ApplicationUpdates {
        updates = List.copyOf(updates);
    }

    public record Update(UUID applicationId, String vacancyTitle, String companyName,
                         ApplicationStage stage, Instant changedAt, long unread) { }
}
