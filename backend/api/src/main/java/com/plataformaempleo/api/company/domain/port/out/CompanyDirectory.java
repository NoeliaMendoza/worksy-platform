package com.plataformaempleo.api.company.domain.port.out;

import com.plataformaempleo.api.company.domain.model.CompanyPage;

public interface CompanyDirectory {
    CompanyPage find(String query, int page, int size);
}
