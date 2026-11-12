package com.plataformaempleo.api.shared.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Guarda eventos en {@code outbox_events}. Exige una transacción abierta: la del cambio de negocio. */
@Component
class JdbcEventOutbox implements EventOutbox {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    JdbcEventOutbox(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at) "
                        + "values (?, ?, ?, ?, cast(? as jsonb), ?)",
                id, aggregateType, aggregateId, eventType, toJson(payload), Timestamp.from(clock.instant()));
        return id;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El evento no se puede convertir a JSON", e);
        }
    }
}
