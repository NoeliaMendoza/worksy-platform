package com.plataformaempleo.api.candidate.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidate_profiles")
class CandidateProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 200)
    private String headline;

    @Column(length = 120)
    private String location;

    @Column(name = "consent_share", nullable = false)
    private boolean consentShare;

    @Column(name = "consent_github", nullable = false)
    private boolean consentGithub;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CandidateProfileJpaEntity() {
    }

    CandidateProfileJpaEntity(UUID userId) {
        this.userId = userId;
    }

    void setConsents(boolean share, boolean github, Instant at) {
        this.consentShare = share;
        this.consentGithub = github;
        this.updatedAt = at;
    }

    boolean isConsentShare() { return consentShare; }
    boolean isConsentGithub() { return consentGithub; }
    Instant getUpdatedAt() { return updatedAt; }
}
