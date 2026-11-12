package com.plataformaempleo.api.jobapplication.domain.port.out;

import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para las postulaciones. */
public interface JobApplicationRepository {

    JobApplication save(JobApplication application);

    Optional<JobApplication> findById(UUID id);

    boolean existsByVacancyAndCandidate(UUID vacancyId, UUID candidateId);

    /** Postulaciones de una persona, de la más reciente a la más antigua. */
    List<JobApplication> findByCandidate(UUID candidateId);

    /** Postulaciones a una vacante, en orden de llegada. */
    List<JobApplication> findByVacancy(UUID vacancyId);

    void delete(UUID id);
}
