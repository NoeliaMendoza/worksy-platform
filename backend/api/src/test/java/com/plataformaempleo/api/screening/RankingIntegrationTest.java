package com.plataformaempleo.api.screening;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class RankingIntegrationTest {
    private static final String PASSWORD = "frase-sintetica-larga";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void rankingOrdenadoConDecisionPersistidaYConsentimiento() throws Exception {
        String admin = companyAdmin();
        String vacancyId = publishedVacancy(admin);
        Candidate ana = candidate(true);
        Candidate luis = candidate(true);
        Candidate eva = candidate(true);
        apply(ana, vacancyId);
        apply(luis, vacancyId);
        apply(eva, vacancyId);
        String base = "/api/v1/companies/me/vacancies/" + vacancyId;
        assertThat(send(HttpMethod.POST, base + "/screenings", Map.of(), admin, UUID.randomUUID().toString())
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        finish(ana, "0.9000", "COMPLETED");
        finish(luis, "0.5000", "NEEDS_REVIEW");
        String path = base + "/ranking";
        JsonNode ranking = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(ranking.get("candidates")).hasSize(2);
        assertThat(ranking.get("pending").asInt()).isEqualTo(1);
        assertThat(ranking.get("received").asInt()).isEqualTo(3);
        JsonNode first = ranking.get("candidates").get(0);
        assertThat(first.get("email").asText()).isEqualTo(ana.email());
        assertThat(first.get("overallScore").asDouble()).isEqualTo(0.9);
        assertThat(first.get("answers").get(0).get("answer").asText()).isEqualTo("Sí");
        assertThat(first.get("decision").isNull()).isTrue();
        assertThat(ranking.get("candidates").get(1).get("status").asText()).isEqualTo("NEEDS_REVIEW");
        String decisionPath = path + "/" + first.get("applicationId").asText() + "/decision";
        assertThat(send(HttpMethod.PUT, decisionPath, Map.of("decision", "preseleccionado"), admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode reloaded = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(reloaded.get("candidates").get(0).get("decision").asText()).isEqualTo("preseleccionado");
        assertThat(send(HttpMethod.PUT, decisionPath, Map.of("decision", "inventada"), admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PUT, decisionPath, Map.of(), admin, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        reloaded = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(reloaded.get("candidates").get(0).get("decision").isNull()).isTrue();

        assertThat(send(HttpMethod.GET, path, null, companyAdmin(), null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.GET, path, null, ana.token(), null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.PUT, decisionPath, Map.of("decision", "descartado"), companyAdmin(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        share(ana, false);
        reloaded = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(reloaded.get("candidates")).hasSize(1);
        assertThat(reloaded.toString()).doesNotContain(ana.email());
        assertThat(send(HttpMethod.PUT, decisionPath, Map.of("decision", "descartado"), admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Las solicitudes fallidas no se convierten en candidatos con puntuación cero.
        jdbc.update("update screening_requests set status = 'FAILED' where cv_id = ?", UUID.fromString(eva.cvId()));
        reloaded = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(reloaded.get("failed").asInt()).isEqualTo(1);
        assertThat(reloaded.get("pending").asInt()).isZero();
        Candidate noRequest = candidate(true);
        apply(noRequest, vacancyId);
        reloaded = json.readTree(send(HttpMethod.GET, path, null, admin, null).getBody());
        assertThat(reloaded.get("notRequested").asInt()).isEqualTo(1);
        UUID pendingId = jdbc.queryForObject("select id from applications where candidate_id = ?", UUID.class, noRequest.userId());
        assertThat(send(HttpMethod.PUT, path + "/" + pendingId + "/decision", Map.of("decision", "descartado"), admin, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void finish(Candidate candidate, String score, String status) {
        UUID request = jdbc.queryForObject("select id from screening_requests where cv_id = ?", UUID.class,
                UUID.fromString(candidate.cvId()));
        jdbc.update("update screening_requests set status = ?, overall_score = cast(? as numeric), confidence = 0.8500, "
                + "model_version = 'baseline-prueba' where id = ?", status, score, request);
        jdbc.update("insert into screening_answers (id, request_id, question_key, choice, probability, score) "
                + "values (?, ?, 'q1', 'Sí', 0.8500, 1.0000)", UUID.randomUUID(), request);
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
