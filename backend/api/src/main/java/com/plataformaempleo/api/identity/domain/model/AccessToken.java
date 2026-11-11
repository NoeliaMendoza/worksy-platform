package com.plataformaempleo.api.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Token de acceso firmado (JWT) y su fecha de caducidad. */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
