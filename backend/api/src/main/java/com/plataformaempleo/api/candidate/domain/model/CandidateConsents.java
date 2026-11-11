package com.plataformaempleo.api.candidate.domain.model;

import java.time.Instant;

/**
 * Consentimientos explícitos del candidato. Sin una decisión suya, todo queda en {@code false}.
 *
 * @param shareWithRecruiters permite que los reclutadores de las vacantes a las que postula vean su perfil y su CV
 * @param githubLink          permite mostrar su GitHub vinculado (ampliación opcional del diseño)
 * @param updatedAt           momento de la última decisión, o {@code null} si nunca decidió
 */
public record CandidateConsents(boolean shareWithRecruiters, boolean githubLink, Instant updatedAt) {

    public static CandidateConsents none() {
        return new CandidateConsents(false, false, null);
    }
}
