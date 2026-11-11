package com.plataformaempleo.api.vacancy;

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

/** Vacantes de punta a punta contra PostgreSQL real (criterios en JSONB). Datos sintéticos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class VacancyIntegrationTest {

    private static final String PASSWORD = "frase-sintetica-larga";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    private static Map<String, Object> vacancy(String title, List<Map<String, Object>> criteria) {
        return Map.of("title", title, "description", "Equipo de pagos con Spring Boot y PostgreSQL.",
                "location", "Lima", "modality", "HIBRIDO", "criteria", criteria);
    }

    private static final List<Map<String, Object>> CRITERIA = List.of(
            Map.of("label", "Spring Boot", "question", "¿Tiene experiencia profesional con Spring Boot?", "kind", "YES_NO"),
            Map.of("label", "Años en Java", "question", "¿Cuántos años de experiencia tiene con Java?", "kind", "CHOICE",
                    "options", List.of("Más de 3", "1 a 3", "Menos de 1")));

    @Test
    void crearEditarPublicarYCerrarConVisibilidadPublica() throws Exception {
        String token = companyAdmin("Andina Cargo");
        String title = "Backend Java " + UUID.randomUUID();

        var created = send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy(title, List.of()), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json.readTree(created.getBody()).get("id").asText();
        assertThat(json.readTree(created.getBody()).get("status").asText()).isEqualTo("DRAFT");

        // Un borrador no es público y no se publica sin criterios.
        assertThat(send(HttpMethod.GET, "/api/v1/vacancies/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var noCriteria = send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, token);
        assertThat(json.readTree(noCriteria.getBody()).get("code").asText()).isEqualTo("vacancy.criteria.required");

        var edited = send(HttpMethod.PUT, "/api/v1/companies/me/vacancies/" + id, vacancy(title, CRITERIA), token);
        JsonNode criteria = json.readTree(edited.getBody()).get("criteria");
        assertThat(criteria).hasSize(2);
        assertThat(criteria.get(0).get("key").asText()).isEqualTo("q1");
        assertThat(criteria.get(0).get("options").toString()).isEqualTo("[\"Sí\",\"No\"]");

        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, token).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Pública y sin sesión: aparece en la lista y su detalle trae las preguntas y el nombre de la empresa.
        JsonNode list = json.readTree(send(HttpMethod.GET, "/api/v1/vacancies", null, null).getBody());
        assertThat(list.findValuesAsText("title")).contains(title);
        JsonNode detail = json.readTree(send(HttpMethod.GET, "/api/v1/vacancies/" + id, null, null).getBody());
        assertThat(detail.get("companyName").asText()).isEqualTo("Andina Cargo");
        assertThat(detail.get("criteria").get(1).get("options")).hasSize(3);
        assertThat(detail.has("createdBy")).isFalse();

        // Publicada ya no se edita; al cerrarla deja de ser pública.
        assertThat(send(HttpMethod.PUT, "/api/v1/companies/me/vacancies/" + id, vacancy(title, CRITERIA), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/close", null, token);
        assertThat(send(HttpMethod.GET, "/api/v1/vacancies/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void otraEmpresaYUnCandidatoNoAccedenALasVacantesDeLaEmpresa() throws Exception {
        String tokenA = companyAdmin("Empresa A");
        String tokenB = companyAdmin("Empresa B");
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies",
                vacancy("Vacante de A", CRITERIA), tokenA).getBody()).get("id").asText();

        var fromB = send(HttpMethod.GET, "/api/v1/companies/me/vacancies/" + id, null, tokenB);
        assertThat(fromB.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, tokenB)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/companies/me/vacancies", null, tokenB).getBody()))
                .isEmpty();

        String candidate = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", candidate, "password", PASSWORD), null);
        String candidateToken = login(candidate);
        assertThat(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy("X Y Z", CRITERIA), candidateToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send(HttpMethod.GET, "/api/v1/companies/me/vacancies", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void datosNoValidosSonUn400ConCodigo() throws Exception {
        String token = companyAdmin("Andina");
        var bad = send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy("Título válido", List.of(
                Map.of("label", "Inglés", "question", "¿Qué nivel de inglés tiene?", "kind", "CHOICE", "options", List.of("B2")))),
                token);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(bad.getBody()).get("code").asText()).isEqualTo("vacancy.criterion.invalid");
    }

    // ---- utilidades ----

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
