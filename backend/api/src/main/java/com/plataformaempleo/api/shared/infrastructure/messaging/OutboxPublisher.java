package com.plataformaempleo.api.shared.infrastructure.messaging;

import com.plataformaempleo.common.events.Messaging;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envía a RabbitMQ los eventos pendientes del outbox (sección 5 del diseño, paso 3).
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED}: varias réplicas de la API pueden publicar a la vez sin repetir eventos.</li>
 *   <li>Se marca como publicado solo tras la confirmación del broker. Si el proceso cae justo después de
 *       publicar, el evento se reenvía: la entrega es "al menos una vez" y el consumidor descarta duplicados
 *       por {@code messageId}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "worksy.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private record Pending(UUID id, String eventType, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RabbitTemplate rabbit;
    private final OutboxProperties props;
    private final Clock clock;
    private final Counter published;
    private final Counter failures;

    OutboxPublisher(JdbcTemplate jdbc, TransactionTemplate tx, RabbitTemplate rabbit, OutboxProperties props, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.rabbit = rabbit;
        this.props = props;
        this.clock = clock;
        this.published = meters.counter("worksy.outbox.published");
        this.failures = meters.counter("worksy.outbox.failures");
    }

    @Scheduled(fixedDelayString = "${worksy.outbox.poll-interval-ms:1000}")
    void tick() {
        try {
            publishPending();
        } catch (RuntimeException e) {
            // Sin broker la API sigue funcionando: los eventos esperan en la base de datos.
            failures.increment();
            log.warn("No se pudo publicar el outbox; se reintentará: {}", e.getMessage());
        }
    }

    /**
     * Publica una ronda dentro de UNA transacción explícita: los bloqueos FOR UPDATE duran hasta marcar
     * los eventos. (Con @Transactional y una llamada desde la misma clase no habría transacción.)
     * Devuelve cuántos eventos quedaron confirmados.
     */
    public int publishPending() {
        Integer count = tx.execute(status -> publishBatch());
        return count == null ? 0 : count;
    }

    private int publishBatch() {
        List<Pending> batch = jdbc.query(
                "select id, event_type, payload::text as payload from outbox_events where published_at is null "
                        + "order by created_at limit ? for update skip locked",
                (rs, n) -> new Pending(rs.getObject("id", UUID.class), rs.getString("event_type"), rs.getString("payload")),
                props.batchSize());
        if (batch.isEmpty()) {
            return 0;
        }
        rabbit.invoke(ops -> {
            for (Pending event : batch) {
                ops.send(Messaging.EVENTS_EXCHANGE, event.eventType(), toMessage(event));
            }
            ops.waitForConfirmsOrDie(props.confirmTimeoutMs());
            return null;
        });
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate("update outbox_events set published_at = ? where id = ?",
                batch.stream().map(e -> new Object[] {now, e.id()}).toList());
        published.increment(batch.size());
        return batch.size();
    }

    private static Message toMessage(Pending event) {
        return MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setMessageId(event.id().toString())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader(Messaging.EVENT_TYPE_HEADER, event.eventType())
                .build();
    }
}
