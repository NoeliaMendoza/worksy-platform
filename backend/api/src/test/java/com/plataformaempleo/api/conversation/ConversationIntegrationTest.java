package com.plataformaempleo.api.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Conversaciones de punta a punta contra PostgreSQL real. Datos sintéticos (dominio .test). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ConversationIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laEmpresaEscribePrimeroYLaPersonaResponde() throws Exception {
        Account admin = companyAdmin("Andina Cargo");
        UUID recruiter = recruiterIn(admin.companyId());
        Account ana = candidate();
        share(ana, true);
        String applicationId = applyTo(publishedVacancy(admin, "Backend Java"), ana);
        String messages = "/api/v1/applications/" + applicationId + "/messages";

        // Antes de que la empresa escriba, la persona puede leer pero no escribir.
        JsonNode empty = body(send(HttpMethod.GET, messages, null, ana.token()));
        assertThat(empty.get("messages")).isEmpty();
        assertThat(empty.get("canWrite").asBoolean()).isFalse();
        var tooSoon = send(HttpMethod.POST, messages, Map.of("body", "Hola"), ana.token());
        assertThat(tooSoon.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code(tooSoon)).isEqualTo("message.company-first");
        assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, ana.token()))).isEmpty();

        // La empresa escribe.
        assertThat(body(send(HttpMethod.GET, messages, null, admin.token())).get("canWrite").asBoolean()).isTrue();
        var sent = send(HttpMethod.POST, messages, Map.of("body", "  Hola Ana, ¿podemos hablar el lunes?  "),
                admin.token());
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode first = body(sent);
        assertThat(first.get("body").asText()).isEqualTo("Hola Ana, ¿podemos hablar el lunes?");
        assertThat(first.get("fromCandidate").asBoolean()).isFalse();
        assertThat(first.get("mine").asBoolean()).isTrue();

        // El evento lleva solo ids: el texto nunca viaja por la cola.
        JsonNode event = json.readTree(jdbc.queryForObject(
                "select payload::text from outbox_events where event_type = 'message.sent' and aggregate_id = ?",
                String.class, UUID.fromString(applicationId)));
        assertThat(event.get("messageId").asText()).isEqualTo(first.get("id").asText());
        assertThat(ids(event.get("recipientIds"))).containsExactlyInAnyOrder(ana.userId(), recruiter);
        assertThat(event.toString()).doesNotContain("lunes");

        // La persona lo ve como no leído, responde y la conversación queda leída para ella.
        JsonNode inbox = body(send(HttpMethod.GET, "/api/v1/conversations", null, ana.token()));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("companyName").asText()).isEqualTo("Andina Cargo");
        assertThat(inbox.get(0).get("candidateEmail").isNull()).isTrue();
        assertThat(inbox.get(0).get("unread").asInt()).isEqualTo(1);

        JsonNode thread = body(send(HttpMethod.GET, messages, null, ana.token()));
        assertThat(thread.get("canWrite").asBoolean()).isTrue();
        assertThat(thread.get("messages").get(0).get("mine").asBoolean()).isFalse();
        assertThat(send(HttpMethod.POST, messages, Map.of("body", "Sí, el lunes a las 10."), ana.token())
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, ana.token())).get(0).get("unread").asInt())
                .isZero();

        // La empresa ve la respuesta como no leída, con el correo de la persona, hasta marcarla como leída.
        JsonNode companyInbox = body(send(HttpMethod.GET, "/api/v1/conversations", null, admin.token()));
        assertThat(companyInbox).hasSize(1);
        assertThat(companyInbox.get(0).get("candidateEmail").asText()).isEqualTo(ana.email());
        assertThat(companyInbox.get(0).get("lastMessage").asText()).isEqualTo("Sí, el lunes a las 10.");
        assertThat(companyInbox.get(0).get("lastFromCandidate").asBoolean()).isTrue();
        assertThat(companyInbox.get(0).get("unread").asInt()).isEqualTo(1);
        assertThat(send(HttpMethod.POST, messages + "/read", null, admin.token()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, admin.token())).get(0).get("unread")
                .asInt()).isZero();

        JsonNode both = body(send(HttpMethod.GET, messages, null, admin.token())).get("messages");
        assertThat(both).hasSize(2);
        assertThat(both.get(0).get("fromCandidate").asBoolean()).isFalse();
        assertThat(both.get(1).get("fromCandidate").asBoolean()).isTrue();
    }

    @Test
    void soloParticipanLaPersonaYSuEmpresa() throws Exception {
        Account adminA = companyAdmin("Empresa A");
        Account adminB = companyAdmin("Empresa B");
        Account ana = candidate();
        Account luis = candidate();
        share(ana, true);
        String applicationId = applyTo(publishedVacancy(adminA, "Vacante de A"), ana);
        String messages = "/api/v1/applications/" + applicationId + "/messages";
        send(HttpMethod.POST, messages, Map.of("body", "Hola"), adminA.token());

        for (Account outsider : List.of(adminB, luis)) {
            var read = send(HttpMethod.GET, messages, null, outsider.token());
            assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(code(read)).isEqualTo("application.not-found");
            assertThat(send(HttpMethod.POST, messages, Map.of("body", "Hola"), outsider.token()).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(send(HttpMethod.POST, messages + "/read", null, outsider.token()).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, outsider.token()))).isEmpty();
        }
        assertThat(send(HttpMethod.GET, messages, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(HttpMethod.GET, "/api/v1/applications/" + UUID.randomUUID() + "/messages", null,
                adminA.token()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sinPermisoDeCompartirLaEmpresaNoEscribeYRetirarBorraLaConversacion() throws Exception {
        Account admin = companyAdmin("Empresa C");
        Account ana = candidate();
        share(ana, true);
        String applicationId = applyTo(publishedVacancy(admin, "Vacante C"), ana);
        String messages = "/api/v1/applications/" + applicationId + "/messages";
        send(HttpMethod.POST, messages, Map.of("body", "Hola"), admin.token());

        share(ana, false);
        var blocked = send(HttpMethod.POST, messages, Map.of("body", "¿Sigues interesada?"), admin.token());
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(code(blocked)).isEqualTo("access.cv-not-shared");
        assertThat(body(send(HttpMethod.GET, messages, null, admin.token())).get("canWrite").asBoolean()).isFalse();
        assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, admin.token())).get(0)
                .get("candidateEmail").isNull()).isTrue();

        // Vacío, demasiado largo o sin cuerpo: 400.
        assertThat(code(send(HttpMethod.POST, messages, Map.of("body", "   "), ana.token())))
                .isEqualTo("message.body.empty");
        assertThat(code(send(HttpMethod.POST, messages, Map.of("body", "a".repeat(2001)), ana.token())))
                .isEqualTo("message.body.too-long");
        assertThat(send(HttpMethod.POST, messages, Map.of(), ana.token()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/applications/" + applicationId, null, ana.token())
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from application_messages where application_id = ?",
                Integer.class, UUID.fromString(applicationId))).isZero();
        assertThat(body(send(HttpMethod.GET, "/api/v1/conversations", null, admin.token()))).isEmpty();
    }

    @Test
    void paginaHaciaAtrasDeCincuentaEnCincuenta() throws Exception {
        Account admin = companyAdmin("Empresa D");
        Account ana = candidate();
        share(ana, true);
        String applicationId = applyTo(publishedVacancy(admin, "Vacante D"), ana);
        UUID adminId = jdbc.queryForObject("select id from users where email = ?", UUID.class, admin.email());
        Instant start = Instant.parse("2026-11-01T09:00:00Z");
        for (int i = 0; i < 55; i++) {
            jdbc.update("insert into application_messages (id, application_id, sender_id, from_candidate, body, sent_at) "
                            + "values (?, ?, ?, false, ?, ?)", UUID.randomUUID(), UUID.fromString(applicationId),
                    adminId, "Mensaje " + i, Timestamp.from(start.plusSeconds(i)));
        }
        String messages = "/api/v1/applications/" + applicationId + "/messages";

        JsonNode latest = body(send(HttpMethod.GET, messages, null, ana.token()));
        assertThat(latest.get("messages")).hasSize(50);
        assertThat(latest.get("hasMore").asBoolean()).isTrue();
        assertThat(latest.get("messages").get(0).get("body").asText()).isEqualTo("Mensaje 5");
        assertThat(latest.get("messages").get(49).get("body").asText()).isEqualTo("Mensaje 54");

        String before = latest.get("messages").get(0).get("sentAt").asText();
        JsonNode older = body(send(HttpMethod.GET, messages + "?before=" + before, null, ana.token()));
        assertThat(older.get("messages")).hasSize(5);
        assertThat(older.get("hasMore").asBoolean()).isFalse();
        assertThat(older.get("messages").get(0).get("body").asText()).isEqualTo("Mensaje 0");
    }

    // ---- utilidades ----

    private record Account(String email, String token, UUID userId, UUID companyId) {
    }

    private Account candidate() throws Exception {
        String email = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        UUID id = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        return new Account(email, login(email), id, null);
    }

    private Account companyAdmin(String companyName) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies",
                Map.of("companyName", companyName, "email", email, "password", PASSWORD), null);
        var row = jdbc.queryForMap("select id, company_id from users where email = ?", email);
        return new Account(email, login(email), (UUID) row.get("id"), (UUID) row.get("company_id"));
    }

    /** Otra persona del equipo; no necesita iniciar sesión, solo debe recibir los avisos. */
    private UUID recruiterIn(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, role, company_id) values (?, ?, 'h', 'RECRUITER', ?)",
                id, "recluta-" + id + "@ejemplo.test", companyId);
        return id;
    }

    private void share(Account c, boolean on) {
        assertThat(send(HttpMethod.PUT, "/api/v1/candidates/me/consents",
                Map.of("shareWithRecruiters", on, "githubLink", false), c.token()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String applyTo(String vacancyId, Account c) throws Exception {
        UUID cv = UUID.randomUUID();
        byte[] hash = new byte[32];
        ThreadLocalRandom.current().nextBytes(hash);
        jdbc.update("insert into cv_documents (id, candidate_id, storage_key, sha256, mime, size_bytes, status, uploaded_at) "
                        + "values (?, ?, ?, ?, 'application/pdf', 1024, 'UPLOADED', ?)",
                cv, c.userId(), "cv/" + cv + ".pdf", HexFormat.of().formatHex(hash), Timestamp.from(Instant.now()));
        var created = send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications",
                Map.of("cvId", cv.toString()), c.token());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return body(created).get("id").asText();
    }

    private String publishedVacancy(Account admin, String title) throws Exception {
        Map<String, Object> vacancy = Map.of("title", title + " " + UUID.randomUUID(),
                "description", "Equipo de pagos con Spring Boot.", "location", "Lima", "modality", "HIBRIDO",
                "criteria", List.of(Map.of("label", "Spring Boot",
                        "question", "¿Tiene experiencia profesional con Spring Boot?", "kind", "YES_NO")));
        String id = body(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy, admin.token()))
                .get("id").asText();
        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, admin.token())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private String login(String email) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return body(response).get("accessToken").asText();
    }

    private static List<UUID> ids(JsonNode array) {
        List<UUID> ids = new ArrayList<>();
        array.forEach(n -> ids.add(UUID.fromString(n.asText())));
        return ids;
    }

    private JsonNode body(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    private String code(ResponseEntity<String> response) throws Exception {
        return body(response).get("code").asText();
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
