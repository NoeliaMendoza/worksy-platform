package com.plataformaempleo.realtime.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Destino STOMP personal de cada usuario. Con el broker relay, {@code /topic/user.<id>} se convierte en la clave
 * de enrutamiento {@code user.<id>} del intercambio {@code amq.topic} de RabbitMQ: cualquier nodo del realtime
 * publica ahí y RabbitMQ lo entrega a las conexiones de esa persona en todos los nodos.
 */
public final class UserTopic {

    private static final String PREFIX = "/topic/user.";

    private UserTopic() {
    }

    public static String of(UUID userId) {
        return PREFIX + Objects.requireNonNull(userId, "userId");
    }

    /** Si el destino es exactamente el tema personal de ese usuario (y no el de otra persona). */
    public static boolean isOwnedBy(String destination, UUID userId) {
        return destination != null && userId != null && destination.equals(of(userId));
    }
}
