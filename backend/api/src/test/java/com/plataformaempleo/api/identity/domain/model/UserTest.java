package com.plataformaempleo.api.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void normalizaElCorreo() {
        User user = User.register("  Ana.Prueba@Ejemplo.TEST ", "hash", Role.CANDIDATE, null, clock);

        assertThat(user.email()).isEqualTo("ana.prueba@ejemplo.test");
        assertThat(user.createdAt()).isEqualTo(clock.instant());
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void rechazaCorreoInvalido() {
        assertThatThrownBy(() -> User.register("no-es-correo", "hash", Role.CANDIDATE, null, clock))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("correo");
    }

    @Test
    void reclutadorRequiereEmpresa() {
        assertThatThrownBy(() -> User.register("r@ejemplo.test", "hash", Role.RECRUITER, null, clock))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("user.company.required");
    }

    @Test
    void candidatoNoPerteneceAEmpresa() {
        assertThatThrownBy(() -> User.register("c@ejemplo.test", "hash", Role.CANDIDATE, UUID.randomUUID(), clock))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo("user.company.forbidden");
    }

    @Test
    void exigeHashDeContrasena() {
        assertThatThrownBy(() -> User.register("c@ejemplo.test", " ", Role.CANDIDATE, null, clock))
                .isInstanceOf(DomainException.class);
    }
}
