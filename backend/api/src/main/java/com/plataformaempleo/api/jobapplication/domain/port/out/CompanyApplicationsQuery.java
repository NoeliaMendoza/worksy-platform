package com.plataformaempleo.api.jobapplication.domain.port.out;

import com.plataformaempleo.api.jobapplication.domain.model.CompanyApplication;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida de lectura: postulaciones de todas las vacantes de una empresa.
 * La búsqueda por texto solo mira datos personales de quien los comparte; de las demás postulaciones
 * solo puede coincidir el título de la vacante.
 */
public interface CompanyApplicationsQuery {

    /** De la más reciente a la más antigua. */
    CompanyApplication.Page search(UUID companyId, CompanyApplication.Filter filter, int page, int size);

    /** Vacío si la postulación no existe o no es de una vacante de esa empresa. */
    Optional<CompanyApplication> find(UUID companyId, UUID applicationId);
}
