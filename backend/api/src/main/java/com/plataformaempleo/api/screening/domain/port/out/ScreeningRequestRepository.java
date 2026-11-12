package com.plataformaempleo.api.screening.domain.port.out;

import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Puerto de salida para las solicitudes de preselección. */
public interface ScreeningRequestRepository {

    void saveAll(Collection<ScreeningRequest> requests);

    List<ScreeningRequest> findByIds(Collection<UUID> ids);

    /** Solicitudes de una vacante, de la más antigua a la más reciente. */
    List<ScreeningRequest> findByVacancy(UUID vacancyId);

    /** Solo una petición concurrente puede pasar la solicitud de FAILED a PENDING. */
    boolean retryFailed(UUID requestId, Instant now);
}
