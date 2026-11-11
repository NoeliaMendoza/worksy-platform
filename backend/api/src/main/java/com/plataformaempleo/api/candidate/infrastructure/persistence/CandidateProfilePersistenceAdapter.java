package com.plataformaempleo.api.candidate.infrastructure.persistence;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link CandidateProfileRepository}. */
@Component
class CandidateProfilePersistenceAdapter implements CandidateProfileRepository {

    private final SpringDataCandidateProfileRepository jpa;

    CandidateProfilePersistenceAdapter(SpringDataCandidateProfileRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CandidateConsents> findConsents(UUID userId) {
        return jpa.findById(userId).map(CandidateProfilePersistenceAdapter::toConsents);
    }

    @Override
    @Transactional
    public CandidateConsents saveConsents(UUID userId, CandidateConsents consents) {
        CandidateProfileJpaEntity entity = jpa.findById(userId).orElseGet(() -> new CandidateProfileJpaEntity(userId));
        entity.setConsents(consents.shareWithRecruiters(), consents.githubLink(), consents.updatedAt());
        return toConsents(jpa.saveAndFlush(entity));
    }

    private static CandidateConsents toConsents(CandidateProfileJpaEntity e) {
        return new CandidateConsents(e.isConsentShare(), e.isConsentGithub(), e.getUpdatedAt());
    }
}
