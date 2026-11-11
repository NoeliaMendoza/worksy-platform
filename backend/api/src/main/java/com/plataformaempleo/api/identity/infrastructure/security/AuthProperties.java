package com.plataformaempleo.api.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de autenticación ({@code worksy.auth.*}).
 * VALORES SUPUESTOS por defecto (15 min, 7 días): confirmar con el diseño técnico.
 *
 * @param jwtSecret          clave HMAC del JWT; mínimo 32 bytes. Llega de {@code JWT_SECRET}.
 * @param issuer             emisor ({@code iss}) que se firma y se exige al validar.
 * @param accessTokenTtl     vida del token de acceso.
 * @param refreshTokenTtl    vida del token de refresco.
 * @param refreshCookieSecure si la cookie del refresco lleva {@code Secure}.
 * @param passwordResetTtl   vida del enlace para recuperar la contraseña (VALOR SUPUESTO: 30 min).
 * @param publicUrl          URL pública del frontend (enlaces de correo y regreso del login externo).
 */
@ConfigurationProperties("worksy.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        Duration passwordResetTtl,
        String publicUrl) {

    static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "worksy.auth.jwt-secret (JWT_SECRET) es obligatorio y debe tener al menos 32 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "worksy-api";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(7);
        }
        if (passwordResetTtl == null) {
            passwordResetTtl = Duration.ofMinutes(30);
        }
        if (publicUrl == null || publicUrl.isBlank()) {
            publicUrl = "http://localhost:5173";
        }
        publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Override
    public String toString() {
        return "AuthProperties[issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + ", jwtSecret=***]";
    }
}
