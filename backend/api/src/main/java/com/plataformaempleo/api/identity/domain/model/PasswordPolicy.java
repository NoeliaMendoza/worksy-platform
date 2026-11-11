package com.plataformaempleo.api.identity.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.nio.charset.StandardCharsets;

/**
 * Reglas de la contraseña en claro, antes de calcular su hash.
 * Longitud al estilo NIST SP 800-63B (sin reglas de composición forzadas).
 * <p>
 * El máximo lo impone BCrypt: solo admite 72 bytes. Por eso se limita a 64 caracteres
 * y, además, a 72 bytes en UTF-8 (las letras con tilde ocupan 2 bytes).
 * VALOR SUPUESTO (10 a 64): confirmar con el diseño técnico.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new DomainException("user.password.invalid", "La contraseña es obligatoria");
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new DomainException("user.password.invalid",
                    "La contraseña debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainException("user.password.invalid",
                    "La contraseña es demasiado larga: usa menos letras con tilde o símbolos especiales");
        }
    }
}
