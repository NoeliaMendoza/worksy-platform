package com.plataformaempleo.api.candidate.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Un PDF ya validado y listo para guardar. No confía en el nombre ni en el tipo que declara el
 * navegador: comprueba la firma {@code %PDF-} del propio contenido.
 */
public final class PdfFile {

    public static final String MIME = "application/pdf";
    /** Tamaño máximo según el diseño técnico (sección 5): 5 MB. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    /** La especificación de PDF permite basura antes de la cabecera, pero dentro del primer kilobyte. */
    private static final int HEADER_WINDOW = 1024;
    private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final byte[] content;
    private final String sha256;

    private PdfFile(byte[] content, String sha256) {
        this.content = content;
        this.sha256 = sha256;
    }

    public static PdfFile of(byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainException("cv.file.empty", "El archivo está vacío");
        }
        if (content.length > MAX_BYTES) {
            throw new DomainException("cv.file.too-large", "El archivo supera el máximo de 5 MB");
        }
        if (!hasPdfSignature(content)) {
            throw new DomainException("cv.file.not-pdf", "El archivo no es un PDF");
        }
        return new PdfFile(content.clone(), sha256Of(content));
    }

    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }

    public String sha256() {
        return sha256;
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        int last = Math.min(bytes.length, HEADER_WINDOW) - SIGNATURE.length;
        for (int start = 0; start <= last; start++) {
            boolean match = true;
            for (int i = 0; i < SIGNATURE.length && match; i++) {
                match = bytes[start + i] == SIGNATURE[i];
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static String sha256Of(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
