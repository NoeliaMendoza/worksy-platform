package com.plataformaempleo.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Flujo HTTP completo de autenticación contra PostgreSQL real (Testcontainers). Datos sintéticos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AuthApiIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Test
    void flywayAplicoLaMigracionDeTokens() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '2' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void registroInicioDeSesionUsuarioActualRefrescoYCierre() throws Exception {
        String email = unique();

        var register = post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody()).doesNotContain("password").doesNotContain("hash");

        var login = post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("worksy_refresh=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        assertThat(login.getHeaders().getCacheControl()).contains("no-store");
        JsonNode body = json.readTree(login.getBody());
        String access = body.get("accessToken").asText();
        assertThat(body.get("user").get("role").asText()).isEqualTo("CANDIDATE");
        assertThat(login.getBody()).doesNotContain(cookieValue(setCookie));

        var me = get("/api/v1/auth/me", access);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).get("email").asText()).isEqualTo(email);

        String refresh1 = cookieValue(setCookie);
        var refreshed = post("/api/v1/auth/refresh", null, refresh1);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refresh2 = cookieValue(refreshed.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(refresh2).isNotEqualTo(refresh1);

        var logout = post("/api/v1/auth/logout", null, refresh2);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(post("/api/v1/auth/refresh", null, refresh2).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reutilizarUnRefrescoRotadoRevocaLaSesionYBorraLaCookie() throws Exception {
        String email = unique();
        post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        String refresh1 = cookieValue(post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        String refresh2 = cookieValue(post("/api/v1/auth/refresh", null, refresh1)
                .getHeaders().getFirst(HttpHeaders.SET_COOKIE));

        var reuse = post("/api/v1/auth/refresh", null, refresh1);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(reuse.getBody()).get("code").asText()).isEqualTo("auth.refresh.reused");
        assertThat(reuse.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        assertThat(post("/api/v1/auth/refresh", null, refresh2).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void erroresComoProblemDetailSinRevelarSiElCorreoExiste() throws Exception {
        String email = unique();
        post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);

        var duplicate = post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(duplicate.getBody()).get("code").asText()).isEqualTo("user.email.taken");

        var wrong = post("/api/v1/auth/login", Map.of("email", email, "password", "otra-frase-larga"), null);
        var unknown = post("/api/v1/auth/login", Map.of("email", unique(), "password", PASSWORD), null);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(json.readTree(wrong.getBody()).get("detail"))
                .isEqualTo(json.readTree(unknown.getBody()).get("detail"));
        assertThat(wrong.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        var shortPassword = post("/api/v1/auth/register", Map.of("email", unique(), "password", "corta"), null);
        assertThat(shortPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var missingFields = post("/api/v1/auth/register", Map.of(), null);
        assertThat(missingFields.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void lasRutasProtegidasExigenUnTokenValido() {
        assertThat(get("/api/v1/auth/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/auth/me", "token.inventado.x").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/refresh", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void elContratoOpenApiDocumentaLaAutenticacion() {
        var docs = http.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getBody()).contains("/api/v1/auth/login", "/api/v1/auth/refresh", "bearer");
    }

    // ---- utilidades ----

    private ResponseEntity<String> post(String path, Object body, String refreshCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (refreshCookie != null) {
            headers.add(HttpHeaders.COOKIE, "worksy_refresh=" + refreshCookie);
        }
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String cookieValue(String setCookie) {
        assertThat(setCookie).isNotNull();
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static String unique() {
        return "auth-" + UUID.randomUUID() + "@ejemplo.test";
    }
}
