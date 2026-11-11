package com.plataformaempleo.api.candidate.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadatos de un CV. El archivo vive en el almacenamiento de objetos bajo {@code storageKey};
 * la base de datos nunca guarda el PDF.
 */
public record CvDocument(
        UUID id,
        UUID candidateId,
        String storageKey,
        String sha256,
        String mime,
        long sizeBytes,
        Status status,
        String language,
        Instant uploadedAt) {

    /** Por ahora solo existe UPLOADED; el trabajador de preselección añadirá los siguientes estados. */
    public enum Status { UPLOADED }

    public CvDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(mime, "mime");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
    }

    public static CvDocument uploaded(UUID candidateId, PdfFile file, Instant now) {
        UUID id = UUID.randomUUID();
        // Llave aleatoria: no revela quién es el candidato ni el nombre original del archivo.
        return new CvDocument(id, candidateId, "cv/" + id + ".pdf", file.sha256(), PdfFile.MIME, file.size(),
                Status.UPLOADED, null, now);
    }

    public boolean belongsTo(UUID userId) {
        return candidateId.equals(userId);
    }
}
