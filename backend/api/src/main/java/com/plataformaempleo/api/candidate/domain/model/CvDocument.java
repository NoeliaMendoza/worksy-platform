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
        Instant uploadedAt,
        String originalName) {

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
        originalName = cleanName(originalName);
    }

    public static CvDocument uploaded(UUID candidateId, PdfFile file, Instant now) {
        return uploaded(candidateId, file, now, null);
    }

    public static CvDocument uploaded(UUID candidateId, PdfFile file, Instant now, String originalName) {
        UUID id = UUID.randomUUID();
        // Llave aleatoria: no revela quién es el candidato ni el nombre original del archivo.
        return new CvDocument(id, candidateId, "cv/" + id + ".pdf", file.sha256(), PdfFile.MIME, file.size(),
                Status.UPLOADED, null, now, originalName);
    }

    public boolean belongsTo(UUID userId) {
        return candidateId.equals(userId);
    }

    private static String cleanName(String value) {
        if (value == null) return null;
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (name.isBlank()) return null;
        if (name.codePointCount(0, name.length()) > 180) name = name.substring(0, name.offsetByCodePoints(0, 180));
        return name;
    }

    @Override
    public String toString() { return "CvDocument[id=" + id + ", status=" + status + "]"; }
}
