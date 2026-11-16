package com.plataformaempleo.realtime.notification.domain.port.out;

import com.plataformaempleo.realtime.notification.domain.model.Notification;
import java.util.UUID;

/** Puerto de salida: entrega un aviso a las conexiones abiertas de una persona (en cualquier nodo). */
public interface NotificationPublisher {

    void publish(UUID userId, Notification notification);
}
