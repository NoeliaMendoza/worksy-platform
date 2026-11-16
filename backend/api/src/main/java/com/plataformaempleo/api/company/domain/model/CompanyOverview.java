package com.plataformaempleo.api.company.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Información administrativa de la empresa, sin datos de candidatos ni contenido de CV. */
public record CompanyOverview(UUID id, String name, Instant createdAt, long administrators,
                              long recruiters, long vacancies, long publishedVacancies) { }
