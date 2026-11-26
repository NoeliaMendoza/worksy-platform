package com.plataformaempleo.api.interview.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reglas de la sala de videollamada de una entrevista. La sala se abre 15 minutos antes de la hora y admite entrar
 * hasta 30 minutos después del final previsto (VALORES SUPUESTOS). Solo entrevistas con modalidad VIDEO, vigentes
 * (por confirmar o confirmadas). No se graba nada: solo consta quién entró y cuándo.
 */
public final class CallRoom {
    public static final Duration OPENS_BEFORE = Duration.ofMinutes(15);
    public static final Duration CLOSES_AFTER = Duration.ofMinutes(30);

    public enum Role { CANDIDATE, INTERVIEWER }

    public record Session(UUID id, UUID interviewId, Instant openedAt) { }

    public record Participant(UUID userId, Role role, Instant joinedAt, Instant leftAt) {
        public boolean present() { return leftAt == null; }
    }

    /** Lo que autoriza a una persona a hablar en la sala: con quién puede intercambiar señales y hasta cuándo. */
    public record Grant(UUID userId, Role role, UUID sessionId, UUID interviewId, List<UUID> peers, Instant expiresAt) {
        public Grant { peers = List.copyOf(peers); }
    }

    private CallRoom() { }

    public static Instant opensAt(Interview interview) { return interview.startsAt().minus(OPENS_BEFORE); }

    public static Instant closesAt(Interview interview) { return interview.endsAt().plus(CLOSES_AFTER); }

    /** Lanza un error comprensible si la sala no está disponible ahora. */
    public static void requireJoinable(Interview interview, Instant now) {
        if (interview.mode() != Interview.Mode.VIDEO) throw Interview.invalid("Esta entrevista no es por videollamada en Worksy");
        if (interview.status() != Interview.Status.INVITED && interview.status() != Interview.Status.CONFIRMED) {
            throw Interview.invalid("La entrevista ya no está vigente");
        }
        if (now.isBefore(opensAt(interview))) throw Interview.invalid("La sala abre 15 minutos antes de la hora de la entrevista");
        if (!now.isBefore(closesAt(interview))) throw Interview.invalid("La sala de esta entrevista ya se cerró");
    }
}
