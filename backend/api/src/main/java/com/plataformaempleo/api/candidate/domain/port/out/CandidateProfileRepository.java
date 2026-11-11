package com.plataformaempleo.api.candidate.domain.port.out;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para el perfil del candidato (por ahora, sus consentimientos). */
public interface CandidateProfileRepository {

    Optional<CandidateConsents> findConsents(UUID userId);

    /** Crea el perfil si no existe o actualiza sus consentimientos. */
    CandidateConsents saveConsents(UUID userId, CandidateConsents consents);
}
