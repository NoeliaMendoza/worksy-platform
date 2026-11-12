package com.plataformaempleo.worker.screening.infrastructure.text;

import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.plataformaempleo.worker.screening.domain.port.out.TextExtractor;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Extrae el texto del PDF con Apache PDFBox y normaliza los espacios. */
public class PdfBoxTextExtractor implements TextExtractor {

    @Override
    public String extract(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw ScreeningFailure.permanent("El PDF está protegido con contraseña");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalize(stripper.getText(document));
        } catch (IOException e) {
            throw ScreeningFailure.permanent("El PDF está dañado o no se puede leer");
        }
    }

    static String normalize(String text) {
        return text.replace(' ', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
