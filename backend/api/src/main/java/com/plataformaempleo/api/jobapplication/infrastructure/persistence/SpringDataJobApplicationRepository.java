package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataJobApplicationRepository extends JpaRepository<JobApplicationJpaEntity, UUID> {

    boolean existsByVacancyIdAndCandidateId(UUID vacancyId, UUID candidateId);

    boolean existsByCvId(UUID cvId);

    List<JobApplicationJpaEntity> findByCandidateIdOrderByAppliedAtDesc(UUID candidateId);

    List<JobApplicationJpaEntity> findByVacancyIdOrderByAppliedAtAsc(UUID vacancyId);
}
