package com.plataformaempleo.api.candidate.domain.port.out;

import java.util.UUID;

/**
 * Puerto de salida: ¿algún proceso usa este CV? Lo implementa el módulo de postulaciones,
 * así el módulo del candidato no depende de él.
 */
public interface CvUsage {

    boolean isInUse(UUID cvId);
}
