package com.plataformaempleo.worker.screening.domain.port.out;

import com.plataformaempleo.worker.screening.domain.model.Criterion;
import com.plataformaempleo.worker.screening.domain.model.Decision;
import java.util.List;

/**
 * Puerto de salida hacia el servicio de inferencia (POST /v1/decide, sección 7 del diseño).
 * El adaptador aplica el tiempo límite y los reintentos.
 */
public interface DecisionProvider {

    Decision decide(String provider, String cvText, String jobDescription, List<Criterion> criteria);
}
