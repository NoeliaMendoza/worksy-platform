package com.plataformaempleo.realtime.notification.infrastructure.stomp;

import com.plataformaempleo.realtime.notification.domain.model.Notification;
import com.plataformaempleo.realtime.notification.domain.model.UserTopic;
import com.plataformaempleo.realtime.notification.domain.port.out.NotificationPublisher;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Publica el aviso en el tema personal del usuario; el broker relay lo lleva a RabbitMQ. */
@Component
class StompNotificationPublisher implements NotificationPublisher {

    private final SimpMessagingTemplate stomp;

    StompNotificationPublisher(SimpMessagingTemplate stomp) {
        this.stomp = stomp;
    }

    @Override
    public void publish(UUID userId, Notification notification) {
        stomp.convertAndSend(UserTopic.of(userId), notification);
    }
}
