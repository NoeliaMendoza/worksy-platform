package com.plataformaempleo.api.vacancy.domain.port.out;

import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para las vacantes. */
public interface VacancyRepository {

    Vacancy save(Vacancy vacancy);

    Optional<Vacancy> findById(UUID id);

    /** Todas las vacantes de una empresa, de la más reciente a la más antigua. */
    List<Vacancy> findByCompany(UUID companyId);

    /** Vacantes publicadas de todas las empresas, de la más reciente a la más antigua. */
    List<Vacancy> findPublished(int limit);
}
