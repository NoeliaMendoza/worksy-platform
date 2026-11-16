package com.plataformaempleo.realtime.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Adaptador de entrada: recibe {@code message.sent} de la API (vía outbox) y lo convierte en avisos.
 * Varias réplicas del realtime comparten la cola: cada evento lo atiende un solo nodo, y el broker relay
 * lo hace llegar a las conexiones de todos los nodos.
 */
@Component
class MessageSentListener {

    private final NotificationService notifications;
    private final ObjectMapper json;

    MessageSentListener(NotificationService notifications, ObjectMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @RabbitListener(queues = Messaging.REALTIME_NOTIFICATIONS_QUEUE)
    void onMessage(Message message) {
        try {
            JsonNode event = json.readTree(message.getBody());
            List<UUID> recipients = new ArrayList<>();
            event.required("recipientIds").forEach(id -> recipients.add(UUID.fromString(id.asText())));
            notifications.messageSent(UUID.fromString(event.required("applicationId").asText()),
                    UUID.fromString(event.required("messageId").asText()), recipients);
        } catch (IOException | IllegalArgumentException e) {
            // Mal formado: reintentarlo no lo arregla. Va a la cola de fallidos para revisarlo.
            throw new AmqpRejectAndDontRequeueException("Evento message.sent mal formado", e);
        }
    }
}
