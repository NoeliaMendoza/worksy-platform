package com.plataformaempleo.worker.screening.domain.port.out;

/** Puerto de salida: texto del PDF. Un PDF sin texto (p. ej., escaneado) devuelve una cadena vacía. */
public interface TextExtractor {

    String extract(byte[] pdf);
}
