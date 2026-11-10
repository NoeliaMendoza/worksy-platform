package com.plataformaempleo.api.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private final Instant now = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void guardaSoloElHashDelSecreto() {
        var issued = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), now, Duration.ofDays(7));

        assertThat(issued.secret()).hasSizeGreaterThanOrEqualTo(43); // 32 bytes en base64url
        assertThat(issued.token().tokenHash()).isEqualTo(RefreshToken.hash(issued.secret()))
                .hasSize(64)
                .doesNotContain(issued.secret());
    }

    @Test
    void cadaEmisionGeneraUnSecretoDistinto() {
        var a = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), now, Duration.ofDays(7));
        var b = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), now, Duration.ofDays(7));
        assertThat(a.secret()).isNotEqualTo(b.secret());
    }

    @Test
    void caducaYSeRota() {
        var token = RefreshToken.issue(UUID.randomUUID(), UUID.randomUUID(), now, Duration.ofDays(7)).token();

        assertThat(token.isActive(now.plus(Duration.ofDays(6)))).isTrue();
        assertThat(token.isActive(now.plus(Duration.ofDays(7)))).isFalse();

        var rotated = token.rotatedTo(UUID.randomUUID(), now.plusSeconds(60));
        assertThat(rotated.wasRotated()).isTrue();
        assertThat(rotated.isActive(now.plusSeconds(61))).isFalse();
    }

    @Test
    void politicaDeContrasenaPorLongitud() {
        PasswordPolicy.validate("diez-chars");
        assertThatThrownBy(() -> PasswordPolicy.validate("corta"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("entre 10 y 128");
        assertThatThrownBy(() -> PasswordPolicy.validate("x".repeat(129))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("          ")).isInstanceOf(DomainException.class);
    }
}
