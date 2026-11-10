package com.plataformaempleo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ApiIntegrationTest {

    // Datos sintéticos: dominio reservado .test, nunca correos reales.
    private static final String SYNTHETIC_DOMAIN = "@ejemplo.test";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    private final Clock clock = Clock.systemUTC();

    @Test
    void flywayAplicoLaMigracionDeUsuarios() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void guardaYRecuperaUnUsuarioPorElPuerto() {
        User saved = users.save(User.register(unique("Candidata"), "hash-sintetico", Role.CANDIDATE, null, clock));

        assertThat(users.findById(saved.id())).hasValueSatisfying(found -> {
            assertThat(found.email()).isEqualTo(saved.email());
            assertThat(found.role()).isEqualTo(Role.CANDIDATE);
            assertThat(found.createdAt()).isCloseTo(saved.createdAt(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
        });
        assertThat(users.findActiveByEmail(saved.email().toUpperCase())).isPresent();
    }

    @Test
    void elCorreoEsUnicoEntreUsuariosActivos() {
        String email = unique("duplicado");
        users.save(User.register(email, "h1", Role.CANDIDATE, null, clock));

        assertThatThrownBy(() -> users.save(User.register(email, "h2", Role.CANDIDATE, null, clock)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unUsuarioBorradoNoBloqueaElCorreo() {
        String email = unique("borrado");
        User original = User.register(email, "h1", Role.CANDIDATE, null, clock);
        users.save(new User(original.id(), original.email(), original.passwordHash(), original.role(),
                null, original.createdAt(), Instant.now()));

        assertThat(users.findActiveByEmail(email)).isEmpty();
        assertThat(users.save(User.register(email, "h2", Role.CANDIDATE, null, clock)).id()).isNotEqualTo(original.id());
    }

    @Test
    void laBaseDeDatosRechazaReclutadorSinEmpresa() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (id, email, password_hash, role) values (?, ?, 'h', 'RECRUITER')",
                UUID.randomUUID(), unique("reclutador")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exponeSaludYContratoOpenApi() {
        var health = http.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");

        var docs = http.getForEntity("/v3/api-docs", String.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("Worksy API");
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID() + SYNTHETIC_DOMAIN;
    }
}
