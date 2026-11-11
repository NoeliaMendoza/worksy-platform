package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
class JobApplicationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "vacancy_id", nullable = false, updatable = false)
    private UUID vacancyId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "cv_id", nullable = false)
    private UUID cvId;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    protected JobApplicationJpaEntity() {
    }

    JobApplicationJpaEntity(UUID id, UUID vacancyId, UUID candidateId, UUID cvId, Instant appliedAt) {
        this.id = id;
        this.vacancyId = vacancyId;
        this.candidateId = candidateId;
        this.cvId = cvId;
        this.appliedAt = appliedAt;
    }

    UUID getId() { return id; }
    UUID getVacancyId() { return vacancyId; }
    UUID getCandidateId() { return candidateId; }
    UUID getCvId() { return cvId; }
    Instant getAppliedAt() { return appliedAt; }
}
