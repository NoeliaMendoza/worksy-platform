package com.plataformaempleo.api.shared.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.shared.domain.port.out.IdempotencyStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claves de idempotencia en {@code idempotency_keys}. Si dos peticiones con la misma clave llegan a la
 * vez, la clave primaria hace fallar a la segunda (409) y la transacción de su trabajo se deshace entera.
 */
@Component
class JdbcIdempotencyStore implements IdempotencyStore {

    record Stored(List<UUID> resultIds, int skipped) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    JdbcIdempotencyStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Entry> find(UUID actorId, String key) {
        return jdbc.query("select operation, fingerprint, result from idempotency_keys where actor_id = ? and idem_key = ?",
                (rs, n) -> {
                    Stored stored = read(rs.getString("result"));
                    return new Entry(rs.getString("operation"), rs.getString("fingerprint"), stored.resultIds(),
                            stored.skipped());
                }, actorId, key).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UUID actorId, String key, Entry entry) {
        jdbc.update("insert into idempotency_keys (actor_id, idem_key, operation, fingerprint, result, created_at) "
                        + "values (?, ?, ?, ?, cast(? as jsonb), ?)",
                actorId, key, entry.operation(), entry.fingerprint(),
                write(Map.of("resultIds", entry.resultIds(), "skipped", entry.skipped())),
                Timestamp.from(clock.instant()));
    }

    private Stored read(String raw) {
        try {
            return json.readValue(raw, Stored.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resultado de idempotencia ilegible", e);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Resultado de idempotencia no serializable", e);
        }
    }
}
