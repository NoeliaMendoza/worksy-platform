package com.plataformaempleo.api.vacancy.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVacancyRepository extends JpaRepository<VacancyJpaEntity, UUID> {

    List<VacancyJpaEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<VacancyJpaEntity> findByStatusOrderByPublishedAtDesc(String status, Pageable page);
}
