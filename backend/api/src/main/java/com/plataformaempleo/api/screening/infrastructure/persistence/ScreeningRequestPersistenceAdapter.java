package com.plataformaempleo.api.screening.infrastructure.persistence;

import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.screening.domain.port.out.ScreeningRequestRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA de las solicitudes de preselección. */
@Component
class ScreeningRequestPersistenceAdapter implements ScreeningRequestRepository {

    private final SpringDataScreeningRequestRepository jpa;

    ScreeningRequestPersistenceAdapter(SpringDataScreeningRequestRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void saveAll(Collection<ScreeningRequest> requests) {
        jpa.saveAllAndFlush(requests.stream().map(ScreeningRequestPersistenceAdapter::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScreeningRequest> findByIds(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(ScreeningRequestPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScreeningRequest> findByVacancy(UUID vacancyId) {
        return jpa.findByVacancyIdOrderByRequestedAtAsc(vacancyId).stream()
                .map(ScreeningRequestPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean retryFailed(UUID requestId, Instant now) {
        return jpa.retryFailed(requestId, now) == 1;
    }

    private static ScreeningRequestJpaEntity toEntity(ScreeningRequest r) {
        return new ScreeningRequestJpaEntity(r.id(), r.vacancyId(), r.applicationId(), r.cvId(), r.provider(),
                r.modelVersion(), r.status().name(), r.attempts(), r.overallScore(), r.confidence(), r.requestedBy(),
                r.requestedAt(), r.updatedAt());
    }

    private static ScreeningRequest toDomain(ScreeningRequestJpaEntity e) {
        return new ScreeningRequest(e.getId(), e.getVacancyId(), e.getApplicationId(), e.getCvId(), e.getProvider(),
                e.getModelVersion(), ScreeningRequest.Status.valueOf(e.getStatus()), e.getAttempts(),
                e.getOverallScore(), e.getConfidence(), e.getRequestedBy(), e.getRequestedAt(), e.getUpdatedAt());
    }
}
