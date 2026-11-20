package com.plataformaempleo.api.jobapplication.domain.model;

import java.time.Instant;
import java.util.List;

/** Historial público del proceso. No expone identidades internas del equipo. */
public record ApplicationProgress(ApplicationStage stage, List<Event> history) {
    public ApplicationProgress {
        history = List.copyOf(history);
    }

    public record Event(long id, ApplicationStage fromStage, ApplicationStage stage,
                        String message, Instant changedAt) { }

    /** Resumen para la lista del candidato, sin mensajes ni identidades del equipo. */
    public record Milestone(ApplicationStage stage, Instant changedAt) { }
}
