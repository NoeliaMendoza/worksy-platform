package com.plataformaempleo.api.company.domain.model;

import java.util.List;

public record CompanyPage(List<CompanyOverview> items, long total, int page, int size) {
    public CompanyPage { items = List.copyOf(items); }
}
