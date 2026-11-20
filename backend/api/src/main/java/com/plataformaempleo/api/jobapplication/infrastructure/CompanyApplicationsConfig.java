package com.plataformaempleo.api.jobapplication.infrastructure;

import com.plataformaempleo.api.jobapplication.application.CompanyApplicationsService;
import com.plataformaempleo.api.jobapplication.domain.port.out.CompanyApplicationsQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta la pantalla "Candidatos" con su adaptador de lectura. */
@Configuration
class CompanyApplicationsConfig {

    @Bean
    CompanyApplicationsService companyApplicationsService(CompanyApplicationsQuery query) {
        return new CompanyApplicationsService(query);
    }
}
