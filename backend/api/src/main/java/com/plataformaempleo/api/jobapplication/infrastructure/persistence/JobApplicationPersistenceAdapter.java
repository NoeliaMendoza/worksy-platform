package com.plataformaempleo.api.jobapplication.infrastructure.persistence;

import com.plataformaempleo.api.candidate.domain.port.out.CvUsage;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JPA de las postulaciones. También responde al puerto {@link CvUsage} del módulo
 * del candidato: un CV está en uso si alguna postulación lo referencia.
 */
@Component
class JobApplicationPersistenceAdapter implements JobApplicationRepository, CvUsage {

    private final SpringDataJobApplicationRepository jpa;

    JobApplicationPersistenceAdapter(SpringDataJobApplicationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public JobApplication save(JobApplication a) {
        return toDomain(jpa.saveAndFlush(
                new JobApplicationJpaEntity(a.id(), a.vacancyId(), a.candidateId(), a.cvId(), a.appliedAt())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobApplication> findById(UUID id) {
        return jpa.findById(id).map(JobApplicationPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByVacancyAndCandidate(UUID vacancyId, UUID candidateId) {
        return jpa.existsByVacancyIdAndCandidateId(vacancyId, candidateId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplication> findByCandidate(UUID candidateId) {
        return jpa.findByCandidateIdOrderByAppliedAtDesc(candidateId).stream()
                .map(JobApplicationPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplication> findByVacancy(UUID vacancyId) {
        return jpa.findByVacancyIdOrderByAppliedAtAsc(vacancyId).stream()
                .map(JobApplicationPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInUse(UUID cvId) {
        return jpa.existsByCvId(cvId);
    }

    private static JobApplication toDomain(JobApplicationJpaEntity e) {
        return new JobApplication(e.getId(), e.getVacancyId(), e.getCandidateId(), e.getCvId(), e.getAppliedAt());
    }
}
