package com.plataformaempleo.api.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Enlace de recuperación de contraseña: de un solo uso y con caducidad corta.
 * Solo se guarda el SHA-256 del secreto que viaja en el enlace del correo.
 */
public record PasswordResetToken(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant usedAt) {

    public PasswordResetToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a createdAt");
        }
    }

    public static Issued issue(UUID userId, Instant now, Duration ttl) {
        String secret = SecureTokens.newSecret();
        var token = new PasswordResetToken(UUID.randomUUID(), userId, SecureTokens.hash(secret), now, now.plus(ttl), null);
        return new Issued(token, secret);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public PasswordResetToken markUsed(Instant now) {
        return new PasswordResetToken(id, userId, tokenHash, createdAt, expiresAt, now);
    }

    /** Token recién emitido y el secreto que irá en el enlace. */
    public record Issued(PasswordResetToken token, String secret) {
    }
}
