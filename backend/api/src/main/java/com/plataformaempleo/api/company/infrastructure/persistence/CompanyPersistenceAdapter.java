package com.plataformaempleo.api.company.infrastructure.persistence;

import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link CompanyRepository}. */
@Component
class CompanyPersistenceAdapter implements CompanyRepository {

    private final SpringDataCompanyRepository jpa;

    CompanyPersistenceAdapter(SpringDataCompanyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Company save(Company c) {
        CompanyJpaEntity e = jpa.saveAndFlush(new CompanyJpaEntity(c.id(), c.name(), c.createdAt()));
        return new Company(e.getId(), e.getName(), e.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Company> findById(UUID id) {
        return jpa.findById(id).map(e -> new Company(e.getId(), e.getName(), e.getCreatedAt()));
    }
}
