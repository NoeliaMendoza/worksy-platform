package com.plataformaempleo.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataformaempleo.api.TestcontainersConfig;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * CV de punta a punta contra PostgreSQL y MinIO reales. Usa la misma imagen de MinIO que
 * Docker Compose, compilada desde el código fuente ({@code infra/minio/Dockerfile}). Datos sintéticos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@Testcontainers
class CandidateCvIntegrationTest {

    /** Debe coincidir con la etiqueta de {@code infra/docker-compose.yml}. */
    static final String MINIO_IMAGE = "plataforma-empleo/minio:RELEASE.2025-10-15T17-29-55Z";
    private static final String USER = "minio-sintetico";
    private static final String SECRET = "secreto-sintetico-minio";
    private static final String BUCKET = "cv-pruebas";
    private static final String PASSWORD = "frase-sintetica-larga";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(MINIO_IMAGE)
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", USER)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("worksy.storage.endpoint", CandidateCvIntegrationTest::endpoint);
        registry.add("worksy.storage.access-key", () -> USER);
        registry.add("worksy.storage.secret-key", () -> SECRET);
        registry.add("worksy.storage.bucket", () -> BUCKET);
    }

    @BeforeAll
    static void createBucket() {
        try (S3Client s3 = s3()) {
            s3.createBucket(b -> b.bucket(BUCKET));
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    void subirListarYEliminarGuardaElPdfEnMinioYNoEnLaBaseDeDatos() throws Exception {
        String token = candidate();

        var uploaded = upload(token, SyntheticPdf.of("integracion-" + UUID.randomUUID()));
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(uploaded.getBody());
        String id = body.get("id").asText();
        assertThat(body.get("status").asText()).isEqualTo("UPLOADED");
        // El contrato no expone la llave de almacenamiento ni el hash.
        assertThat(body.has("storageKey")).isFalse();
        assertThat(body.has("sha256")).isFalse();
        assertThat(objectKeys()).contains("cv/" + id + ".pdf");

        JsonNode list = json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/cv", null, token).getBody());
        assertThat(list.findValuesAsText("id")).containsExactly(id);

        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/cv/" + id, null, token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(objectKeys()).doesNotContain("cv/" + id + ".pdf");
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/cv", null, token).getBody())).isEmpty();
    }

    @Test
    void validaElArchivoAntesDeGuardarlo() throws Exception {
        String token = candidate();
        int before = objectKeys().size();

        var notPdf = upload(token, "PK\u0003\u0004 un docx renombrado".getBytes());
        assertThat(notPdf.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(code(notPdf)).isEqualTo("cv.file.not-pdf");

        var tooLarge = upload(token, SyntheticPdf.ofSize(5 * 1024 * 1024 + 1));
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(code(tooLarge)).isEqualTo("cv.file.too-large");

        byte[] same = SyntheticPdf.of("duplicado-" + UUID.randomUUID());
        assertThat(upload(token, same).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var duplicate = upload(token, same);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(duplicate)).isEqualTo("cv.duplicate");

        assertThat(objectKeys()).hasSize(before + 1);
    }

    @Test
    void nadieMasPuedeVerNiBorrarElCvDeUnCandidato() throws Exception {
        String owner = candidate();
        String intruder = candidate();
        String id = json.readTree(upload(owner, SyntheticPdf.of("privado-" + UUID.randomUUID())).getBody())
                .get("id").asText();

        assertThat(send(HttpMethod.DELETE, "/api/v1/candidates/me/cv/" + id, null, intruder).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/cv", null, intruder).getBody())).isEmpty();
        assertThat(objectKeys()).contains("cv/" + id + ".pdf");

        String admin = companyAdmin();
        assertThat(upload(admin, SyntheticPdf.of("empresa")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload(null, SyntheticPdf.of("anonimo")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void consentimientosEmpiezanDesactivadosYSeGuardan() throws Exception {
        String token = candidate();

        JsonNode initial = json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/consents", null, token).getBody());
        assertThat(initial.get("shareWithRecruiters").asBoolean()).isFalse();
        assertThat(initial.get("githubLink").asBoolean()).isFalse();
        assertThat(initial.get("updatedAt").isNull()).isTrue();

        var updated = send(HttpMethod.PUT, "/api/v1/candidates/me/consents",
                Map.of("shareWithRecruiters", true, "githubLink", false), token);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode stored = json.readTree(send(HttpMethod.GET, "/api/v1/candidates/me/consents", null, token).getBody());
        assertThat(stored.get("shareWithRecruiters").asBoolean()).isTrue();
        assertThat(stored.get("githubLink").asBoolean()).isFalse();
        assertThat(stored.get("updatedAt").isNull()).isFalse();

        assertThat(send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("githubLink", true), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void laEmpresaAbreElCvConUnEnlaceTemporalSoloMientrasHayPermiso() throws Exception {
        String admin = companyAdmin();
        String vacancyId = publishedVacancy(admin);
        String candidate = candidate();
        byte[] pdf = SyntheticPdf.of("enlace-" + UUID.randomUUID());
        String cvId = json.readTree(upload(candidate, pdf).getBody()).get("id").asText();
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", true, "githubLink", false), candidate);
        String applicationId = json.readTree(send(HttpMethod.POST, "/api/v1/vacancies/" + vacancyId + "/applications",
                Map.of("cvId", cvId), candidate).getBody()).get("id").asText();
        String path = "/api/v1/companies/me/vacancies/" + vacancyId + "/applications/" + applicationId + "/cv";

        var link = send(HttpMethod.GET, path, null, admin);
        assertThat(link.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(link.getBody());
        assertThat(java.time.Instant.parse(body.get("expiresAt").asText())).isAfter(java.time.Instant.now());

        // El navegador descarga el PDF directamente del almacenamiento, sin sesión y sin pasar por la API.
        var direct = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(URI.create(body.get("url").asText())).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertThat(direct.statusCode()).isEqualTo(200);
        assertThat(direct.body()).isEqualTo(pdf);
        assertThat(direct.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(direct.headers().firstValue("Content-Disposition").orElse("")).contains("cv.pdf");

        // Otra empresa no puede pedirlo y, sin permiso de la persona, tampoco la propia.
        assertThat(send(HttpMethod.GET, path, null, companyAdmin()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.GET, path, null, candidate).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        send(HttpMethod.PUT, "/api/v1/candidates/me/consents", Map.of("shareWithRecruiters", false, "githubLink", false), candidate);
        var revoked = send(HttpMethod.GET, path, null, admin);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(code(revoked)).isEqualTo("access.cv-not-shared");
    }

    // ---- utilidades ----

    private String publishedVacancy(String admin) throws Exception {
        Map<String, Object> vacancy = Map.of("title", "Backend Java", "description", "Equipo de pagos con Spring Boot y PostgreSQL.",
                "location", "Lima", "modality", "REMOTO", "criteria", List.of(Map.of("label", "Spring Boot",
                        "question", "¿Tiene experiencia con Spring Boot?", "kind", "YES_NO")));
        String id = json.readTree(send(HttpMethod.POST, "/api/v1/companies/me/vacancies", vacancy, admin).getBody())
                .get("id").asText();
        send(HttpMethod.POST, "/api/v1/companies/me/vacancies/" + id + "/publish", null, admin);
        return id;
    }

    private static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static S3Client s3() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USER, SECRET)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static List<String> objectKeys() {
        try (S3Client s3 = s3()) {
            return s3.listObjectsV2Paginator(b -> b.bucket(BUCKET)).contents().stream().map(S3Object::key).toList();
        }
    }

    private String candidate() throws Exception {
        String email = "cand-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/auth/register", Map.of("email", email, "password", PASSWORD), null);
        return login(email);
    }

    private String companyAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@ejemplo.test";
        send(HttpMethod.POST, "/api/v1/companies",
                Map.of("companyName", "Empresa sintética", "email", email, "password", PASSWORD), null);
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

    private ResponseEntity<String> upload(String bearer, byte[] content) {
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "cv-sintetico.pdf";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (bearer != null) {
            headers.setBearerAuth(bearer);
        }
        return http.exchange("/api/v1/candidates/me/cv", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
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
