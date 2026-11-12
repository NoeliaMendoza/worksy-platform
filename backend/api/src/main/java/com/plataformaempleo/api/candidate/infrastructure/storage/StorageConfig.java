package com.plataformaempleo.api.candidate.infrastructure.storage;

import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente S3. No contacta con el almacenamiento al arrancar: el bucket lo crea la
 * infraestructura (servicio {@code minio-init} de Docker Compose).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                // MinIO usa rutas (http://host/bucket/llave) en vez de subdominios por bucket.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // El diseño exige tiempo límite en toda llamada externa.
                .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(15))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)));
        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    /**
     * Firma enlaces temporales con la dirección pública: la firma incluye el host, así que debe ser
     * la misma con la que el navegador llegará al almacenamiento. Firmar no contacta con el servicio.
     */
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties props) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        String publicEndpoint = StringUtils.hasText(props.publicEndpoint()) ? props.publicEndpoint() : props.endpoint();
        if (StringUtils.hasText(publicEndpoint)) {
            builder.endpointOverride(URI.create(publicEndpoint));
        }
        return builder.build();
    }

    @Bean
    CvStorage cvStorage(S3Client s3, S3Presigner presigner, StorageProperties props) {
        return new S3CvStorage(s3, presigner, props.bucket());
    }

    /**
     * Sin credenciales la API arranca igual (el resto de funciones no depende del almacenamiento),
     * pero cada operación con archivos responderá "almacenamiento no disponible".
     */
    private static AwsCredentialsProvider credentials(StorageProperties props) {
        if (!StringUtils.hasText(props.accessKey()) || !StringUtils.hasText(props.secretKey())) {
            log.warn("Faltan STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY: la subida de CV no funcionará");
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
    }
}
