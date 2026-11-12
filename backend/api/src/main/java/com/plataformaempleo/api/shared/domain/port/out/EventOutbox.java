package com.plataformaempleo.api.shared.domain.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de salida del patrón Outbox. Guarda el evento en la misma transacción que el cambio
 * de negocio; un publicador lo envía después al broker. Nunca publica directamente.
 */
public interface EventOutbox {

    /** Devuelve el id del evento, que viaja como messageId para que el consumidor detecte duplicados. */
    UUID append(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload);
}
