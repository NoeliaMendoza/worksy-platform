package com.plataformaempleo.api.candidate.domain.port.out;

import com.plataformaempleo.api.candidate.domain.model.CvDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para los metadatos de los CV. */
public interface CvDocumentRepository {

    CvDocument save(CvDocument document);

    Optional<CvDocument> findById(UUID id);

    /** CV de un candidato, del más reciente al más antiguo. */
    List<CvDocument> findByCandidate(UUID candidateId);

    boolean existsByCandidateAndSha256(UUID candidateId, String sha256);

    long countByCandidate(UUID candidateId);

    void delete(UUID id);
}
