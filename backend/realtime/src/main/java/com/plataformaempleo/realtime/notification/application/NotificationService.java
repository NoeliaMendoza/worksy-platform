package com.plataformaempleo.realtime.notification.application;

import com.plataformaempleo.realtime.notification.domain.model.Notification;
import com.plataformaempleo.realtime.notification.domain.port.out.NotificationPublisher;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Reparte los avisos de mensajes nuevos entre los destinatarios.
 * Si un evento llega dos veces (entrega "al menos una vez"), el navegador recibe dos avisos y vuelve a pedir la
 * conversación: es inofensivo, así que no hace falta guardar ids procesados.
 */
public class NotificationService {

    private final NotificationPublisher publisher;

    public NotificationService(NotificationPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher);
    }

    public void messageSent(UUID applicationId, UUID messageId, Collection<UUID> recipients) {
        Notification notification = Notification.messageSent(applicationId, messageId);
        for (UUID recipient : new LinkedHashSet<>(recipients)) {
            publisher.publish(recipient, notification);
        }
    }

    public void stageChanged(UUID applicationId, long stageChangeId, Collection<UUID> recipients) {
        Notification notification = Notification.stageChanged(applicationId, stageChangeId);
        for (UUID recipient : new LinkedHashSet<>(recipients)) {
            publisher.publish(recipient, notification);
        }
    }

    public void interviewChanged(UUID applicationId, UUID interviewId, Collection<UUID> recipients) {
        Notification notification = Notification.interviewChanged(applicationId, interviewId);
        for (UUID recipient : new LinkedHashSet<>(recipients)) publisher.publish(recipient, notification);
    }
}
