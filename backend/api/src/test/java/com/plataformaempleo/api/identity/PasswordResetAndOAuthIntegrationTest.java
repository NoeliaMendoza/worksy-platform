package com.plataformaempleo.api.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetMailer;
import java.net.URI;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Recuperación de contraseña y arranque del login externo contra PostgreSQL real.
 * El correo se captura en memoria; Google nunca se contacta (solo se comprueba la redirección).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, PasswordResetAndOAuthIntegrationTest.CapturingMailer.class})
class PasswordResetAndOAuthIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";
    static final List<String[]> SENT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class CapturingMailer {
        @Bean
        @Primary
        PasswordResetMailer capturingMailer() {
            return (email, link, minutes) -> SENT.add(new String[] {email, link});
        }
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void clear() {
        SENT.clear();
    }

    @Test
    void flywayAplicoLaV3() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '3' and success", Integer.class)).isEqualTo(1);
    }

    @Test
    void recuperarContrasenaDePuntaAPunta() throws Exception {
        String email = unique();
        post("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        String oldRefresh = cookie(post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null));

        var forgot = post("/api/v1/auth/password/forgot", Map.of("email", email), null);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(SENT).singleElement().satisfies(m -> {
            assertThat(m[0]).isEqualTo(email);
            assertThat(m[1]).startsWith("http://localhost:5173/restablecer?token=");
        });
        String token = URLDecoder.decode(SENT.get(0)[1].split("token=")[1], StandardCharsets.UTF_8);

        var reset = post("/api/v1/auth/password/reset", Map.of("token", token, "password", "nueva-frase-larga"), null);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // La contraseña vieja ya no sirve, la nueva sí, y las sesiones anteriores se cerraron.
        assertThat(post("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/login", Map.of("email", email, "password", "nueva-frase-larga"), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/refresh", null, oldRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // El enlace no sirve dos veces.
        var again = post("/api/v1/auth/password/reset", Map.of("token", token, "password", "tercera-frase-larga"), null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("password.reset.invalid");
    }

    @Test
    void pedirElEnlaceNoRevelaSiLaCuentaExiste() {
        var unknown = post("/api/v1/auth/password/forgot", Map.of("email", unique()), null);
        var malformed = post("/api/v1/auth/password/forgot", Map.of("email", "no-es-correo"), null);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(SENT).isEmpty();
        // Antes de validar el correo fuera de la transacción, esto daba 500.
        assertThat(post("/api/v1/auth/login", Map.of("email", "no-es-correo", "password", PASSWORD), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listaLosProveedoresHabilitadosYRedirigeAGoogleConLaUrlDeRegreso() throws Exception {
        var providers = http.getForEntity("/api/v1/auth/providers", String.class);
        assertThat(providers.getStatusCode()).isEqualTo(HttpStatus.OK);
        var list = json.readTree(providers.getBody());
        assertThat(list).hasSize(1); // solo Google tiene credenciales (sintéticas) en las pruebas
        assertThat(list.get(0).get("id").asText()).isEqualTo("google");
        assertThat(list.get(0).get("authorizationUrl").asText()).isEqualTo("/api/v1/auth/oauth2/authorization/google");

        // Cliente que NO sigue redirecciones: la prueba nunca sale a internet.
        var client = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        var start = client.send(java.net.http.HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/auth/oauth2/authorization/google")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        assertThat(start.statusCode()).isEqualTo(302);
        URI location = URI.create(start.headers().firstValue("Location").orElseThrow());
        assertThat(location.getHost()).isEqualTo("accounts.google.com");
        String query = URLDecoder.decode(location.getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("client_id=cliente-sintetico.apps.ejemplo.test")
                .contains("/api/v1/auth/oauth2/callback/google")
                .contains("scope=openid profile email")
                .contains("state=");
    }

    @Test
    void conLoginExternoActivoLasRutasProtegidasSiguenRespondiendo401() {
        var me = http.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers(MediaType.TEXT_HTML)), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/login", String.class).getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    // ---- utilidades ----

    private ResponseEntity<String> post(String path, Object body, String refreshCookie) {
        HttpHeaders headers = headers(MediaType.APPLICATION_JSON);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (refreshCookie != null) {
            headers.add(HttpHeaders.COOKIE, "worksy_refresh=" + refreshCookie);
        }
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static HttpHeaders headers(MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(accept, MediaType.APPLICATION_PROBLEM_JSON));
        return headers;
    }

    private static String cookie(ResponseEntity<String> response) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static String unique() {
        return "reset-" + UUID.randomUUID() + "@ejemplo.test";
    }
}
