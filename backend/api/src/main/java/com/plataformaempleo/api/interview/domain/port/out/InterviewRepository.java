package com.plataformaempleo.api.interview.domain.port.out;

import com.plataformaempleo.api.interview.domain.model.ChangeRequest;
import com.plataformaempleo.api.interview.domain.model.Interview;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository {

    /** Quién mira: la persona candidata (sin empresa) o alguien del equipo de una empresa. */
    record Viewer(UUID userId, UUID companyId) {
        public boolean company() { return companyId != null; }
    }

    /** Propuesta abierta tal como la ve quien participa (sin el id de quien la pidió). */
    record OpenChange(UUID id, String reason, List<Instant> slots, Instant createdAt) { }

    /**
     * Cita con su contexto. {@code candidateEmail} y {@code organizerEmail} solo llegan a la empresa.
     * {@code mine}: la persona del equipo que mira es responsable de la cita.
     */
    record Item(Interview interview, String vacancyTitle, String companyName, String candidateEmail, String organizerEmail,
                boolean unread, boolean mine, Interview.Phase phase, OpenChange changeRequest) { }

    record Event(int version, Interview.Change change, Instant startsAt, int durationMinutes, String zoneId, Instant changedAt,
                 Actor actor, String reason, Boolean placeChanged) {
        public enum Actor { CANDIDATE, COMPANY }
    }

    record Counts(long upcoming, long pending, long history, long cancelled, long changeRequests, long awaitingOutcome,
                  long expired) { }

    /** Otra cita vigente que se cruza con un horario. */
    record Overlap(UUID interviewId, String title, Instant startsAt, int durationMinutes, String zoneId, String vacancyTitle) { }

    record Query(Interview.View view, Instant now, Instant from, Instant to, boolean mineOnly, int offset, int limit) { }

    Optional<Interview> find(UUID id);
    boolean insert(Interview interview);
    boolean update(Interview interview, int expectedVersion);
    void record(Interview interview, Interview.Change change, UUID actorId, Event.Actor actor, String reason, Boolean placeChanged);
    Optional<Item> item(UUID id, Viewer viewer, Instant now);
    List<Item> list(Viewer viewer, Query query);
    Counts counts(Viewer viewer, boolean mineOnly, Instant now);
    long unread(UUID userId, UUID companyId);
    void markRead(UUID interviewId, UUID userId, int version);
    List<Event> history(UUID interviewId);

    void addParticipant(UUID interviewId, UUID userId, String role);
    /** Citas vigentes de la misma postulación que se cruzan con [start, end), sin contar {@code exclude}. */
    boolean overlapsApplication(UUID applicationId, Instant start, Instant end, UUID exclude);
    /** Citas vigentes a cargo de {@code userId} que se cruzan con [start, end), sin contar {@code exclude}. */
    List<Overlap> overlapsParticipant(UUID userId, Instant start, Instant end, UUID exclude);

    boolean insertChangeRequest(ChangeRequest request);
    Optional<ChangeRequest> openChangeRequest(UUID interviewId);
    boolean resolveChangeRequest(UUID requestId, ChangeRequest.Status status, UUID resolvedBy, Instant at);
}
