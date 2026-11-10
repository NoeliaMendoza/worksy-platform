package com.plataformaempleo.api.identity.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Vínculo entre un usuario de Worksy y su cuenta en un proveedor externo (Google, LinkedIn, GitHub).
 * Se identifica por (proveedor, subject): el correo puede cambiar en el proveedor, el subject no.
 */
public record ExternalIdentity(
        UUID id,
        UUID userId,
        String provider,
        String subject,
        String email,
        Instant createdAt) {

    public ExternalIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (provider == null || provider.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Proveedor y subject son obligatorios");
        }
        provider = provider.toLowerCase(Locale.ROOT);
    }

    public static ExternalIdentity link(UUID userId, String provider, String subject, String email, Instant now) {
        return new ExternalIdentity(UUID.randomUUID(), userId, provider, subject, email, now);
    }
}
