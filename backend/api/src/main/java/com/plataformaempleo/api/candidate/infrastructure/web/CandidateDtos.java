package com.plataformaempleo.api.candidate.infrastructure.web;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.model.CvDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Contratos JSON del candidato. No exponen la llave de almacenamiento ni el hash del archivo. */
final class CandidateDtos {

    private CandidateDtos() {
    }

    @Schema(name = "CvDocument", description = "CV subido por el candidato")
    record CvResponse(
            UUID id,
            @Schema(example = "245760") long sizeBytes,
            @Schema(example = "UPLOADED") CvDocument.Status status,
            Instant uploadedAt) {

        static CvResponse from(CvDocument d) {
            return new CvResponse(d.id(), d.sizeBytes(), d.status(), d.uploadedAt());
        }
    }

    @Schema(name = "CandidateConsents", description = "Qué permite ver el candidato a los reclutadores")
    record ConsentsResponse(
            @Schema(description = "Los reclutadores de las vacantes a las que postula pueden ver su perfil y su CV")
            boolean shareWithRecruiters,
            @Schema(description = "Se puede mostrar su GitHub vinculado") boolean githubLink,
            @Schema(description = "Última decisión; vacío si nunca decidió") Instant updatedAt) {

        static ConsentsResponse from(CandidateConsents c) {
            return new ConsentsResponse(c.shareWithRecruiters(), c.githubLink(), c.updatedAt());
        }
    }

    @Schema(name = "CandidateConsentsRequest")
    record ConsentsRequest(@NotNull Boolean shareWithRecruiters, @NotNull Boolean githubLink) {
    }
}
