package com.plataformaempleo.api.candidate.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión al almacenamiento de objetos compatible con S3 (MinIO en desarrollo).
 * Las credenciales llegan por variables de entorno; nunca se escriben en el código.
 *
 * @param endpoint  URL del servicio (en S3 gestionado puede quedar vacía para usar la de AWS)
 * @param region    región; MinIO acepta cualquiera
 * @param accessKey usuario de servicio con permisos solo sobre el bucket de CV
 * @param secretKey clave del usuario de servicio
 * @param bucket    bucket privado de CV
 */
@ConfigurationProperties("worksy.storage")
record StorageProperties(String endpoint, String region, String accessKey, String secretKey, String bucket) {
}
