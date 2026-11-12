package com.plataformaempleo.api.candidate.domain.port.out;

import java.net.URI;
import java.time.Duration;

/**
 * Puerto de salida para guardar los archivos de CV fuera de la base de datos
 * (MinIO en desarrollo; un almacenamiento S3 gestionado en producción).
 * Los fallos se reportan como {@code DomainException} con código {@code storage.unavailable}.
 */
public interface CvStorage {

    void put(String key, byte[] content, String contentType);

    /** Borra el objeto. Es idempotente: borrar una llave que no existe no es un error. */
    void delete(String key);

    /**
     * Enlace de solo lectura que caduca: el navegador descarga el PDF directamente del almacenamiento,
     * sin pasar los bytes por la API (sección 14.3 del diseño).
     */
    URI temporaryReadUrl(String key, Duration validity);
}
