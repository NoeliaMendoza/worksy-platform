package com.plataformaempleo.api.company.application;

import com.plataformaempleo.api.company.domain.model.CompanyPage;
import com.plataformaempleo.api.company.domain.port.out.CompanyDirectory;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

public class CompanyAdminService {
    private final CompanyDirectory companies;

    public CompanyAdminService(CompanyDirectory companies) { this.companies = companies; }

    @Transactional(readOnly = true)
    public CompanyPage list(Actor actor, String query, int page, int size) {
        actor.requireRole(Role.PLATFORM_ADMIN);
        String search = query == null ? "" : query.strip();
        if (page < 0 || size < 1 || size > 100 || search.codePointCount(0, search.length()) > 160) {
            throw new DomainException("company.directory.invalid", "La búsqueda admite hasta 160 caracteres y páginas de 1 a 100 empresas");
        }
        return companies.find(search.toLowerCase(Locale.ROOT), page, size);
    }
}
