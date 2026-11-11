package com.plataformaempleo.api.candidate.infrastructure.persistence;

import com.plataformaempleo.api.candidate.domain.model.CvDocument;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link CvDocumentRepository}. */
@Component
class CvDocumentPersistenceAdapter implements CvDocumentRepository {

    private final SpringDataCvDocumentRepository jpa;

    CvDocumentPersistenceAdapter(SpringDataCvDocumentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public CvDocument save(CvDocument d) {
        return toDomain(jpa.saveAndFlush(new CvDocumentJpaEntity(d.id(), d.candidateId(), d.storageKey(), d.sha256(),
                d.mime(), d.sizeBytes(), d.status().name(), d.language(), d.uploadedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CvDocument> findById(UUID id) {
        return jpa.findById(id).map(CvDocumentPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CvDocument> findByCandidate(UUID candidateId) {
        return jpa.findByCandidateIdOrderByUploadedAtDesc(candidateId).stream()
                .map(CvDocumentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCandidateAndSha256(UUID candidateId, String sha256) {
        return jpa.existsByCandidateIdAndSha256(candidateId, sha256);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByCandidate(UUID candidateId) {
        return jpa.countByCandidateId(candidateId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    private static CvDocument toDomain(CvDocumentJpaEntity e) {
        return new CvDocument(e.getId(), e.getCandidateId(), e.getStorageKey(), e.getSha256(), e.getMime(),
                e.getSizeBytes(), CvDocument.Status.valueOf(e.getStatus()), e.getLanguage(), e.getUploadedAt());
    }
}
