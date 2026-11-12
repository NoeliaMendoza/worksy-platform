package com.plataformaempleo.api.vacancy.infrastructure;

import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.shared.application.AuditRecorder;
import com.plataformaempleo.api.vacancy.application.VacancyService;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de vacantes con sus adaptadores. */
@Configuration
class VacancyConfig {

    @Bean
    VacancyService vacancyService(VacancyRepository vacancies, CompanyRepository companies, Clock clock, AuditRecorder audit) {
        return new VacancyService(vacancies, companies, clock, audit);
    }
}
