package com.plataformaempleo.api.company.domain.port.out;

import com.plataformaempleo.api.company.domain.model.Company;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para las empresas. */
public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);
}
