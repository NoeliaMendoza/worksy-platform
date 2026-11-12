package com.plataformaempleo.api.shared.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Publicador del outbox.
 *
 * @param enabled    false desactiva el envío (p. ej., en pruebas que no levantan RabbitMQ)
 * @param batchSize  eventos por ronda; VALOR SUPUESTO 100
 * @param confirmTimeoutMs espera máxima de la confirmación del broker por ronda
 */
@ConfigurationProperties("worksy.outbox")
record OutboxProperties(boolean enabled, int batchSize, long confirmTimeoutMs) {

    OutboxProperties {
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (confirmTimeoutMs <= 0) {
            confirmTimeoutMs = 5_000;
        }
    }
}
