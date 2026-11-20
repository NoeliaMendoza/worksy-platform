package com.plataformaempleo.api.jobapplication.domain.port.out;

import com.plataformaempleo.api.jobapplication.domain.model.ApplicationProgress;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import java.time.Instant;
import java.util.UUID;
import java.util.OptionalLong;
import java.util.List;
import java.util.Map;

public interface ApplicationProgressRepository {
    ApplicationStage stage(UUID applicationId);
    ApplicationProgress find(UUID applicationId);
    Map<UUID, List<ApplicationProgress.Milestone>> milestonesForCandidate(UUID candidateId);
    void recordReceived(UUID applicationId, UUID candidateId, Instant now);
    OptionalLong move(UUID applicationId, ApplicationStage expected, ApplicationStage next,
                 UUID changedBy, String message, Instant now);
}
