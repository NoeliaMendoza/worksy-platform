package com.plataformaempleo.api.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
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

/** Pantalla "Candidatos" de punta a punta contra PostgreSQL real. Datos sintéticos (dominio .test). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CompanyApplicationsIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";
    private static final String LIST = "/api/v1/companies/me/applications";

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
    void listaTodasLasVacantesSinDatosDeQuienNoComparte() throws Exception {
        Admin admin = companyAdmin("Andina Cargo");
        String backend = publishedVacancy(admin.token(), "Backend Java");
        String datos = publishedVacancy(admin.token(), "Analista de datos");
        Candidate jose = candidate();
        Candidate luis = candidate();
        share(jose, true);
        share(luis, true);
        String joseApp = id(apply(jose, backend));
        String luisApp = id(apply(luis, datos));
        name(jose, "José Pérez");
        share(luis, false);

        JsonNode page = body(send(HttpMethod.GET, LIST, null, admin.token()));
        assertThat(page.get("total").asLong()).isEqualTo(2);
        assertThat(ids(page)).containsExactly(luisApp, joseApp); // la más reciente primero

        JsonNode shared = find(page, joseApp);
        assertThat(shared.get("shared").asBoolean()).isTrue();
        assertThat(shared.get("candidateName").asText()).isEqualTo("José Pérez");
        assertThat(shared.get("candidateEmail").asText()).isEqualTo(jose.email());
        assertThat(shared.get("vacancyTitle").asText()).startsWith("Backend Java");
        assertThat(shared.get("stage").asText()).isEqualTo("RECEIVED");
        assertThat(shared.has("overallScore")).as("nunca la puntuación del modelo").isFalse();

        JsonNode hidden = find(page, luisApp);
        assertThat(hidden.get("shared").asBoolean()).isFalse();
        for (String field : List.of("candidateName", "candidateEmail", "cvId", "stage", "screeningStatus", "nextInterviewAt")) {
            assertThat(hidden.get(field).isNull()).as(field).isTrue();
        }
        assertThat(hidden.get("vacancyTitle").asText()).startsWith("Analista de datos");

        // Filtro por vacante.
        assertThat(ids(body(send(HttpMethod.GET, LIST + "?vacancyId=" + datos, null, admin.token()))))
                .containsExactly(luisApp);

        // Detalle de una postulación.
        JsonNode detail = body(send(HttpMethod.GET, LIST + "/" + joseApp, null, admin.token()));
        assertThat(detail.get("candidateEmail").asText()).isEqualTo(jose.email());
    }

    @Test
    void buscaSinAcentosYSoloEnDatosCompartidos() throws Exception {
        Admin admin = companyAdmin("Empresa de búsqueda");
        String vacancy = publishedVacancy(admin.token(), "Diseño de producto");
        Candidate jose = candidate();
        Candidate oculta = candidate();
        share(jose, true);
        share(oculta, true);
        String joseApp = id(apply(jose, vacancy));
        id(apply(oculta, vacancy));
        name(jose, "José Pérez");
        share(oculta, false);

        assertThat(ids(body(send(HttpMethod.GET, LIST + "?q=jose perez", null, admin.token())))).containsExactly(joseApp);
        assertThat(ids(body(send(HttpMethod.GET, LIST + "?q=PÉREZ", null, admin.token())))).containsExactly(joseApp);
        // El correo de quien no comparte sus datos no sirve para encontrarla.
        String hiddenUser = oculta.email().substring(0, oculta.email().indexOf('@'));
        assertThat(body(send(HttpMethod.GET, LIST + "?q=" + hiddenUser, null, admin.token())).get("total").asLong()).isZero();
        // El título de la vacante sí, para las dos.
        assertThat(body(send(HttpMethod.GET, LIST + "?q=diseno", null, admin.token())).get("total").asLong()).isEqualTo(2);
        // Los comodines de LIKE se buscan como texto.
        assertThat(body(send(HttpMethod.GET, LIST + "?q=%25", null, admin.token())).get("total").asLong()).isZero();
    }

    @Test
    void filtraPorEtapaYMuestraLaProximaEntrevista() throws Exception {
        Admin admin = companyAdmin("Empresa de etapas");
        String vacancy = publishedVacancy(admin.token(), "Soporte");
        Candidate ana = candidate();
        Candidate eva = candidate();
        share(ana, true);
        share(eva, true);
        String anaApp = id(apply(ana, vacancy));
        String evaApp = id(apply(eva, vacancy));
        assertThat(send(HttpMethod.PATCH, "/api/v1/companies/me/vacancies/" + vacancy + "/applications/" + anaApp + "/progress",
                Map.of("expectedStage", "RECEIVED", "stage", "REVIEW", "message", "Revisando tu CV."), admin.token())
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        Instant soon = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        interview(anaApp, soon, "INVITED");
        interview(anaApp, soon.plus(1, ChronoUnit.DAYS), "CONFIRMED");
        interview(evaApp, soon, "CANCELLED");

        JsonNode review = body(send(HttpMethod.GET, LIST + "?stage=REVIEW", null, admin.token()));
        assertThat(ids(review)).containsExactly(anaApp);
        assertThat(Instant.parse(review.get("items").get(0).get("nextInterviewAt").asText())).isEqualTo(soon);
        JsonNode received = body(send(HttpMethod.GET, LIST + "?stage=RECEIVED", null, admin.token()));
        assertThat(ids(received)).containsExactly(evaApp);
        assertThat(received.get("items").get(0).get("nextInterviewAt").isNull()).as("cancelada no cuenta").isTrue();
    }

    @Test
    void paginaYValidaLosParametros() throws Exception {
        Admin admin = companyAdmin("Empresa paginada");
        String vacancy = publishedVacancy(admin.token(), "Ventas");
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Candidate c = candidate();
            share(c, true);
            created.add(id(apply(c, vacancy)));
        }
        JsonNode second = body(send(HttpMethod.GET, LIST + "?size=2&page=1", null, admin.token()));
        assertThat(second.get("total").asLong()).isEqualTo(3);
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(ids(second)).containsExactly(created.get(0));

        for (String bad : List.of("?size=0", "?size=101", "?page=-1")) {
            var response = send(HttpMethod.GET, LIST + bad, null, admin.token());
            assertThat(response.getStatusCode()).as(bad).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code(response)).isEqualTo("pagination.invalid");
        }
        assertThat(send(HttpMethod.GET, LIST + "?stage=NADA", null, admin.token()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cadaEmpresaVeSoloLoSuyo() throws Exception {
        Admin a = companyAdmin("Empresa A");
        Admin b = companyAdmin("Empresa B");
        Candidate ana = candidate();
        share(ana, true);
        String app = id(apply(ana, publishedVacancy(a.token(), "Vacante de A")));

        assertThat(body(send(HttpMethod.GET, LIST, null, b.token())).get("total").asLong()).isZero();
        var foreign = send(HttpMethod.GET, LIST + "/" + app, null, b.token());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(code(foreign)).isEqualTo("application.not-found");
        assertThat(send(HttpMethod.GET, LIST, null, ana.token()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.GET, LIST, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(HttpMethod.GET, LIST + "/" + UUID.randomUUID(), null, a.token()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- utilidades ----

    private record Candidate(String email, String token, UUID userId) {
    }

    private record Admin(String token) {
    }

    private Candidate candidate() throws Exception {
        String email = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        UUID id = jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
        return new Candidate(email, login(email), id);
    }

    private Admin companyAdmin(String companyName) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies",
                Map.of("companyName", companyName, "email", email, "password", PASSWORD), null);
        return new Admin(login(email));
    }

    private void share(Candidate c, boolean on) {
        assertThat(send(HttpMethod.PUT, "/api/v1/candidates/me/consents",
                Map.of("shareWithRecruiters", on, "githubLink", false), c.token()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Nombre del perfil (la API de perfil no es parte de esta prueba). */
    private void name(Candidate c, String fullName) {
        assertThat(jdbc.update("update candidate_profiles set full_name = ? where user_id = ?", fullName, c.userId()))
                .isEqualTo(1);
    }

    private void interview(String applicationId, Instant startsAt, String status) {
        jdbc.update("insert into interviews (id, application_id, version, title, starts_at, duration_minutes, zone_id, mode, "
                        + "place, status, updated_at) values (?, ?, 0, 'Entrevista', ?, 30, 'America/Lima', 'ONLINE', "
                        + "'https://reunion.ejemplo.test', ?, now())",
                UUID.randomUUID(), UUID.fromString(applicationId), Timestamp.from(startsAt), status);
    }

    private ResponseEntity<String> apply(Candidate c, String vacancyId) {
        UUID cv = UUID.randomUUID();
        byte[] hash = new byte[32];
        ThreadLocalRandom.current().nextBytes(hash);
        jdbc.update("insert into cv_documents (id, candidate_id, storage_key, sha256, mime, size_bytes, status, uploaded_at) "
                        + "values (?, ?, ?, ?, 'application/pdf', 1024, 'UPLOADED', ?)",
                cv, c.userId(), "cv/" + cv + ".pdf", HexFormat.of().formatHex(hash), Timestamp.from(Instant.now()));
        var created = send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications",
                Map.of("cvId", cv.toString()), c.token());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created;
    }

    private String publishedVacancy(String admin, String title) throws Exception {
        Map<String, Object> vacancy = Map.of("title", title + " " + UUID.randomUUID(),
                "description", "Equipo con Spring Boot.", "location", "Lima", "modality", "HIBRIDO",
                "criteria", List.of(Map.of("label", "Spring Boot",
                        "question", "¿Tiene experiencia profesional con Spring Boot?", "kind", "YES_NO")));
        String id = body(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy, admin)).get("id").asText();
        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }

    private String login(String email) throws Exception {
        var response = send(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return body(response).get("accessToken").asText();
    }

    private String id(ResponseEntity<String> response) throws Exception {
        return body(response).get("id").asText();
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("applicationId").asText()));
        return ids;
    }

    private static JsonNode find(JsonNode page, String applicationId) {
        for (JsonNode item : page.get("items")) {
            if (item.get("applicationId").asText().equals(applicationId)) {
                return item;
            }
        }
        throw new AssertionError("No está " + applicationId);
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
