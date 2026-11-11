package com.plataformaempleo.api.candidate.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.api.candidate.SyntheticPdf;
import com.plataformaempleo.common.domain.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PdfFileTest {

    @Test
    void aceptaUnPdfYCalculaSuSha256() throws Exception {
        byte[] bytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        PdfFile file = PdfFile.of(bytes);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(file.sha256()).isEqualTo(expected).matches("[0-9a-f]{64}");
        assertThat(file.size()).isEqualTo(9);
    }

    @Test
    void elMismoContenidoDaElMismoHashYOtroContenidoOtro() {
        assertThat(PdfFile.of(SyntheticPdf.of("a")).sha256()).isEqualTo(PdfFile.of(SyntheticPdf.of("a")).sha256());
        assertThat(PdfFile.of(SyntheticPdf.of("a")).sha256()).isNotEqualTo(PdfFile.of(SyntheticPdf.of("b")).sha256());
    }

    @Test
    void aceptaBytesPreviosALaCabeceraDentroDelPrimerKilobyte() {
        byte[] bytes = ("x".repeat(500) + "%PDF-1.4\n").getBytes(StandardCharsets.US_ASCII);
        assertThat(PdfFile.of(bytes).size()).isEqualTo(bytes.length);
    }

    @Test
    void rechazaArchivoVacio() {
        assertCode(new byte[0], "cv.file.empty");
        assertCode(null, "cv.file.empty");
    }

    @Test
    void rechazaLoQueNoEsPdfAunqueSeLlame_pdf() {
        assertCode("PK\u0003\u0004 un docx renombrado".getBytes(StandardCharsets.ISO_8859_1), "cv.file.not-pdf");
        assertCode(("x".repeat(1100) + "%PDF-1.4").getBytes(StandardCharsets.US_ASCII), "cv.file.not-pdf");
    }

    @Test
    void aceptaExactamente5MbYRechazaUnByteMas() {
        assertThat(PdfFile.of(SyntheticPdf.ofSize((int) PdfFile.MAX_BYTES)).size()).isEqualTo(PdfFile.MAX_BYTES);
        assertCode(SyntheticPdf.ofSize((int) PdfFile.MAX_BYTES + 1), "cv.file.too-large");
    }

    @Test
    void noExponeSuArregloInterno() {
        PdfFile file = PdfFile.of(SyntheticPdf.of("c"));
        file.content()[0] = 'X';
        assertThat(file.content()[0]).isEqualTo((byte) '%');
    }

    private static void assertCode(byte[] content, String code) {
        assertThatThrownBy(() -> PdfFile.of(content))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }
}
