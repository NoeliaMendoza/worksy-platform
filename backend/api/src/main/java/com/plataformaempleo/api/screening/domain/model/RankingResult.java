package com.plataformaempleo.api.screening.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resultado visible para la empresa mientras el candidato mantiene su consentimiento. */
public record RankingResult(UUID applicationId, String fullName, String email, String headline, String location,
                            Instant appliedAt, ScreeningRequest.Status status, String modelVersion,
                            BigDecimal overallScore, BigDecimal confidence, List<Answer> answers,
                            Decision decision) {
    public enum Decision { preseleccionado, en_espera, descartado }

    public record Answer(String criterionId, String answer, BigDecimal score, BigDecimal probability) { }

    public boolean analyzed() {
        return (status == ScreeningRequest.Status.COMPLETED || status == ScreeningRequest.Status.NEEDS_REVIEW)
                && overallScore != null && confidence != null;
    }
}
