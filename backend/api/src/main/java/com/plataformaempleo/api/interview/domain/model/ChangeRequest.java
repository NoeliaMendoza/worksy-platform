package com.plataformaempleo.api.interview.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Propuesta de otra hora hecha por el candidato: de 1 a 3 horarios y un motivo. Mientras está abierta, la cita
 * sigue en pie con su hora actual; solo hay una abierta por entrevista.
 */
public record ChangeRequest(UUID id, UUID interviewId, UUID requestedBy, String reason, List<Instant> slots,
                            Status status, Instant createdAt) {
    public enum Status { OPEN, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED }

    public ChangeRequest {
        slots = List.copyOf(slots);
    }

    public static ChangeRequest open(UUID id, Interview interview, UUID requestedBy, String reason, List<Instant> proposed, Instant now) {
        if (!interview.changeable(now) || interview.status() == Interview.Status.DECLINED) {
            throw Interview.invalid("Solo puedes proponer otra hora para una entrevista vigente que aún no haya comenzado");
        }
        String cleaned = Interview.clean(reason, 300, true);
        if (cleaned.length() < 3) throw Interview.invalid("Cuéntale a la empresa por qué necesitas otra hora");
        if (proposed == null || proposed.isEmpty() || proposed.size() > 3) throw Interview.invalid("Propón de 1 a 3 horarios");
        var unique = new LinkedHashSet<Instant>();
        for (Instant slot : proposed) {
            Instant start = Interview.requireFutureStart(slot, now);
            if (start.equals(interview.startsAt())) throw Interview.invalid("Propón horarios distintos al actual");
            unique.add(start);
        }
        if (unique.size() != proposed.size()) throw Interview.invalid("Los horarios propuestos se repiten");
        return new ChangeRequest(id, interview.id(), requestedBy, cleaned, List.copyOf(unique), Status.OPEN, now);
    }

    public Instant slot(int index) {
        if (index < 0 || index >= slots.size()) throw Interview.invalid("Elige uno de los horarios propuestos");
        return slots.get(index);
    }

    @Override
    public String toString() { return "ChangeRequest[id=" + id + ", status=" + status + "]"; }
}
