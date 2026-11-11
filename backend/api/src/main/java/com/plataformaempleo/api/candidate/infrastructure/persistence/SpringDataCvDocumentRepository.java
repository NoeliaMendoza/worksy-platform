package com.plataformaempleo.api.candidate.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCvDocumentRepository extends JpaRepository<CvDocumentJpaEntity, UUID> {

    List<CvDocumentJpaEntity> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    boolean existsByCandidateIdAndSha256(UUID candidateId, String sha256);

    long countByCandidateId(UUID candidateId);
}
