package com.plataformaempleo.worker.screening.infrastructure.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

    /** PDF sintético generado en la prueba: no contiene datos de ninguna persona real. */
    static byte[] pdfWith(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                float y = 700;
                for (String line : lines) {
                    content.beginText();
                    content.newLineAtOffset(72, y);
                    content.showText(line);
                    content.endText();
                    y -= 18;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extraeElTextoDeUnPdf() throws IOException {
        String text = extractor.extract(pdfWith("Desarrolladora backend", "Java    y   Spring Boot"));

        assertThat(text).contains("Desarrolladora backend").contains("Java y Spring Boot");
    }

    @Test
    void unPdfSinTextoDevuelveVacio() throws IOException {
        assertThat(extractor.extract(pdfWith())).isEmpty();
    }

    @Test
    void unArchivoDanadoEsUnFalloDefinitivo() {
        assertThatThrownBy(() -> extractor.extract("%PDF-1.7 roto".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOfSatisfying(ScreeningFailure.class, f -> assertThat(f.retryable()).isFalse());
    }
}
