package com.plataformaempleo.api.candidate.infrastructure.storage;

import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import com.plataformaempleo.common.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** Adaptador del puerto {@link CvStorage} sobre la API de S3 (MinIO u otro compatible). */
class S3CvStorage implements CvStorage {

    private static final Logger log = LoggerFactory.getLogger(S3CvStorage.class);

    private final S3Client s3;
    private final String bucket;

    S3CvStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) content.length),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw unavailable("guardar", key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            // S3 responde con éxito aunque la llave no exista: el borrado es idempotente.
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw unavailable("borrar", key, e);
        }
    }

    private static DomainException unavailable(String action, String key, SdkException cause) {
        log.error("Fallo al {} el objeto {} en el almacenamiento", action, key, cause);
        return new DomainException("storage.unavailable",
                "No pudimos procesar el archivo en este momento. Intenta de nuevo en unos minutos");
    }
}
