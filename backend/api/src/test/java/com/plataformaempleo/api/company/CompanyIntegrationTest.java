package com.plataformaempleo.api.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import com.plataformaempleo.api.company.domain.port.out.TeamInvitationMailer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Registro de empresa, invitación y aislamiento entre empresas contra PostgreSQL real. Datos sintéticos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, CompanyIntegrationTest.CapturingMailer.class})
class CompanyIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";
    static final List<String[]> INVITES = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class CapturingMailer {
        @Bean
        @Primary
        TeamInvitationMailer capturingInvitationMailer() {
            return (email, company, link, hours) -> INVITES.add(new String[] {email, link});
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void clear() {
        INVITES.clear();
    }

    @Test
    void registrarEmpresaInvitarYActivarUnReclutador() throws Exception {
        String admin = unique("admin");
        var registered = post("/api/v1/companies",
                Map.of("companyName", "Andina Cargo", "email", admin, "password", PASSWORD), null);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(registered.getBody());
        String companyId = body.get("company").get("id").asText();
        assertThat(body.get("admin").get("role").asText()).isEqualTo("COMPANY_ADMIN");
        assertThat(registered.getBody()).doesNotContain("password").doesNotContain("hash");

        String adminToken = login(admin, PASSWORD);
        var me = get("/api/v1/companies/me", adminToken);
        assertThat(json.readTree(me.getBody()).get("name").asText()).isEqualTo("Andina Cargo");

        String recruiter = unique("recluta");
        var invited = post("/api/v1/companies/me/members", Map.of("email", recruiter), adminToken);
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(INVITES).singleElement().satisfies(i -> assertThat(i[0]).isEqualTo(recruiter));

        // La cuenta invitada no tiene contraseña conocida hasta usar el enlace.
        assertThat(post("/api/v1/auth/login", Map.of("email", recruiter, "password", PASSWORD), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String token = URLDecoder.decode(INVITES.get(0)[1].split("token=")[1], StandardCharsets.UTF_8);
        assertThat(post("/api/v1/auth/password/reset", Map.of("token", token, "password", PASSWORD), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String recruiterToken = login(recruiter, PASSWORD);
        assertThat(json.readTree(get("/api/v1/companies/me", recruiterToken).getBody()).get("id").asText())
                .isEqualTo(companyId);
        JsonNode members = json.readTree(get("/api/v1/companies/me/members", recruiterToken).getBody());
        assertThat(members).hasSize(2);

        // Un reclutador no puede invitar.
        var forbidden = post("/api/v1/companies/me/members", Map.of("email", unique("x")), recruiterToken);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(forbidden.getBody()).get("code").asText()).isEqualTo("access.denied");
    }

    @Test
    void lasEmpresasEstanAisladasYUnCandidatoNoTieneEmpresa() throws Exception {
        String adminA = unique("a");
        String adminB = unique("b");
        post("/api/v1/companies", Map.of("companyName", "Empresa A", "email", adminA, "password", PASSWORD), null);
        post("/api/v1/companies", Map.of("companyName", "Empresa B", "email", adminB, "password", PASSWORD), null);
        String tokenA = login(adminA, PASSWORD);
        post("/api/v1/companies/me/members", Map.of("email", unique("recluta-a")), tokenA);

        JsonNode membersB = json.readTree(get("/api/v1/companies/me/members", login(adminB, PASSWORD)).getBody());
        assertThat(membersB).hasSize(1);
        assertThat(membersB.get(0).get("email").asText()).isEqualTo(adminB);

        String candidate = unique("candidata");
        post("/api/v1/auth/register", Map.of("email", candidate, "password", PASSWORD), null);
        assertThat(get("/api/v1/companies/me", login(candidate, PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/companies/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void erroresDeRegistroYClaveForaneaDeEmpresa() throws Exception {
        String admin = unique("dup");
        post("/api/v1/companies", Map.of("companyName", "Andina", "email", admin, "password", PASSWORD), null);
        var duplicate = post("/api/v1/companies", Map.of("companyName", "Otra", "email", admin, "password", PASSWORD), null);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var badName = post("/api/v1/companies", Map.of("companyName", "A", "email", unique("n"), "password", PASSWORD), null);
        assertThat(json.readTree(badName.getBody()).get("code").asText()).isEqualTo("company.name.invalid");
        // Antes daba 500: BCrypt no admite más de 72 bytes. Ahora es un 400 con un mensaje claro.
        var tooLong = post("/api/v1/companies", Map.of("companyName", "Larga", "email", unique("l"), "password", "ñ".repeat(40)), null);
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(tooLong.getBody()).get("code").asText()).isEqualTo("user.password.invalid");

        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '4' and success", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (id, email, password_hash, role, company_id) values (?, ?, 'h', 'RECRUITER', ?)",
                UUID.randomUUID(), unique("fk"), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- utilidades ----

    private String login(String email, String password) throws Exception {
        var response = post("/api/v1/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody()).get("accessToken").asText();
    }

    private ResponseEntity<String> post(String path, Object body, String bearer) {
        HttpHeaders headers = headers(bearer);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(bearer)), String.class);
    }

    private static HttpHeaders headers(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return headers;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@ejemplo.test";
    }
}
