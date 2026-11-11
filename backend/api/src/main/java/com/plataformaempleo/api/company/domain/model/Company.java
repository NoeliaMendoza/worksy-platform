package com.plataformaempleo.api.company.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Empresa que publica vacantes. Es el inquilino: sus datos nunca se mezclan con los de otra. */
public record Company(UUID id, String name, Instant createdAt) {

    /** VALOR SUPUESTO (2 a 160 caracteres): confirmar con el diseño técnico. */
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 160;

    public Company {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        name = normalizeName(name);
    }

    public static Company register(String name, Clock clock) {
        return new Company(UUID.randomUUID(), name, clock.instant());
    }

    static String normalizeName(String raw) {
        String value = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        int length = value.codePointCount(0, value.length());
        if (length < NAME_MIN || length > NAME_MAX) {
            throw new DomainException("company.name.invalid",
                    "El nombre de la empresa debe tener entre " + NAME_MIN + " y " + NAME_MAX + " caracteres");
        }
        return value;
    }
}
