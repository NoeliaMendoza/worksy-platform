package com.plataformaempleo.api.candidate.domain.port.out;

/**
 * Puerto de salida para guardar los archivos de CV fuera de la base de datos
 * (MinIO en desarrollo; un almacenamiento S3 gestionado en producción).
 * Los fallos se reportan como {@code DomainException} con código {@code storage.unavailable}.
 */
public interface CvStorage {

    void put(String key, byte[] content, String contentType);

    /** Borra el objeto. Es idempotente: borrar una llave que no existe no es un error. */
    void delete(String key);
}
