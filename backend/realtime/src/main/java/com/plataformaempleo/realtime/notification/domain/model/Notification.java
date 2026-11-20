package com.plataformaempleo.realtime.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aviso que recibe el navegador por WebSocket. Solo lleva ids: el contenido lo pide el navegador a la API,
 * que vuelve a comprobar los permisos. No envía nombres, texto del CV ni mensajes por el broker.
 *
 * @param type          tipo de aviso: mensaje o cambio de etapa
 * @param applicationId postulación a la que pertenece la conversación
 * @param messageId     mensaje nuevo
 * @param stageChangeId evento del historial de etapas; null en avisos de mensajes
 */
public record Notification(String type, UUID applicationId, UUID messageId, Long stageChangeId, UUID interviewId) {

    public static final String MESSAGE_SENT = "message.sent";
    public static final String APPLICATION_STAGE_CHANGED = "application.stage.changed";

    public Notification {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(applicationId, "applicationId");
    }

    public static Notification messageSent(UUID applicationId, UUID messageId) {
        return new Notification(MESSAGE_SENT, applicationId, Objects.requireNonNull(messageId, "messageId"), null, null);
    }

    public static Notification stageChanged(UUID applicationId, long stageChangeId) {
        if (stageChangeId <= 0) throw new IllegalArgumentException("stageChangeId");
        return new Notification(APPLICATION_STAGE_CHANGED, applicationId, null, stageChangeId, null);
    }

    public static Notification interviewChanged(UUID applicationId, UUID interviewId) {
        return new Notification("interview.changed", applicationId, null, null, Objects.requireNonNull(interviewId));
    }
}
