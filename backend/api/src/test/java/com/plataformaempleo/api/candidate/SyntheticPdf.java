package com.plataformaempleo.api.candidate;

import java.nio.charset.StandardCharsets;

/** PDF mínimos y sintéticos para pruebas: no contienen datos de ninguna persona. */
public final class SyntheticPdf {

    private SyntheticPdf() {
    }

    /** Un PDF válido en su cabecera; {@code marker} cambia el contenido y, con él, el hash. */
    public static byte[] of(String marker) {
        String body = "%PDF-1.7\n% CV sintetico " + marker + "\n1 0 obj << /Type /Catalog >> endobj\ntrailer << >>\n%%EOF\n";
        return body.getBytes(StandardCharsets.US_ASCII);
    }

    /** Un PDF que ocupa exactamente {@code size} bytes. */
    public static byte[] ofSize(int size) {
        byte[] bytes = new byte[size];
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        for (int i = header.length; i < size; i++) {
            bytes[i] = ' ';
        }
        return bytes;
    }
}
