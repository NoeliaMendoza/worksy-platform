package com.plataformaempleo.common.events;

/**
 * Topología de RabbitMQ compartida por quien publica (API) y quien consume (worker y realtime).
 * Ambos la declaran de forma idempotente: el primero que arranca la crea.
 */
public final class Messaging {

    /** Intercambio de eventos de dominio. La clave de enrutamiento es el tipo de evento. */
    public static final String EVENTS_EXCHANGE = "worksy.events";
    /** Intercambio de mensajes fallidos: recibe lo que el consumidor rechaza definitivamente. */
    public static final String DEAD_LETTER_EXCHANGE = "worksy.events.dlx";

    public static final String SCREENING_REQUESTED_QUEUE = "screening.requested";
    public static final String SCREENING_REQUESTED_DLQ = "screening.requested.dlq";

    /** Avisos para el servicio de tiempo real (hoy, {@code message.sent}). */
    public static final String REALTIME_NOTIFICATIONS_QUEUE = "realtime.notifications";
    public static final String REALTIME_NOTIFICATIONS_DLQ = "realtime.notifications.dlq";
    /**
     * VALOR SUPUESTO: un aviso sin entregar caduca a los 5 minutos. Es solo un "hay novedades": si el realtime
     * estuvo caído más tiempo, el navegador ve los mensajes al recargar la bandeja. API y realtime declaran la
     * cola con este mismo valor (RabbitMQ rechaza declaraciones con argumentos distintos).
     */
    public static final int REALTIME_NOTIFICATIONS_TTL_MS = 300_000;

    /** Cabecera con el tipo de evento (además de la clave de enrutamiento). */
    public static final String EVENT_TYPE_HEADER = "x-event-type";

    private Messaging() {
    }
}
