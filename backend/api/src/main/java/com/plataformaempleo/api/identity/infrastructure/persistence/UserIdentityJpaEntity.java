package com.plataformaempleo.api.identity.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
class UserIdentityJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(length = 254)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserIdentityJpaEntity() {
    }

    UserIdentityJpaEntity(UUID id, UUID userId, String provider, String subject, String email, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getUserId() { return userId; }
    String getProvider() { return provider; }
    String getSubject() { return subject; }
    String getEmail() { return email; }
    Instant getCreatedAt() { return createdAt; }
}
