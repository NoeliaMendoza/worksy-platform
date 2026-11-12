package com.plataformaempleo.worker.screening.domain.port.out;

import java.time.Duration;

/**
 * Puerto de salida: bloqueo con caducidad para que dos réplicas no procesen la misma solicitud a la vez.
 * Es una optimización: la corrección la garantiza la base de datos (processed_messages y estados).
 */
public interface ProcessingLock {

    boolean tryAcquire(String key, Duration ttl);

    void release(String key);
}
