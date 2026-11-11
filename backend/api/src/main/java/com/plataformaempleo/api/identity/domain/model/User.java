package com.plataformaempleo.api.identity.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Usuario de la plataforma. Modelo de dominio puro: sin anotaciones de JPA ni de Spring.
 * La contraseña nunca llega aquí en claro, solo su hash.
 */
public record User(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        UUID companyId,
        Instant createdAt,
        Instant deletedAt) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_EMAIL = 254;

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        email = normalizeEmail(email);
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainException("user.password.missing", "Falta el hash de la contraseña");
        }
        if (role.requiresCompany() && companyId == null) {
            throw new DomainException("user.company.required", "El rol " + role + " requiere una empresa");
        }
        if (!role.requiresCompany() && companyId != null) {
            throw new DomainException("user.company.forbidden", "El rol " + role + " no pertenece a una empresa");
        }
    }

    public static User register(String email, String passwordHash, Role role, UUID companyId, Clock clock) {
        return new User(UUID.randomUUID(), email, passwordHash, role, companyId, clock.instant(), null);
    }

    /** Mismo usuario con otra contraseña (ya hasheada). */
    public User withPasswordHash(String newHash) {
        return new User(id, email, newHash, role, companyId, createdAt, deletedAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public static String normalizeEmail(String raw) {
        if (raw == null) {
            throw new DomainException("user.email.invalid", "El correo es obligatorio");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_EMAIL || !EMAIL.matcher(value).matches()) {
            throw new DomainException("user.email.invalid", "El correo no es válido");
        }
        return value;
    }
}
