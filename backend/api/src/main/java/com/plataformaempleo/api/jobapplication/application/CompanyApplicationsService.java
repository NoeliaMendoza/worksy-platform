package com.plataformaempleo.api.jobapplication.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.jobapplication.domain.model.CompanyApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.CompanyApplicationsQuery;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Pantalla "Candidatos": todas las postulaciones de las vacantes de la empresa, con filtros.
 * Solo la administración y el equipo de reclutamiento de esa empresa.
 */
public class CompanyApplicationsService {

    /** VALORES SUPUESTOS: tamaño de página por defecto y máximo. */
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    private final CompanyApplicationsQuery query;

    public CompanyApplicationsService(CompanyApplicationsQuery query) {
        this.query = Objects.requireNonNull(query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanyApplication.Page search(Actor actor, CompanyApplication.Filter filter, Integer page, Integer size) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_SIZE) {
            throw new DomainException("pagination.invalid",
                    "La página debe ser 0 o mayor y el tamaño estar entre 1 y " + MAX_SIZE);
        }
        return query.search(companyId, filter, p, s);
    }

    @Transactional(readOnly = true)
    public CompanyApplication find(Actor actor, UUID applicationId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return query.find(companyId, applicationId)
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
    }
}
