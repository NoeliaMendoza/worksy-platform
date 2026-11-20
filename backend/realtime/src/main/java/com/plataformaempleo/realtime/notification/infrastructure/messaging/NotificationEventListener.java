package com.plataformaempleo.realtime.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import com.plataformaempleo.realtime.notification.application.NotificationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: recibe eventos de mensajes y etapas de la API (vía outbox) y los convierte en avisos.
 * Varias réplicas del realtime comparten la cola: cada evento lo atiende un solo nodo, y el broker relay
 * lo hace llegar a las conexiones de todos los nodos.
 */
@Component
class NotificationEventListener {

    private final NotificationService notifications;
    private final ObjectMapper json;

    NotificationEventListener(NotificationService notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @RabbitListener(queues = Messaging.REALTIME_NOTIFICATIONS_QUEUE)
    void onMessage(Message message) {
        try {
            JsonNode event = json.readTree(message.getBody());
            if (event == null || !event.isObject()) throw new IllegalArgumentException("event");
            List<UUID> recipients = new ArrayList<>();
            if (!event.required("recipientIds").isArray()) throw new IllegalArgumentException("recipientIds");
            event.required("recipientIds").forEach(id -> recipients.add(UUID.fromString(id.asText())));
            if (recipients.isEmpty()) throw new IllegalArgumentException("recipientIds");
            UUID applicationId = UUID.fromString(event.required("applicationId").asText());
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            if (EventTypes.MESSAGE_SENT.equals(routingKey)) {
                notifications.messageSent(applicationId, UUID.fromString(event.required("messageId").asText()), recipients);
            } else if (EventTypes.APPLICATION_STAGE_CHANGED.equals(routingKey)) {
                JsonNode changeId = event.required("stageChangeId");
                if (!changeId.isIntegralNumber() || !changeId.canConvertToLong()) throw new IllegalArgumentException("stageChangeId");
                notifications.stageChanged(applicationId, changeId.longValue(), recipients);
            } else if (EventTypes.INTERVIEW_CHANGED.equals(routingKey)) {
                notifications.interviewChanged(applicationId, UUID.fromString(event.required("interviewId").asText()), recipients);
            } else {
                throw new IllegalArgumentException("eventType");
            }
        } catch (IOException | IllegalArgumentException e) {
            // Mal formado: reintentarlo no lo arregla. Va a la cola de fallidos para revisarlo.
            throw new AmqpRejectAndDontRequeueException("Evento de notificación mal formado", e);
        }
    }
}
