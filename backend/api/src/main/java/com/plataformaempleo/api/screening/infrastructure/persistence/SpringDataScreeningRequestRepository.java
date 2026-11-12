package com.plataformaempleo.api.screening.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataScreeningRequestRepository extends JpaRepository<ScreeningRequestJpaEntity, UUID> {

    List<ScreeningRequestJpaEntity> findByVacancyIdOrderByRequestedAtAsc(UUID vacancyId);
}
