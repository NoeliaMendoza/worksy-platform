package com.plataformaempleo.api.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformaempleo.api.identity.domain.model.PasswordPolicy;
import com.plataformaempleo.api.identity.domain.model.SecureTokens;
import org.junit.jupiter.api.Test;

/**
 * Con el BCrypt real (no un doble): BCrypt rechaza más de 72 bytes. Esta prueba habría detectado
 * que la contraseña de relleno de las cuentas externas e invitadas (73 caracteres) daba error 500.
 */
class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void elSecretoDeRellenoCabeEnBcrypt() {
        String filler = SecureTokens.newSecret();
        assertThat(filler.length()).isLessThanOrEqualTo(PasswordPolicy.MAX_BYTES);
        assertThat(hasher.matches(filler, hasher.hash(filler))).isTrue();
    }

    @Test
    void laContrasenaMasLargaQueAdmiteLaPoliticaSeHasheaSinError() {
        String longest = "ñ".repeat(36); // 72 bytes
        PasswordPolicy.validate(longest);
        assertThat(hasher.matches(longest, hasher.hash(longest))).isTrue();
        assertThat(hasher.matches("otra", hasher.hash(longest))).isFalse();
    }
}
