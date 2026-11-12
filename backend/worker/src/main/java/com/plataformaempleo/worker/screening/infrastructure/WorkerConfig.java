package com.plataformaempleo.worker.screening.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import com.plataformaempleo.worker.screening.application.ScreeningProcessor;
import com.plataformaempleo.worker.screening.infrastructure.inference.HttpDecisionProvider;
import com.plataformaempleo.worker.screening.infrastructure.lock.RedisProcessingLock;
import com.plataformaempleo.worker.screening.infrastructure.messaging.ScreeningRequestedListener;
import com.plataformaempleo.worker.screening.infrastructure.persistence.JdbcScreeningStore;
import com.plataformaempleo.worker.screening.infrastructure.storage.S3CvFiles;
import com.plataformaempleo.worker.screening.infrastructure.text.PdfBoxTextExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Conecta el caso de uso del worker con sus adaptadores. */
@Configuration
@EnableConfigurationProperties(WorkerConfig.WorkerProperties.class)
class WorkerConfig {

    /**
     * @param reviewThreshold VALOR SUPUESTO 0,6: bajo esta confianza la solicitud queda NEEDS_REVIEW
     * @param inferenceUrl    servicio de inferencia (en F4, Nginx para aplicar balanceo y canario)
     * @param inferenceTimeout tiempo límite por llamada (5 s, sección 5 del diseño)
     * @param retryBackoff    espera base entre reintentos
     */
    @ConfigurationProperties("worksy")
    record WorkerProperties(double reviewThreshold, String inferenceUrl, Duration inferenceTimeout,
                            Duration retryBackoff, Storage storage) {

        record Storage(String endpoint, String region, String accessKey, String secretKey, String bucket) {
        }
    }

    /** Misma topología que declara la API: el primero que arranca la crea. */
    @Bean
    Declarables screeningTopology() {
        TopicExchange events = new TopicExchange(Messaging.EVENTS_EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE, true, false);
        Queue requested = QueueBuilder.durable(Messaging.SCREENING_REQUESTED_QUEUE)
                .quorum()
                .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(Messaging.SCREENING_REQUESTED_DLQ)
                .build();
        Queue failed = QueueBuilder.durable(Messaging.SCREENING_REQUESTED_DLQ).quorum().build();
        Binding requestedBinding = BindingBuilder.bind(requested).to(events).with(EventTypes.SCREENING_REQUESTED);
        Binding failedBinding = BindingBuilder.bind(failed).to(deadLetters).with(Messaging.SCREENING_REQUESTED_DLQ);
        return new Declarables(events, deadLetters, requested, failed, requestedBinding, failedBinding);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JdbcScreeningStore screeningStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        return new JdbcScreeningStore(jdbc, json, clock, "screening-worker");
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(WorkerProperties props) {
        var s = props.storage();
        var builder = S3Client.builder()
                .region(Region.of(s.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(blankToDash(s.accessKey()), blankToDash(s.secretKey()))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(15))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)));
        if (StringUtils.hasText(s.endpoint())) {
            builder.endpointOverride(URI.create(s.endpoint()));
        }
        return builder.build();
    }

    @Bean
    HttpDecisionProvider decisionProvider(WorkerProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.inferenceTimeout());
        factory.setReadTimeout(props.inferenceTimeout());
        RestClient http = RestClient.builder()
                .baseUrl(props.inferenceUrl())
                .requestFactory(factory)
                .build();
        return new HttpDecisionProvider(http, props.retryBackoff());
    }

    @Bean
    ScreeningProcessor screeningProcessor(JdbcScreeningStore store, S3Client s3, HttpDecisionProvider decisions,
                                          StringRedisTemplate redis, WorkerProperties props) {
        return new ScreeningProcessor(store, new S3CvFiles(s3, props.storage().bucket()), new PdfBoxTextExtractor(),
                decisions, new RedisProcessingLock(redis), props.reviewThreshold());
    }

    @Bean
    ScreeningRequestedListener screeningRequestedListener(ScreeningProcessor processor, ObjectMapper json,
                                                          MeterRegistry meters) {
        return new ScreeningRequestedListener(processor, json, meters);
    }

    /** El SDK no admite credenciales vacías; sin ellas cada lectura fallará como "almacenamiento no disponible". */
    private static String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
