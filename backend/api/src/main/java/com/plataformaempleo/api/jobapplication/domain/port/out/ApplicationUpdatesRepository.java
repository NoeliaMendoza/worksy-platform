package com.plataformaempleo.api.jobapplication.domain.port.out;

import com.plataformaempleo.api.jobapplication.domain.model.ApplicationUpdates;
import java.util.UUID;

public interface ApplicationUpdatesRepository {
    ApplicationUpdates inbox(UUID candidateId);
    boolean markSeen(UUID candidateId, UUID applicationId, long lastSeenEventId);
}
