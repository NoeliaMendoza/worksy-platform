package com.plataformaempleo.api.screening.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataScreeningRequestRepository extends JpaRepository<ScreeningRequestJpaEntity, UUID> {

    List<ScreeningRequestJpaEntity> findByVacancyIdOrderByRequestedAtAsc(UUID vacancyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ScreeningRequestJpaEntity r set r.status = 'PENDING', r.modelVersion = null, "
            + "r.overallScore = null, r.confidence = null, r.updatedAt = :now "
            + "where r.id = :id and r.status = 'FAILED'")
    int retryFailed(@Param("id") UUID requestId, @Param("now") Instant now);
}
