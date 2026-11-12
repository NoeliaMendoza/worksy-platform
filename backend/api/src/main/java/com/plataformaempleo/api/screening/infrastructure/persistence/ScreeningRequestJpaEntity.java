package com.plataformaempleo.api.screening.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "screening_requests")
class ScreeningRequestJpaEntity {

    @Id
    private UUID id;

    @Column(name = "vacancy_id", nullable = false, updatable = false)
    private UUID vacancyId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "cv_id", nullable = false, updatable = false)
    private UUID cvId;

    @Column(nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "overall_score", precision = 5, scale = 4)
    private BigDecimal overallScore;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScreeningRequestJpaEntity() {
    }

    ScreeningRequestJpaEntity(UUID id, UUID vacancyId, UUID applicationId, UUID cvId, String provider,
                              String modelVersion, String status, int attempts, BigDecimal overallScore,
                              BigDecimal confidence, UUID requestedBy, Instant requestedAt, Instant updatedAt) {
        this.id = id;
        this.vacancyId = vacancyId;
        this.applicationId = applicationId;
        this.cvId = cvId;
        this.provider = provider;
        this.modelVersion = modelVersion;
        this.status = status;
        this.attempts = attempts;
        this.overallScore = overallScore;
        this.confidence = confidence;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() { return id; }
    UUID getVacancyId() { return vacancyId; }
    UUID getApplicationId() { return applicationId; }
    UUID getCvId() { return cvId; }
    String getProvider() { return provider; }
    String getModelVersion() { return modelVersion; }
    String getStatus() { return status; }
    int getAttempts() { return attempts; }
    BigDecimal getOverallScore() { return overallScore; }
    BigDecimal getConfidence() { return confidence; }
    UUID getRequestedBy() { return requestedBy; }
    Instant getRequestedAt() { return requestedAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
