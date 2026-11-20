package com.plataformaempleo.common.events;

/**
 * Nombres de los eventos que viajan por el outbox y RabbitMQ.
 * Se centralizan para que productor y consumidor usen el mismo valor.
 */
public final class EventTypes {

    public static final String SCREENING_REQUESTED = "screening.requested";
    public static final String SCREENING_COMPLETED = "screening.completed";
    /** Mensaje nuevo en una conversación. Lleva solo ids (nunca el texto): quien lo recibe lo pide a la API. */
    public static final String MESSAGE_SENT = "message.sent";
    public static final String APPLICATION_STAGE_CHANGED = "application.stage.changed";
    public static final String INTERVIEW_CHANGED = "interview.changed";

    private EventTypes() {
    }
}
