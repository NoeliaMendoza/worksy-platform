package com.plataformaempleo.worker.screening.domain.port.out;

/** Puerto de salida: lee el PDF del almacenamiento de objetos. Fallos pasajeros: ScreeningFailure reintentable. */
public interface CvFiles {

    byte[] read(String storageKey);
}
