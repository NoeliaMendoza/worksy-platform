package com.plataformaempleo.api.interview.infrastructure.persistence;

import com.plataformaempleo.api.interview.domain.model.CallRoom;
import com.plataformaempleo.api.interview.domain.port.out.CallRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCallRepository implements CallRepository {
    private final JdbcTemplate jdbc;
    JdbcCallRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public CallRoom.Session openSession(UUID interviewId, Instant now) {
        // El índice único parcial resuelve la carrera: si otra persona la abrió a la vez, se usa la suya.
        jdbc.update("insert into interview_call_sessions (id, interview_id, opened_at) values (?, ?, ?) "
                + "on conflict (interview_id) where closed_at is null do nothing", UUID.randomUUID(), interviewId, Timestamp.from(now));
        return findOpen(interviewId).orElseThrow();
    }

    @Override
    public Optional<CallRoom.Session> findOpen(UUID interviewId) {
        return jdbc.query("select id, interview_id, opened_at from interview_call_sessions where interview_id = ? and closed_at is null",
                (rs, n) -> new CallRoom.Session(rs.getObject("id", UUID.class), rs.getObject("interview_id", UUID.class),
                        rs.getTimestamp("opened_at").toInstant()), interviewId).stream().findFirst();
    }

    @Override
    public void join(UUID sessionId, UUID userId, CallRoom.Role role, Instant now) {
        // Volver a entrar (recarga, otro dispositivo, reconexión) conserva la primera hora de entrada.
        jdbc.update("insert into interview_call_participants (session_id, user_id, role, joined_at) values (?, ?, ?, ?) "
                + "on conflict (session_id, user_id) do update set left_at = null", sessionId, userId, role.name(), Timestamp.from(now));
    }

    @Override
    public void leave(UUID sessionId, UUID userId, Instant now) {
        jdbc.update("update interview_call_participants set left_at = ? where session_id = ? and user_id = ? and left_at is null",
                Timestamp.from(now), sessionId, userId);
    }

    @Override
    public boolean close(UUID sessionId, UUID closedBy, Instant now) {
        jdbc.update("update interview_call_participants set left_at = ? where session_id = ? and left_at is null", Timestamp.from(now), sessionId);
        return jdbc.update("update interview_call_sessions set closed_at = ?, closed_by = ? where id = ? and closed_at is null",
                Timestamp.from(now), closedBy, sessionId) == 1;
    }

    @Override
    public List<CallRoom.Participant> participants(UUID sessionId) {
        return jdbc.query("select user_id, role, joined_at, left_at from interview_call_participants where session_id = ? order by joined_at",
                (rs, n) -> new CallRoom.Participant(rs.getObject("user_id", UUID.class), CallRoom.Role.valueOf(rs.getString("role")),
                        rs.getTimestamp("joined_at").toInstant(),
                        rs.getTimestamp("left_at") == null ? null : rs.getTimestamp("left_at").toInstant()), sessionId);
    }

    @Override
    public List<UUID> interviewers(UUID interviewId) {
        // Solo cuentas activas de la empresa: alguien dado de baja ya no entra a la sala.
        return jdbc.query("select p.user_id from interview_participants p join users u on u.id = p.user_id "
                        + "where p.interview_id = ? and u.deleted_at is null order by p.role, p.user_id",
                (rs, n) -> rs.getObject(1, UUID.class), interviewId);
    }
}
