package com.plataformaempleo.api.jobapplication.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationUpdates;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationUpdatesRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

public class ApplicationUpdatesService {
    private final ApplicationUpdatesRepository updates;
    private final JobApplicationRepository applications;

    public ApplicationUpdatesService(ApplicationUpdatesRepository updates, JobApplicationRepository applications) {
        this.updates = Objects.requireNonNull(updates);
        this.applications = Objects.requireNonNull(applications);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationUpdates inbox(Actor actor) {
        return updates.inbox(actor.requireRole(Role.CANDIDATE));
    }

    @Transactional
    public void markSeen(Actor actor, UUID applicationId, long lastSeenEventId) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        applications.findById(applicationId).filter(a -> a.belongsTo(candidateId))
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
        if (lastSeenEventId <= 0 || !updates.markSeen(candidateId, applicationId, lastSeenEventId)) {
            throw new DomainException("application.progress.invalid", "El cambio indicado no pertenece a esta postulación");
        }
    }
}
