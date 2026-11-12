package com.plataformaempleo.api.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.common.events.Messaging;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Pedir la preselección de punta a punta: solicitudes + outbox en una transacción, publicación en
 * RabbitMQ real con confirmación del broker, e Idempotency-Key. Datos sintéticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"worksy.outbox.enabled=true", "worksy.outbox.poll-interval-ms=200"})
@Import(TestcontainersConfig.class)
@Testcontainers
class ScreeningRequestIntegrationTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-management-alpine"));

    private static final String PASSWORD = "frase-sintetica-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbit;

    @Test
    void pedirLaPreseleccionGuardaElEventoYLoPublicaUnaSolaVez() throws Exception {
        String admin = companyAdmin();
        String vacancyId = publishedVacancy(admin);
        Candidate ana = candidate(true);
        Candidate luis = candidate(true);
        apply(ana, vacancyId);
        apply(luis, vacancyId);
        share(luis, false); // Luis retiró el permiso: su CV no se procesa.
        String path = "/api/v1/companies/me/vacancies/" + vacancyId + "/screenings";

        var noKey = send(HttpMethod.POST, path, Map.of(), admin, null);
        assertThat(noKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code(noKey)).isEqualTo("idempotency.key.required");

        String key = UUID.randomUUID().toString();
        var accepted = send(HttpMethod.POST, path, Map.of(), admin, key);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode batch = json.readTree(accepted.getBody());
        assertThat(batch.get("requests")).hasSize(1);
        assertThat(batch.get("skipped").asInt()).isEqualTo(1);
        assertThat(batch.get("replayed").asBoolean()).isFalse();
        String requestId = batch.get("requests").get(0).get("id").asText();
        assertThat(batch.get("requests").get(0).get("status").asText()).isEqualTo("PENDING");

        // Mismo evento en el outbox, publicado y confirmado por el broker.
        UUID eventId = jdbc.queryForObject("select id from outbox_events where aggregate_id = ?", UUID.class,
                UUID.fromString(requestId));
        Message message = rabbit.receive(Messaging.SCREENING_REQUESTED_QUEUE, 15_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(EventTypes.SCREENING_REQUESTED);
        JsonNode payload = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(payload.get("screeningRequestId").asText()).isEqualTo(requestId);
        assertThat(payload.get("provider").asText()).isEqualTo("baseline");
        assertThat(payload.has("cvText")).isFalse();
        // El mensaje puede recibirse antes de que el publicador confirme y cierre su transaccion.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select published_at is not null from outbox_events where id = ?",
                        Boolean.class, eventId)).isTrue());

        // Repetir con la misma clave devuelve lo mismo y no crea más trabajo.
        var replay = send(HttpMethod.POST, path, Map.of(), admin, key);
        assertThat(json.readTree(replay.getBody()).get("replayed").asBoolean()).isTrue();
        assertThat(json.readTree(replay.getBody()).get("requests").get(0).get("id").asText()).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class)).isEqualTo(1);
        assertThat(rabbit.receive(Messaging.SCREENING_REQUESTED_QUEUE, 1_500)).isNull();

        // La misma clave con otra petición es un error; una clave nueva no duplica solicitudes.
        var reused = send(HttpMethod.POST, path, Map.of("applicationIds", List.of(UUID.randomUUID())), admin, key);
        assertThat(code(reused)).isEqualTo("idempotency.key.reused");
        var again = json.readTree(send(HttpMethod.POST, path, Map.of(), admin, UUID.randomUUID().toString()).getBody());
        assertThat(again.get("requests")).isEmpty();
        assertThat(again.get("skipped").asInt()).isEqualTo(2);

        JsonNode list = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void soloLaEmpresaDuenaPidePreseleccionYNoEnBorrador() throws Exception {
        String admin = companyAdmin();
        String vacancyId = publishedVacancy(admin);
        String path = "/api/v1/companies/me/vacancies/" + vacancyId + "/screenings";
        Candidate ana = candidate(true);

        assertThat(send(HttpMethod.POST, path, Map.of(), companyAdmin(), UUID.randomUUID().toString()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, path, Map.of(), ana.token(), UUID.randomUUID().toString()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String draft = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy("Borrador"), admin,
                null).getBody()).get("id").asText();
        var draftResponse = send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + draft + "/screenings", Map.of(),
                admin, UUID.randomUUID().toString());
        assertThat(code(draftResponse)).isEqualTo("screening.vacancy.draft");
    }

    // ---- utilidades ----

    private record Candidate(String email, String token, UUID userId, String cvId) {
    }

    private Candidate candidate(boolean shares) throws Exception {
        String email = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null, null);
        UUID userId = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        UUID cvId = UUID.randomUUID();
        byte[] hash = new byte[32];
        ThreadLocalRandom.current().nextBytes(hash);
        jdbc.update("insert into cv_documents (id, candidate_id, storage_key, sha256, mime, size_bytes, status, uploaded_at) "
                        + "values (?, ?, ?, ?, 'application/pdf', 1024, 'UPLOADED', ?)",
                cvId, userId, "cv/" + cvId + ".pdf", HexFormat.of().formatHex(hash), Timestamp.from(Instant.now()));
        Candidate c = new Candidate(email, login(email), userId, cvId.toString());
        share(c, shares);
        return c;
    }

    private void share(Candidate c, boolean on) {
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", on, "githubLink", false),
                c.token(), null);
    }

    private void apply(Candidate c, String vacancyId) {
        assertThat(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications", Map.of("cvId", c.cvId()),
                c.token(), null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private static Map<String, Object> vacancy(String title) {
        return Map.of("title", title, "description", "Equipo de pagos con Spring Boot y PostgreSQL.", "location", "Lima",
                "modality", "HIBRIDO", "criteria", List.of(Map.of("label", "Spring Boot",
                        "question", "¿Tiene experiencia profesional con Spring Boot?", "kind", "YES_NO")));
    }

    private String publishedVacancy(String admin) throws Exception {
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy("Backend Java"), admin,
                null).getBody()).get("id").asText();
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, admin, null);
        return id;
    }

    private String companyAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies",
                Map.of("companyName", "Empresa sintética", "email", email, "password", PASSWORD), null, null);
        return login(email);
    }

    private String login(String email) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody()).get("accessToken").asText();
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).get("code").asText();
    }

    private ResponseEntity<String> send(HttpMethod method, String path, Object body, String bearer, String idemKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        if (idemKey != null) {
            headers.set("Idempotency-Key", idemKey);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
