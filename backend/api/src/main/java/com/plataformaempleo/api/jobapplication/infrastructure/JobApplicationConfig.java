package com.plataformaempleo.api.jobapplication.infrastructure;

import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.jobapplication.application.JobApplicationService;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de postulaciones con sus adaptadores. */
@Configuration
class JobApplicationConfig {

    @Bean
    JobApplicationService jobApplicationService(JobApplicationRepository applications, VacancyRepository vacancies,
                                                CompanyRepository companies, CvDocumentRepository cvs,
                                                CandidateProfileRepository profiles, UserRepository users,
                                                CvStorage storage, Clock clock) {
        return new JobApplicationService(applications, vacancies, companies, cvs, profiles, users, storage, clock);
    }
}
