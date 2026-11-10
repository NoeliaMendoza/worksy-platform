package com.plataformaempleo.api.identity.infrastructure.persistence;

import com.plataformaempleo.api.identity.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserJpaEntity() {
    }

    UserJpaEntity(UUID id, String email, String passwordHash, Role role, UUID companyId,
                  Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.companyId = companyId;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    UUID getId() { return id; }
    String getEmail() { return email; }
    String getPasswordHash() { return passwordHash; }
    Role getRole() { return role; }
    UUID getCompanyId() { return companyId; }
    Instant getCreatedAt() { return createdAt; }
    Instant getDeletedAt() { return deletedAt; }
}
