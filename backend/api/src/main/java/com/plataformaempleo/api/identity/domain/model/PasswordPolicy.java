package com.plataformaempleo.api.identity.domain.model;

import com.plataformaempleo.common.domain.DomainException;

/**
 * Reglas de la contraseña en claro, antes de calcular su hash.
 * Longitud mínima y máxima al estilo NIST SP 800-63B: sin reglas de composición forzadas.
 * VALOR SUPUESTO (10 a 128): confirmar con el diseño técnico.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

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
    }
}
