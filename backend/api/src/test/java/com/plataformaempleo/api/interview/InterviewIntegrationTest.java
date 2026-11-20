package com.plataformaempleo.api.interview;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class InterviewIntegrationTest {
    private static final String PASSWORD = "Clave-sintetica-entrevistas";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    private record Fixture(String admin, String candidate, String other, String applicationId) { }

    @Test
    void agendaSinDuplicadosConPermisosYLecturasPorVersion() throws Exception {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        Map<String, Object> details = details(1);
        Map<String, Object> body = Map.of("id", id, "applicationId", f.applicationId(), "details", details);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from interview_events where interview_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(unread(f.admin())).isZero();
        assertThat(unread(f.other())).isZero();
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", body, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.GET, "/api/v1/interviews/" + id + "/history", null, f.other()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode listed = tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.candidate()));
        assertThat(listed.get("items")).hasSize(1);
        assertThat(listed.get("items").get(0).get("interview").get("zoneId").asText()).isEqualTo("America/Guayaquil");
        JsonNode event = json.readTree(jdbc.queryForObject("select payload::text from outbox_events where aggregate_id = ?", String.class, id));
        assertThat(event.has("place")).isFalse();
        assertThat(event.has("note")).isFalse();

        String path = "/api/v1/interviews/" + id;
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 0, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.admin())).isEqualTo(1);
        assertThat(unread(f.candidate())).isZero();
        assertThat(send(HttpMethod.PUT, path, Map.of("expectedVersion", 1, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 1), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 100), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 1, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var confirm = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 2, "status", "CONFIRMED"), f.candidate()));
        var decline = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 2, "status", "DECLINED"), f.candidate()));
        assertThat(List.of(confirm.get().getStatusCode().value(), decline.get().getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from interview_events where interview_id = ?", Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select stage from applications where id = ?", String.class, UUID.fromString(f.applicationId()))).isEqualTo("RECEIVED");
        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + f.applicationId(), null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from interviews where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void cancelarYRetirarConsentimientoNoFiltraDatosAOtraEmpresa() throws Exception {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(), "details", details(1)), f.admin());
        String path = "/api/v1/interviews/" + id;
        String foreignAdmin = company();
        assertThat(send(HttpMethod.GET, path + "/history", null, foreignAdmin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0), foreignAdmin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", false, "githubLink", false), f.candidate());
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.admin())).get("items")).isEmpty();
        assertThat(send(HttpMethod.PUT, path, Map.of("expectedVersion", 0, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, path + "/read", Map.of("expectedVersion", 0), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), f.candidate());
        assertThat(send(HttpMethod.POST, path + "/cancel", Map.of("expectedVersion", 0), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread(f.candidate())).isEqualTo(1);
        assertThat(tree(send(HttpMethod.GET, "/api/v1/interviews?view=history", null, f.candidate())).get("items")).hasSize(1);
        assertThat(send(HttpMethod.POST, path + "/response", Map.of("expectedVersion", 1, "status", "CONFIRMED"), f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> details(int days) {
        return Map.of("title", "Entrevista sintética", "startsAt", Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString(),
                "durationMinutes", 30, "zoneId", "America/Guayaquil", "mode", "ONLINE", "place", "https://meet.example.test/demo", "note", "Mensaje público sintético");
    }

    @Test
    void paginaSinOmitirCitasYBloqueaNuevasInvitacionesEnProcesosFinalizados() throws Exception {
        Fixture f = fixture();
        UUID first = UUID.randomUUID();
        Map<String, Object> firstDetails = details(1);
        for (int index = 0; index < 21; index++) {
            UUID id = index == 0 ? first : UUID.randomUUID();
            assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(),
                    "details", index == 0 ? firstDetails : details(index + 1)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        JsonNode pageOne = tree(send(HttpMethod.GET, "/api/v1/interviews", null, f.candidate()));
        JsonNode pageTwo = tree(send(HttpMethod.GET, "/api/v1/interviews?page=1", null, f.candidate()));
        assertThat(pageOne.get("items")).hasSize(20);
        assertThat(pageOne.get("hasMore").asBoolean()).isTrue();
        assertThat(pageTwo.get("items")).hasSize(1);
        assertThat(pageTwo.get("hasMore").asBoolean()).isFalse();
        var ids = new java.util.HashSet<String>();
        for (JsonNode item : pageOne.get("items")) ids.add(item.get("interview").get("id").asText());
        for (JsonNode item : pageTwo.get("items")) ids.add(item.get("interview").get("id").asText());
        assertThat(ids).hasSize(21);
        assertThat(unread(f.candidate())).isEqualTo(21);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", first, "applicationId", f.applicationId(), "details", details(30)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(send(HttpMethod.GET, "/api/v1/interviews?page=-1", null, f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("update applications set stage = 'HIRED' where id = ?", UUID.fromString(f.applicationId()));
        assertThat(send(HttpMethod.GET, "/api/v1/interviews/context/" + f.applicationId(), null, f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", UUID.randomUUID(), "applicationId", f.applicationId(), "details", details(1)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.PUT, "/api/v1/interviews/" + first, Map.of("expectedVersion", 0, "details", details(2)), f.admin()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.POST, "/api/v1/interviews/" + first + "/cancel", Map.of("expectedVersion", 0), f.admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Fixture fixture() throws Exception {
        String admin = company();
        String candidateEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        String otherEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        for (String email : List.of(candidateEmail, otherEmail)) send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        String candidate = login(candidateEmail);
        String other = login(otherEmail);
        UUID user = jdbc.queryForObject("select id from users where email = ?", UUID.class, candidateEmail);
        UUID cv = UUID.randomUUID();
        jdbc.update("insert into cv_documents (id,candidate_id,storage_key,sha256,mime,size_bytes,status,uploaded_at) values (?,?,?,?,'application/pdf',100,'UPLOADED',?)",
                cv, user, "cv/" + cv + ".pdf", cv.toString().replace("-", "").repeat(2), Timestamp.from(Instant.now()));
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), candidate);
        var createdVacancy = send(HttpMethod.POST, "/api/v1/companies/me/vacancies", Map.of("title", "Vacante sintética", "description", "Equipo de desarrollo backend Java.", "location", "Quito",
                "modality", "REMOTO", "criteria", List.of(Map.of("label", "Java", "question", "¿Tiene experiencia profesional con Java?", "kind", "YES_NO"))), admin);
        assertThat(createdVacancy.getStatusCode()).as(createdVacancy.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode vacancy = tree(createdVacancy);
        String vacancyId = vacancy.get("id").asText();
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + vacancyId + "/publish", null, admin);
        String application = tree(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications", Map.of("cvId", cv), candidate)).get("id").asText();
        return new Fixture(admin, candidate, other, application);
    }

    private String company() throws Exception {
        String email = "e-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies", Map.of("companyName", "Empresa sintética", "email", email, "password", PASSWORD), null);
        return login(email);
    }
    private String login(String email) throws Exception { return tree(send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)).get("accessToken").asText(); }
    private long unread(String token) throws Exception { return tree(send(HttpMethod.GET, "/api/v1/interviews/unread-count", null, token)).get("unread").asLong(); }
    private JsonNode tree(ResponseEntity<String> result) throws Exception { return json.readTree(result.getBody()); }
    private ResponseEntity<String> send(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
