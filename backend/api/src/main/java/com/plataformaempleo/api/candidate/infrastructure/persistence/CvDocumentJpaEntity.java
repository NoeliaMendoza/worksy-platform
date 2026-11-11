package com.plataformaempleo.api.candidate.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_documents")
class CvDocumentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 200)
    private String storageKey;

    @Column(nullable = false, updatable = false, length = 64, columnDefinition = "bpchar(64)")
    private String sha256;

    @Column(nullable = false, length = 64)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 8)
    private String language;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected CvDocumentJpaEntity() {
    }

    CvDocumentJpaEntity(UUID id, UUID candidateId, String storageKey, String sha256, String mime, long sizeBytes,
                        String status, String language, Instant uploadedAt) {
        this.id = id;
        this.candidateId = candidateId;
        this.storageKey = storageKey;
        this.sha256 = sha256;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.language = language;
        this.uploadedAt = uploadedAt;
    }

    UUID getId() { return id; }
    UUID getCandidateId() { return candidateId; }
    String getStorageKey() { return storageKey; }
    String getSha256() { return sha256; }
    String getMime() { return mime; }
    long getSizeBytes() { return sizeBytes; }
    String getStatus() { return status; }
    String getLanguage() { return language; }
    Instant getUploadedAt() { return uploadedAt; }
}
