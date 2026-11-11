package com.plataformaempleo.api.identity.domain.model;

import com.plataformaempleo.common.domain.DomainException;

/**
 * Credenciales o token de refresco no válidos. El mensaje es deliberadamente genérico:
 * no revela si el correo existe ni por qué falló el token.
 */
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException(String code) {
        super(code, "No se pudo verificar tu identidad");
    }

    public static AuthenticationFailedException badCredentials() {
        return new AuthenticationFailedException("auth.credentials.invalid");
    }

    public static AuthenticationFailedException invalidRefresh() {
        return new AuthenticationFailedException("auth.refresh.invalid");
    }
}
