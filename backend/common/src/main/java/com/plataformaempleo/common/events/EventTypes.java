package com.plataformaempleo.common.events;

/**
 * Nombres de los eventos que viajan por el outbox y RabbitMQ.
 * Se centralizan para que productor y consumidor usen el mismo valor.
 */
public final class EventTypes {

    public static final String SCREENING_REQUESTED = "screening.requested";
    public static final String SCREENING_COMPLETED = "screening.completed";

    private EventTypes() {
    }
}
