package com.plataformaempleo.api.candidate.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión al almacenamiento de objetos compatible con S3 (MinIO en desarrollo).
 * Las credenciales llegan por variables de entorno; nunca se escriben en el código.
 *
 * @param endpoint       URL con la que la API habla con el servicio (en Compose, {@code http://minio:9000})
 * @param publicEndpoint URL con la que el NAVEGADOR llega al servicio; firma los enlaces temporales.
 *                       Si está vacía se usa {@code endpoint}
 * @param region         región; MinIO acepta cualquiera
 * @param accessKey      usuario de servicio con permisos solo sobre el bucket de CV
 * @param secretKey      clave del usuario de servicio
 * @param bucket         bucket privado de CV
 */
@ConfigurationProperties("worksy.storage")
record StorageProperties(String endpoint, String publicEndpoint, String region, String accessKey, String secretKey,
                         String bucket) {
}
