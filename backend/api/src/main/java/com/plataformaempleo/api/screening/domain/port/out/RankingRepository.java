package com.plataformaempleo.api.screening.domain.port.out;

import com.plataformaempleo.api.screening.domain.model.RankingResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    List<RankingResult> findSharedByVacancy(UUID vacancyId, String provider);
    void saveDecision(UUID applicationId, RankingResult.Decision decision, UUID reviewerId, Instant at);
}
