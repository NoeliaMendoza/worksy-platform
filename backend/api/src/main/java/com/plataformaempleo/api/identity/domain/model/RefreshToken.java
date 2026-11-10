package com.plataformaempleo.api.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de refresco con rotación. El secreto solo existe en la cookie del navegador:
 * aquí se guarda su SHA-256. Todos los tokens que nacen de un mismo inicio de sesión
 * comparten {@code familyId}; si se reutiliza uno ya rotado, se revoca la familia entera.
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        UUID familyId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID replacedBy) {

    public RefreshToken {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a issuedAt");
        }
    }

    /** Emite un token nuevo y devuelve también su secreto en claro (solo para enviarlo al cliente). */
    public static Issued issue(UUID userId, UUID familyId, Instant now, Duration ttl) {
        String secret = newSecret();
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, familyId, hash(secret), now,
                now.plus(ttl), null, null);
        return new Issued(token, secret);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean wasRotated() {
        return revokedAt != null && replacedBy != null;
    }

    public RefreshToken rotatedTo(UUID successor, Instant now) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, now, successor);
    }

    public static String hash(String secret) {
        return SecureTokens.hash(secret);
    }

    private static String newSecret() {
        return SecureTokens.newSecret();
    }

    /** Token recién emitido junto con el secreto que solo verá el cliente. */
    public record Issued(RefreshToken token, String secret) {
    }
}
