package com.plataformaempleo.worker.screening.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.worker.screening.domain.model.Criterion;
import com.plataformaempleo.worker.screening.domain.model.ScreeningContext;
import com.plataformaempleo.worker.screening.domain.model.ScreeningOutcome;
import com.plataformaempleo.worker.screening.domain.port.out.ScreeningStore;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC sobre la base de datos compartida con la API. Las tablas las crea Flyway desde la API;
 * aquí no hay JPA para no duplicar entidades. {@code screening.completed} se deja en el outbox de la API:
 * su publicador lo envía a RabbitMQ (una sola pieza publica, con las mismas garantías).
 */
public class JdbcScreeningStore implements ScreeningStore {

    private record CriterionJson(String key, String label, String question, String kind, List<String> options) {
    }

    private static final TypeReference<List<CriterionJson>> CRITERIA = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final String consumer;

    public JdbcScreeningStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock, String consumer) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.consumer = consumer;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(String messageId) {
        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from processed_messages where message_id = ? and consumer = ?)",
                Boolean.class, messageId, consumer);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScreeningContext> load(UUID requestId) {
        return jdbc.query("""
                        select r.id, r.vacancy_id, r.status, r.provider, c.storage_key, v.description,
                               v.criteria::text as criteria
                        from screening_requests r
                        join cv_documents c on c.id = r.cv_id
                        join vacancies v on v.id = r.vacancy_id
                        where r.id = ?""",
                (rs, n) -> new ScreeningContext(
                        rs.getObject("id", UUID.class),
                        rs.getObject("vacancy_id", UUID.class),
                        ScreeningContext.Status.valueOf(rs.getString("status")),
                        rs.getString("provider"),
                        rs.getString("storage_key"),
                        rs.getString("description"),
                        criteria(rs.getString("criteria"))),
                requestId).stream().findFirst();
    }

    @Override
    @Transactional
    public void markProcessing(UUID requestId) {
        jdbc.update("update screening_requests set status = 'PROCESSING', attempts = attempts + 1, updated_at = ? "
                + "where id = ? and status in ('PENDING', 'PROCESSING')", now(), requestId);
    }

    @Override
    @Transactional
    public void complete(ScreeningContext context, String messageId, ScreeningOutcome outcome) {
        recordMessage(messageId);
        jdbc.update("delete from screening_answers where request_id = ?", context.requestId());
        jdbc.batchUpdate("insert into screening_answers (id, request_id, question_key, choice, probability, score, "
                        + "latency_ms) values (?, ?, ?, ?, ?, ?, ?)",
                outcome.results().stream().map(r -> new Object[] {UUID.randomUUID(), context.requestId(),
                        r.questionKey(), r.choice(), decimal(r.probability()), decimal(r.score()), r.latencyMs()})
                        .toList());
        String status = outcome.needsReview() ? "NEEDS_REVIEW" : "COMPLETED";
        jdbc.update("update screening_requests set status = ?, model_version = ?, overall_score = ?, confidence = ?, "
                        + "updated_at = ? where id = ?",
                status, outcome.modelVersion(), decimal(outcome.overallScore()), decimal(outcome.confidence()), now(),
                context.requestId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("screeningRequestId", context.requestId().toString());
        payload.put("vacancyId", context.vacancyId().toString());
        payload.put("status", status);
        jdbc.update("insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at) "
                        + "values (?, 'screening_request', ?, ?, cast(? as jsonb), ?)",
                UUID.randomUUID(), context.requestId(), EventTypes.SCREENING_COMPLETED, toJson(payload), now());
    }

    @Override
    @Transactional
    public void fail(UUID requestId, String messageId) {
        recordMessage(messageId);
        // Nunca pisa un resultado ya guardado (p. ej., si otro mensaje terminó antes).
        jdbc.update("update screening_requests set status = 'FAILED', updated_at = ? "
                + "where id = ? and status in ('PENDING', 'PROCESSING')", now(), requestId);
    }

    @Override
    @Transactional
    public void markMessageProcessed(String messageId) {
        recordMessage(messageId);
    }

    private void recordMessage(String messageId) {
        jdbc.update("insert into processed_messages (message_id, consumer, processed_at) values (?, ?, ?) "
                + "on conflict do nothing", messageId, consumer, now());
    }

    private List<Criterion> criteria(String raw) {
        try {
            return json.readValue(raw, CRITERIA).stream()
                    .map(c -> new Criterion(c.key(), c.label(), c.question(), Criterion.Kind.valueOf(c.kind()), c.options()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Criterios de la vacante ilegibles", e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static BigDecimal decimal(double v) {
        return BigDecimal.valueOf(v).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
