package com.plataformaempleo.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * El worker de punta a punta contra PostgreSQL, RabbitMQ, Redis y MinIO reales, y un servicio de
 * inferencia de prueba. El esquema sale de las migraciones de la API. Datos sintéticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WorkerScreeningIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    /** Misma imagen que Docker Compose, compilada desde el código fuente. */
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("plataforma-empleo/minio:RELEASE.2025-10-15T17-29-55Z")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "minio-sintetico")
            .withEnv("MINIO_ROOT_PASSWORD", "secreto-sintetico-minio")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    private static final String BUCKET = "cv-pruebas";
    private static HttpServer inference;
    /** 200 = responde bien; 503 = caído. */
    private static final AtomicInteger INFERENCE_STATUS = new AtomicInteger(200);
    private static final AtomicInteger INFERENCE_CALLS = new AtomicInteger();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("worksy.storage.endpoint", WorkerScreeningIntegrationTest::minioUrl);
        registry.add("worksy.storage.access-key", () -> "minio-sintetico");
        registry.add("worksy.storage.secret-key", () -> "secreto-sintetico-minio");
        registry.add("worksy.storage.bucket", () -> BUCKET);
        registry.add("worksy.inference-url", () -> "http://127.0.0.1:" + inference.getAddress().getPort());
    }

    @BeforeAll
    static void startInferenceAndBucket() throws IOException {
        inference = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        inference.createContext("/v1/decide", exchange -> {
            INFERENCE_CALLS.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            int status = INFERENCE_STATUS.get();
            byte[] body = (status == 200
                    ? "{\"answers\":{\"q1\":{\"choice\":\"Sí\",\"probability\":0.91}},\"model_version\":\"baseline-bow-1\",\"latency_ms\":4}"
                    : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        inference.start();
        try (S3Client s3 = s3()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
    }

    @AfterAll
    static void stopInference() {
        inference.stop(0);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private TestRestTemplate http;

    @BeforeEach
    void reset() {
        INFERENCE_STATUS.set(200);
        INFERENCE_CALLS.set(0);
    }

    @Test
    void procesaElMensajeGuardaElResultadoYNoRepiteConUnDuplicado() throws Exception {
        UUID requestId = seedRequest(pdfWith("Desarrolladora backend con experiencia en Spring Boot y Java"));
        String messageId = UUID.randomUUID().toString();

        publish(messageId, requestId);
        String status = awaitFinalStatus(requestId);

        assertThat(status).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select model_version from screening_requests where id = ?", String.class, requestId))
                .isEqualTo("baseline-bow-1");
        assertThat(jdbc.queryForObject("select overall_score from screening_requests where id = ?", Double.class, requestId))
                .isEqualTo(1.0);
        assertThat(jdbc.queryForObject("select choice from screening_answers where request_id = ?", String.class, requestId))
                .isEqualTo("Sí");
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and event_type = ?",
                Integer.class, requestId, EventTypes.SCREENING_COMPLETED)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from processed_messages where message_id = ?", Integer.class,
                messageId)).isEqualTo(1);

        // RabbitMQ (o el outbox) entrega el mismo mensaje otra vez: no se repite el trabajo.
        publish(messageId, requestId);
        Thread.sleep(1_500);
        assertThat(INFERENCE_CALLS).hasValue(1);
        assertThat(jdbc.queryForObject("select attempts from screening_requests where id = ?", Integer.class, requestId))
                .isEqualTo(1);

        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unPdfSinTextoQuedaFailedSinIrALaColaDeFallidos() throws Exception {
        UUID requestId = seedRequest(pdfWith());

        publish(UUID.randomUUID().toString(), requestId);

        assertThat(awaitFinalStatus(requestId)).isEqualTo("FAILED");
        assertThat(INFERENCE_CALLS).hasValue(0);
        assertThat(rabbit.receive(Messaging.SCREENING_REQUESTED_DLQ, 1_000)).isNull();
    }

    @Test
    void siLaInferenciaSigueCaidaQuedaFailedYElMensajeVaALaColaDeFallidos() throws Exception {
        INFERENCE_STATUS.set(503);
        UUID requestId = seedRequest(pdfWith("Analista de datos con Python"));
        String messageId = UUID.randomUUID().toString();

        publish(messageId, requestId);

        assertThat(awaitFinalStatus(requestId)).isEqualTo("FAILED");
        var dead = rabbit.receive(Messaging.SCREENING_REQUESTED_DLQ, 10_000);
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(messageId);
        assertThat(INFERENCE_CALLS).hasValue(3);
    }

    // ---- utilidades ----

    private void publish(String messageId, UUID requestId) {
        String payload = "{\"screeningRequestId\":\"" + requestId + "\",\"provider\":\"baseline\"}";
        rabbit.send(Messaging.EVENTS_EXCHANGE, EventTypes.SCREENING_REQUESTED,
                MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                        .setMessageId(messageId)
                        .setContentType("application/json")
                        .build());
    }

    private String awaitFinalStatus(UUID requestId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = jdbc.queryForObject("select status from screening_requests where id = ?", String.class,
                    requestId);
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                return status;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("La preselección " + requestId + " no terminó a tiempo");
    }

    /** Siembra empresa, vacante, candidata, CV (en MinIO) y una solicitud PENDING. */
    private UUID seedRequest(byte[] pdf) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID company = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        UUID vacancy = UUID.randomUUID();
        UUID cv = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        jdbc.update("insert into companies (id, name, created_at) values (?, 'Empresa sintética', ?)", company, now);
        jdbc.update("insert into users (id, email, password_hash, role, company_id, created_at) values (?, ?, 'h', 'COMPANY_ADMIN', ?, ?)",
                admin, "admin-" + admin + "@ejemplo.test", company, now);
        jdbc.update("insert into users (id, email, password_hash, role, created_at) values (?, ?, 'h', 'CANDIDATE', ?)",
                candidate, "cand-" + candidate + "@ejemplo.test", now);
        jdbc.update("""
                insert into vacancies (id, company_id, created_by, title, description, location, modality, status, criteria,
                                       created_at, updated_at, published_at)
                values (?, ?, ?, 'Backend Java', 'Equipo de pagos con Spring Boot.', 'Lima', 'HIBRIDO', 'PUBLISHED',
                        cast(? as jsonb), ?, ?, ?)""",
                vacancy, company, admin,
                "[{\"key\":\"q1\",\"label\":\"Spring Boot\",\"question\":\"¿Tiene experiencia con Spring Boot?\","
                        + "\"kind\":\"YES_NO\",\"options\":[\"Sí\",\"No\"]}]",
                now, now, now);
        byte[] hash = new byte[32];
        ThreadLocalRandom.current().nextBytes(hash);
        String key = "cv/" + cv + ".pdf";
        jdbc.update("insert into cv_documents (id, candidate_id, storage_key, sha256, mime, size_bytes, status, uploaded_at) "
                + "values (?, ?, ?, ?, 'application/pdf', ?, 'UPLOADED', ?)", cv, candidate, key, HexFormat.of().formatHex(hash),
                pdf.length, now);
        try (S3Client s3 = s3()) {
            s3.putObject(b -> b.bucket(BUCKET).key(key).contentType("application/pdf"), RequestBody.fromBytes(pdf));
        }
        jdbc.update("insert into applications (id, vacancy_id, candidate_id, cv_id, applied_at) values (?, ?, ?, ?, ?)",
                application, vacancy, candidate, cv, now);
        jdbc.update("insert into screening_requests (id, vacancy_id, application_id, cv_id, provider, status, requested_by, "
                + "requested_at, updated_at) values (?, ?, ?, ?, 'baseline', 'PENDING', ?, ?, ?)",
                request, vacancy, application, cv, admin, now, now);
        return request;
    }

    private static byte[] pdfWith(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = 700;
                for (String line : lines) {
                    content.beginText();
                    content.newLineAtOffset(72, y);
                    content.showText(line);
                    content.endText();
                    y -= 18;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String minioUrl() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static S3Client s3() {
        return S3Client.builder()
                .endpointOverride(URI.create(minioUrl()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minio-sintetico", "secreto-sintetico-minio")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
