package com.plataformaempleo.api.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "vacancies")
class VacancyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 120)
    private String location;

    @Column(nullable = false, length = 16)
    private String modality;

    @Column(nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<CriterionJson> criteria;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected VacancyJpaEntity() {
    }

    VacancyJpaEntity(UUID id, UUID companyId, UUID createdBy, String title, String description, String location,
                     String modality, String status, List<CriterionJson> criteria, Instant createdAt,
                     Instant updatedAt, Instant publishedAt, Instant closedAt) {
        this.id = id;
        this.companyId = companyId;
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.location = location;
        this.modality = modality;
        this.status = status;
        this.criteria = criteria;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.publishedAt = publishedAt;
        this.closedAt = closedAt;
    }

    /** Criterio tal como se guarda en la columna JSONB. */
    record CriterionJson(String key, String label, String question, String kind, List<String> options) {
    }

    UUID getId() { return id; }
    UUID getCompanyId() { return companyId; }
    UUID getCreatedBy() { return createdBy; }
    String getTitle() { return title; }
    String getDescription() { return description; }
    String getLocation() { return location; }
    String getModality() { return modality; }
    String getStatus() { return status; }
    List<CriterionJson> getCriteria() { return criteria; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    Instant getPublishedAt() { return publishedAt; }
    Instant getClosedAt() { return closedAt; }
}
