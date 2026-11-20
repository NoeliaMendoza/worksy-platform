package com.plataformaempleo.api.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Postulaciones de punta a punta contra PostgreSQL real. Los CV se registran directamente en la base
 * (la subida a MinIO ya se prueba en CandidateCvIntegrationTest). Datos sintéticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class JobApplicationIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void useHttpClientWithPatchSupport() {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    @Test
    void avisosPersistentesSoloPropiosYLecturaLimitadaALoQueSeMostro() throws Exception {
        String admin = companyAdmin("Empresa de avisos");
        String vacancyId = publishedVacancy(admin, "Vacante con avisos");
        Candidate ana = candidate();
        Candidate otra = candidate();
        share(ana, true);
        String id = json.readTree(apply(ana, vacancyId, cvOf(ana)).getBody()).get("id").asText();
        String progressPath = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications/" + id + "/progress";
        String readPath = "/api/v1/candidates/me/applications/" + id + "/progress/read";
        String inboxPath = "/api/v1/candidates/me/application-updates";
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody()).get("unread").asLong()).isZero();
        var review = send(HttpMethod.PATCH, progressPath, Map.of("expectedStage", "RECEIVED", "stage", "REVIEW"), admin);
        JsonNode history = json.readTree(review.getBody()).get("history");
        long firstId = history.get(0).get("id").asLong();
        long reviewId = history.get(1).get("id").asLong();
        JsonNode unread = json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody());
        assertThat(unread.get("unread").asLong()).isEqualTo(1);
        assertThat(unread.get("updates").get(0).get("applicationId").asText()).isEqualTo(id);
        assertThat(unread.get("updates").get(0).get("stage").asText()).isEqualTo("REVIEW");
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, otra.token()).getBody()).get("unread").asLong()).isZero();
        assertThat(send(HttpMethod.GET, inboxPath, null, admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode event = json.readTree(jdbc.queryForObject("select payload::text from outbox_events where aggregate_id = ? "
                + "and event_type = 'application.stage.changed'", String.class, UUID.fromString(id)));
        assertThat(event.get("stageChangeId").asLong()).isEqualTo(reviewId);
        assertThat(event.get("recipientIds")).hasSize(1);
        assertThat(event.get("recipientIds").get(0).asText()).isEqualTo(ana.userId().toString());
        assertThat(event.has("message")).isFalse();
        assertThat(event.has("stage")).isFalse();
        send(HttpMethod.GET, "/api/v1/candidates/me/applications/" + id + "/progress", null, ana.token());
        var interview = send(HttpMethod.PATCH, progressPath, Map.of("expectedStage", "REVIEW", "stage", "INTERVIEW"), admin);
        long interviewId = json.readTree(interview.getBody()).get("history").get(2).get("id").asLong();
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", reviewId), otra.token()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", reviewId), admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", Long.MAX_VALUE), ana.token()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", reviewId), ana.token()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody()).get("unread").asLong()).isEqualTo(1);
        send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", firstId), ana.token());
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody()).get("unread").asLong()).isEqualTo(1);
        share(ana, false);
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", interviewId), ana.token()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.POST, readPath, Map.of("lastSeenEventId", interviewId), ana.token()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody()).get("unread").asLong()).isZero();
        share(ana, true);
        send(HttpMethod.PATCH, progressPath, Map.of("expectedStage", "INTERVIEW", "stage", "OFFER"), admin);
        send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + id, null, ana.token());
        assertThat(json.readTree(send(HttpMethod.GET, inboxPath, null, ana.token()).getBody()).get("unread").asLong()).isZero();
    }

    @Test
    void etapasConHistorialPermisosYControlDeCambiosSimultaneos() throws Exception {
        String admin = companyAdmin("Empresa de seguimiento");
        String vacancyId = publishedVacancy(admin, "Vacante de seguimiento");
        Candidate ana = candidate();
        Candidate otra = candidate();
        share(ana, true);
        String id = json.readTree(apply(ana, vacancyId, cvOf(ana)).getBody()).get("id").asText();
        String companyPath = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications/" + id + "/progress";
        String candidatePath = "/api/v1/candidates/me/applications/" + id + "/progress";
        Map<String, String> review = Map.of("expectedStage", "RECEIVED", "stage", "REVIEW", "message", "Estamos revisando tu experiencia.");

        JsonNode initial = json.readTree(send(HttpMethod.GET, candidatePath, null, ana.token()).getBody());
        assertThat(initial.get("stage").asText()).isEqualTo("RECEIVED");
        assertThat(initial.get("history")).hasSize(1);
        assertThat(send(HttpMethod.GET, candidatePath, null, otra.token()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.PATCH, companyPath, review, ana.token()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.PATCH, companyPath, review, companyAdmin("Otra empresa")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.PATCH, companyPath, Map.of("expectedStage", "RECEIVED", "stage", "HIRED"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PATCH, companyPath, Map.of("expectedStage", "RECEIVED", "stage", "REVIEW", "message", "x".repeat(501)), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Dos cambios válidos compiten por la misma etapa: solo uno puede escribir el historial.
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> send(HttpMethod.PATCH, companyPath, review, admin));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> send(HttpMethod.PATCH, companyPath, review, admin));
        assertThat(List.of(first.get().getStatusCode().value(), second.get().getStatusCode().value()))
                .containsExactlyInAnyOrder(200, 409);
        JsonNode changed = json.readTree(send(HttpMethod.GET, candidatePath, null, ana.token()).getBody());
        assertThat(changed.get("stage").asText()).isEqualTo("REVIEW");
        assertThat(changed.get("history")).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? "
                + "and event_type = 'application.stage.changed'", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(changed.get("history").get(1).get("message").asText()).isEqualTo(review.get("message"));
        assertThat(changed.get("history").get(1).has("changedBy")).isFalse();
        JsonNode mine = json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/applications", null, ana.token()).getBody());
        assertThat(mine.get(0).get("stage").asText()).isEqualTo("REVIEW");
        JsonNode milestones = mine.get(0).get("milestones");
        assertThat(milestones).hasSize(2);
        assertThat(milestones.get(0).get("stage").asText()).isEqualTo("RECEIVED");
        assertThat(milestones.get(1).get("stage").asText()).isEqualTo("REVIEW");
        assertThat(milestones.get(1).get("changedAt")).isEqualTo(changed.get("history").get(1).get("changedAt"));
        assertThat(milestones.get(1).has("message")).isFalse();
        assertThat(milestones.get(1).has("changedBy")).isFalse();
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/applications", null, otra.token()).getBody())).isEmpty();

        share(ana, false);
        assertThat(send(HttpMethod.GET, companyPath, null, admin).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.PATCH, companyPath, Map.of("expectedStage", "REVIEW", "stage", "INTERVIEW"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.GET, candidatePath, null, ana.token()).getStatusCode()).isEqualTo(HttpStatus.OK);
        share(ana, true);
        String previous = "REVIEW";
        for (String next : List.of("INTERVIEW", "OFFER", "HIRED")) {
            assertThat(send(HttpMethod.PATCH, companyPath, Map.of("expectedStage", previous, "stage", next), admin)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            previous = next;
        }
        assertThat(send(HttpMethod.PATCH, companyPath, Map.of("expectedStage", "HIRED", "stage", "REVIEW"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(send(HttpMethod.GET, candidatePath, null, ana.token()).getBody()).get("history")).hasSize(5);
        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + id, null, ana.token()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from application_stage_history where application_id = ?",
                Integer.class, UUID.fromString(id))).isZero();

        share(otra, true);
        String rejectedId = json.readTree(apply(otra, vacancyId, cvOf(otra)).getBody()).get("id").asText();
        String rejectedPath = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications/" + rejectedId + "/progress";
        assertThat(send(HttpMethod.PATCH, rejectedPath, Map.of("expectedStage", "RECEIVED", "stage", "REJECTED",
                "message", "Gracias por participar en este proceso."), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.PATCH, rejectedPath, Map.of("expectedStage", "REJECTED", "stage", "REVIEW"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postularConsultarYRetirarRespetandoElPermisoDeCompartir() throws Exception {
        String admin = companyAdmin("Andina Cargo");
        String vacancyId = publishedVacancy(admin, "Backend Java " + UUID.randomUUID());
        Candidate ana = candidate();
        String cvId = cvOf(ana);

        // Sin permiso de compartir el CV no se puede postular.
        var noConsent = apply(ana, vacancyId, cvId);
        assertThat(noConsent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code(noConsent)).isEqualTo("application.consent.required");

        share(ana, true);
        var created = apply(ana, vacancyId, cvId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String applicationId = json.readTree(created.getBody()).get("id").asText();

        var again = apply(ana, vacancyId, cvId);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(again)).isEqualTo("application.duplicate");

        // La persona ve su postulación con la vacante y la empresa.
        JsonNode mine = json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/applications", null, ana.token).getBody());
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("companyName").asText()).isEqualTo("Andina Cargo");
        assertThat(mine.get(0).get("vacancyStatus").asText()).isEqualTo("PUBLISHED");

        // La empresa ve quién se postuló mientras la persona lo permita.
        String path = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications";
        JsonNode list = json.readTree(send(HttpMethod.GET, path, null, admin).getBody());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("shared").asBoolean()).isTrue();
        assertThat(list.get(0).get("candidateEmail").asText()).isEqualTo(ana.email);
        assertThat(list.get(0).get("cvId").asText()).isEqualTo(cvId);

        share(ana, false);
        JsonNode hidden = json.readTree(send(HttpMethod.GET, path, null, admin).getBody());
        assertThat(hidden.get(0).get("shared").asBoolean()).isFalse();
        assertThat(hidden.get(0).get("candidateEmail").isNull()).isTrue();
        assertThat(hidden.get(0).get("cvId").isNull()).isTrue();

        // Un CV en una postulación no se puede eliminar hasta retirarla.
        var inUse = send(HttpMethod.DELETE, "/api/v1/candidates/me/cv/" + cvId, null, ana.token);
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(inUse)).isEqualTo("cv.in-use");

        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + applicationId, null, ana.token)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/applications", null, ana.token).getBody()))
                .isEmpty();
        assertThat(json.readTree(send(HttpMethod.GET, path, null, admin).getBody())).isEmpty();
    }

    @Test
    void soloSePostulaAVacantesPublicadasYConUnCvPropio() throws Exception {
        String admin = companyAdmin("Empresa A");
        String published = publishedVacancy(admin, "Publicada " + UUID.randomUUID());
        String draft = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies",
                vacancy("Borrador"), admin).getBody()).get("id").asText();
        Candidate ana = candidate();
        Candidate luis = candidate();
        share(ana, true);
        String cvAna = cvOf(ana);
        String cvLuis = cvOf(luis);

        assertThat(apply(ana, draft, cvAna).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(apply(ana, UUID.randomUUID().toString(), cvAna).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var foreignCv = apply(ana, published, cvLuis);
        assertThat(foreignCv.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(code(foreignCv)).isEqualTo("cv.not-found");

        String applicationId = json.readTree(apply(ana, published, cvAna).getBody()).get("id").asText();
        // Nadie más puede retirar la postulación de otra persona.
        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + applicationId, null, luis.token)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void otraEmpresaYLosCandidatosNoVenLasPostulaciones() throws Exception {
        String adminA = companyAdmin("Empresa A");
        String adminB = companyAdmin("Empresa B");
        String vacancyId = publishedVacancy(adminA, "Vacante de A " + UUID.randomUUID());
        Candidate ana = candidate();
        share(ana, true);
        apply(ana, vacancyId, cvOf(ana));

        String path = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications";
        assertThat(send(HttpMethod.GET, path, null, adminB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.GET, path, null, ana.token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications",
                Map.of("cvId", UUID.randomUUID().toString()), adminA).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications",
                Map.of("cvId", UUID.randomUUID().toString()), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- utilidades ----

    private record Candidate(String email, String token, UUID userId) {
    }

    private Candidate candidate() throws Exception {
        String email = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        UUID id = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        return new Candidate(email, login(email), id);
    }

    /** Registra un CV sintético del candidato sin pasar por el almacenamiento. */
    private String cvOf(Candidate c) {
        UUID id = UUID.randomUUID();
        byte[] hash = new byte[32];
        ThreadLocalRandom.current().nextBytes(hash);
        jdbc.update("insert into cv_documents (id, candidate_id, storage_key, sha256, mime, size_bytes, status, uploaded_at) "
                        + "values (?, ?, ?, ?, 'application/pdf', 1024, 'UPLOADED', ?)",
                id, c.userId(), "cv/" + id + ".pdf", HexFormat.of().formatHex(hash), Timestamp.from(Instant.now()));
        return id.toString();
    }

    private void share(Candidate c, boolean on) {
        assertThat(send(HttpMethod.PUT, "/api/v1/candidates/me/consents",
                Map.of("shareWithRecruiters", on, "githubLink", false), c.token()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> apply(Candidate c, String vacancyId, String cvId) {
        return send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications", Map.of("cvId", cvId), c.token());
    }

    private static Map<String, Object> vacancy(String title) {
        return Map.of("title", title, "description", "Equipo de pagos con Spring Boot.", "location", "Lima",
                "modality", "HIBRIDO", "criteria", List.of(Map.of("label", "Spring Boot",
                        "question", "¿Tiene experiencia profesional con Spring Boot?", "kind", "YES_NO")));
    }

    private String publishedVacancy(String admin, String title) throws Exception {
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy(title), admin)
                .getBody()).get("id").asText();
        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private String companyAdmin(String companyName) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies",
                Map.of("companyName", companyName, "email", email, "password", PASSWORD), null);
        return login(email);
    }

    private String login(String email) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody()).get("accessToken").asText();
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).get("code").asText();
    }

    private ResponseEntity<String> send(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
