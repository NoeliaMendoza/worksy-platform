package com.plataformaempleo.api.interview.domain.port.out;

import com.plataformaempleo.api.interview.domain.model.CallRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Salas de videollamada y su registro de asistencia (sin contenido audiovisual). */
public interface CallRepository {
    /** Devuelve la sala abierta de la entrevista o la crea; dos llamadas simultáneas obtienen la misma. */
    CallRoom.Session openSession(UUID interviewId, Instant now);
    Optional<CallRoom.Session> findOpen(UUID interviewId);
    void join(UUID sessionId, UUID userId, CallRoom.Role role, Instant now);
    void leave(UUID sessionId, UUID userId, Instant now);
    boolean close(UUID sessionId, UUID closedBy, Instant now);
    List<CallRoom.Participant> participants(UUID sessionId);
    /** Personas de la empresa a cargo de la entrevista (pueden entrar como entrevistadoras). */
    List<UUID> interviewers(UUID interviewId);
}
