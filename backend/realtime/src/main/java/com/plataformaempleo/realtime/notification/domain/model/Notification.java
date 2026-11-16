package com.plataformaempleo.realtime.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aviso que recibe el navegador por WebSocket. Solo lleva ids: el contenido lo pide el navegador a la API,
 * que vuelve a comprobar los permisos. Así ningún dato personal pasa por el broker ni por el realtime.
 *
 * @param type          tipo de aviso; hoy solo {@code message.sent}
 * @param applicationId postulación a la que pertenece la conversación
 * @param messageId     mensaje nuevo
 */
public record Notification(String type, UUID applicationId, UUID messageId) {

    public static final String MESSAGE_SENT = "message.sent";

    public Notification {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(applicationId, "applicationId");
    }

    public static Notification messageSent(UUID applicationId, UUID messageId) {
        return new Notification(MESSAGE_SENT, applicationId, Objects.requireNonNull(messageId, "messageId"));
    }
}
