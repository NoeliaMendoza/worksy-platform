package com.plataformaempleo.worker.screening.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.common.events.Messaging;
import com.plataformaempleo.worker.screening.application.ScreeningProcessor;
import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Adaptador de entrada: consume {@code screening.requested}. Decide qué hacer con el mensaje:
 * <ul>
 *   <li>Procesado, duplicado o fallo definitivo → confirmación (ack): ya no hay nada que hacer.</li>
 *   <li>Otra réplica tiene el bloqueo → vuelve a la cola tras una pausa breve.</li>
 *   <li>Fallo pasajero que persiste o mensaje ilegible → a la cola de mensajes fallidos (sin reencolar).</li>
 * </ul>
 */
public class ScreeningRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ScreeningRequestedListener.class);
    /** VALOR SUPUESTO: pausa antes de devolver a la cola un mensaje cuyo bloqueo tiene otra réplica. */
    static final long BUSY_PAUSE_MS = 1_000;

    private final ScreeningProcessor processor;
    private final ObjectMapper json;
    private final MeterRegistry meters;

    public ScreeningRequestedListener(ScreeningProcessor processor, ObjectMapper json, MeterRegistry meters) {
        this.processor = processor;
        this.json = json;
        this.meters = meters;
    }

    @RabbitListener(queues = Messaging.SCREENING_REQUESTED_QUEUE)
    public void onMessage(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        UUID requestId = requestId(message, messageId);
        ScreeningProcessor.Result result;
        try {
            result = processor.process(messageId, requestId);
        } catch (ScreeningFailure failure) {
            count("dead_letter");
            throw new AmqpRejectAndDontRequeueException("Preselección " + requestId + " a la cola de fallidos", failure);
        }
        count(result.name().toLowerCase());
        if (result == ScreeningProcessor.Result.BUSY) {
            pause();
            throw new ImmediateRequeueAmqpException("Otra réplica procesa la preselección " + requestId);
        }
    }

    private UUID requestId(Message message, String messageId) {
        if (messageId == null) {
            count("malformed");
            throw new AmqpRejectAndDontRequeueException("Mensaje sin messageId");
        }
        try {
            JsonNode payload = json.readTree(message.getBody());
            return UUID.fromString(payload.get("screeningRequestId").asText());
        } catch (IOException | RuntimeException e) {
            count("malformed");
            log.error("Mensaje {} ilegible: va a la cola de fallidos", messageId);
            throw new AmqpRejectAndDontRequeueException("Mensaje ilegible", e);
        }
    }

    private void count(String outcome) {
        meters.counter("worksy.screening.messages", "outcome", outcome).increment();
    }

    private static void pause() {
        try {
            Thread.sleep(BUSY_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
