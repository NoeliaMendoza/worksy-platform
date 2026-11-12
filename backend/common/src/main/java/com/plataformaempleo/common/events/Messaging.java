package com.plataformaempleo.common.events;

/**
 * Topología de RabbitMQ compartida por quien publica (API) y quien consume (worker).
 * Ambos la declaran de forma idempotente: el primero que arranca la crea.
 */
public final class Messaging {

    /** Intercambio de eventos de dominio. La clave de enrutamiento es el tipo de evento. */
    public static final String EVENTS_EXCHANGE = "worksy.events";
    /** Intercambio de mensajes fallidos: recibe lo que el consumidor rechaza definitivamente. */
    public static final String DEAD_LETTER_EXCHANGE = "worksy.events.dlx";

    public static final String SCREENING_REQUESTED_QUEUE = "screening.requested";
    public static final String SCREENING_REQUESTED_DLQ = "screening.requested.dlq";

    /** Cabecera con el tipo de evento (además de la clave de enrutamiento). */
    public static final String EVENT_TYPE_HEADER = "x-event-type";

    private Messaging() {
    }
}
