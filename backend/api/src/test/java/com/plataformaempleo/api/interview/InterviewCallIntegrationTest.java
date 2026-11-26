package com.plataformaempleo.api.interview;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
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

/** Sala de videollamada: quién entra, cuándo y qué permite el token de sala. Datos sintéticos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class InterviewCallIntegrationTest {
    private static final String PASSWORD = "Clave-sintetica-videollamada";
    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private record Fixture(String admin, String candidate, String other, String applicationId, UUID candidateId, UUID adminId) { }

    @Test
    void soloEntranLaPersonaCandidataYQuienAgendoDentroDelHorario() throws Exception {
        Fixture f = fixture();
        UUID id = schedule(f, "VIDEO", 10);
        String call = "/api/v1/interviews/" + id + "/call";

        JsonNode status = tree(send(HttpMethod.GET, call, f.candidate()));
        assertThat(status.get("joinable").asBoolean()).isTrue();
        assertThat(status.get("present")).isEmpty();

        JsonNode candidate = tree(send(HttpMethod.POST, call + "/join", f.candidate()));
        assertThat(candidate.get("role").asText()).isEqualTo("CANDIDATE");
        assertThat(candidate.get("peers")).hasSize(1);
        assertThat(candidate.get("peers").get(0).get("userId").asText()).isEqualTo(f.adminId().toString());
        assertThat(candidate.get("peers").get(0).get("present").asBoolean()).isFalse();
        Map<String, Object> claims = claims(candidate.get("roomToken").asText());
        assertThat(claims).containsEntry("iss", "worksy-call").containsEntry("sub", f.candidateId().toString())
                .containsEntry("sid", candidate.get("sessionId").asText());
        assertThat(claims.get("aud").toString()).contains("worksy-realtime");
        assertThat(claims.get("peers").toString()).contains(f.adminId().toString());

        JsonNode recruiter = tree(send(HttpMethod.POST, call + "/join", f.admin()));
        assertThat(recruiter.get("role").asText()).isEqualTo("INTERVIEWER");
        assertThat(recruiter.get("sessionId").asText()).isEqualTo(candidate.get("sessionId").asText());
        assertThat(recruiter.get("peers").get(0).get("present").asBoolean()).isTrue();

        // Nadie más: otra persona candidata, otra empresa, ni alguien de la empresa que no está a cargo.
        assertThat(send(HttpMethod.POST, call + "/join", f.other()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, call + "/join", company()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        jdbc.update("delete from interview_participants where interview_id = ?", id);
        assertThat(send(HttpMethod.POST, call + "/join", f.admin()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // El token de sala no sirve como token de acceso a la API.
        var misuse = http.exchange("/api/v1/interviews", HttpMethod.GET, new HttpEntity<>(headers(candidate.get("roomToken").asText())), String.class);
        assertThat(misuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Salir deja constancia sin cerrar la sala para las demás personas.
        assertThat(send(HttpMethod.POST, call + "/leave", f.candidate()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from interview_call_participants where left_at is not null and session_id = ?",
                Integer.class, UUID.fromString(candidate.get("sessionId").asText()))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from interview_call_sessions where interview_id = ? and closed_at is null", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void laSalaRespetaModalidadHorarioYEstadoYTerminarLaCierraParaTodos() throws Exception {
        Fixture f = fixture();
        UUID online = schedule(f, "ONLINE", 5 * 60);
        assertThat(code(send(HttpMethod.POST, "/api/v1/interviews/" + online + "/call/join", f.candidate()))).contains("interview.invalid");
        UUID later = schedule(f, "VIDEO", 2 * 24 * 60);
        JsonNode closed = tree(send(HttpMethod.GET, "/api/v1/interviews/" + later + "/call", f.candidate()));
        assertThat(closed.get("joinable").asBoolean()).isFalse();
        assertThat(closed.get("unavailableReason").asText()).contains("15 minutos antes");
        assertThat(send(HttpMethod.POST, "/api/v1/interviews/" + later + "/call/join", f.candidate()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        UUID id = schedule(f, "VIDEO", 10);
        String call = "/api/v1/interviews/" + id + "/call";
        // Dos personas que entran a la vez caen en la misma sala.
        var a = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, call + "/join", f.candidate()));
        var b = CompletableFuture.supplyAsync(() -> send(HttpMethod.POST, call + "/join", f.admin()));
        assertThat(tree(a.get()).get("sessionId").asText()).isEqualTo(tree(b.get()).get("sessionId").asText());
        assertThat(send(HttpMethod.POST, call + "/end", f.candidate()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.POST, call + "/end", f.admin()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from interview_call_sessions where interview_id = ? and closed_at is null", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from interview_call_participants p join interview_call_sessions s on s.id = p.session_id "
                + "where s.interview_id = ? and p.left_at is null", Integer.class, id)).isZero();
        // Si sigue en horario, se puede volver a entrar: se abre una sala nueva.
        assertThat(send(HttpMethod.POST, call + "/join", f.candidate()).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Con el proceso cerrado o la entrevista cancelada, la sala no está disponible.
        jdbc.update("update applications set stage = 'REJECTED' where id = ?", UUID.fromString(f.applicationId()));
        assertThat(code(send(HttpMethod.POST, call + "/join", f.candidate()))).contains("interview.closed");
        // No se guarda contenido audiovisual ni señales: solo sesiones y asistencia.
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name like 'interview_call%' "
                + "and column_name in ('sdp', 'candidate', 'ip', 'media')", Integer.class)).isZero();
    }

    private UUID schedule(Fixture f, String mode, int minutesFromNow) {
        UUID id = UUID.randomUUID();
        Map<String, Object> details = Map.of("title", "Entrevista sintética", "startsAt",
                Instant.now().plus(minutesFromNow, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toString(),
                "durationMinutes", 30, "zoneId", "America/Guayaquil", "mode", mode,
                "place", mode.equals("ONLINE") ? "https://meet.example.test/demo" : "", "note", "Mensaje público sintético");
        var created = send(HttpMethod.POST, "/api/v1/interviews", Map.of("id", id, "applicationId", f.applicationId(), "details", details), f.admin());
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        return id;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> claims(String token) throws Exception {
        return json.readValue(new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8), Map.class);
    }

    private Fixture fixture() throws Exception {
        String adminEmail = "e-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies", Map.of("companyName", "Empresa sintética", "email", adminEmail, "password", PASSWORD), null);
        String admin = login(adminEmail);
        String candidateEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        String otherEmail = "c-" + UUID.randomUUID() + "@ejemplo.test";
        for (String email : List.of(candidateEmail, otherEmail)) send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        String candidate = login(candidateEmail);
        UUID user = jdbc.queryForObject("select id from users where email = ?", UUID.class, candidateEmail);
        UUID cv = UUID.randomUUID();
        jdbc.update("insert into cv_documents (id,candidate_id,storage_key,sha256,mime,size_bytes,status,uploaded_at) values (?,?,?,?,'application/pdf',100,'UPLOADED',?)",
                cv, user, "cv/" + cv + ".pdf", cv.toString().replace("-", "").repeat(2), Timestamp.from(Instant.now()));
        sendJson(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), candidate);
        var vacancy = tree(sendJson(HttpMethod.POST, "/api/v1/companies/me/vacancies", Map.of("title", "Vacante sintética", "description", "Equipo de desarrollo backend Java.",
                "location", "Quito", "modality", "REMOTO", "criteria", List.of(Map.of("label", "Java", "question", "¿Tiene experiencia profesional con Java?", "kind", "YES_NO"))), admin));
        String vacancyId = vacancy.get("id").asText();
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + vacancyId + "/publish", admin);
        String application = tree(sendJson(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications", Map.of("cvId", cv), candidate)).get("id").asText();
        UUID adminId = jdbc.queryForObject("select id from users where email = ?", UUID.class, adminEmail);
        return new Fixture(admin, candidate, login(otherEmail), application, user, adminId);
    }

    private String company() throws Exception {
        String email = "e-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies", Map.of("companyName", "Otra empresa sintética", "email", email, "password", PASSWORD), null);
        return login(email);
    }

    private String login(String email) throws Exception {
        return tree(send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)).get("accessToken").asText();
    }

    private String code(ResponseEntity<String> response) throws Exception { return tree(response).path("code").asText(); }
    private JsonNode tree(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }
    private ResponseEntity<String> send(HttpMethod method, String path, String token) { return sendJson(method, path, null, token); }
    private ResponseEntity<String> send(HttpMethod method, String path, Object body, String token) { return sendJson(method, path, body, token); }

    private ResponseEntity<String> sendJson(HttpMethod method, String path, Object body, String token) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(token)), String.class);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
