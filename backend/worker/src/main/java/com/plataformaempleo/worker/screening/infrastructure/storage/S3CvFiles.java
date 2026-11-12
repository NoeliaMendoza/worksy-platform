package com.plataformaempleo.worker.screening.infrastructure.storage;

import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.plataformaempleo.worker.screening.domain.port.out.CvFiles;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** Lee el PDF de MinIO con el mismo usuario de servicio limitado al bucket de CV. */
public class S3CvFiles implements CvFiles {

    private final S3Client s3;
    private final String bucket;

    public S3CvFiles(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return s3.getObjectAsBytes(b -> b.bucket(bucket).key(storageKey)).asByteArray();
        } catch (NoSuchKeyException e) {
            throw ScreeningFailure.permanent("El archivo del CV no existe en el almacenamiento");
        } catch (SdkException e) {
            // El SDK ya reintentó; si sigue fallando, el almacenamiento no está disponible.
            throw ScreeningFailure.transientFailure("Almacenamiento no disponible", e);
        }
    }
}
