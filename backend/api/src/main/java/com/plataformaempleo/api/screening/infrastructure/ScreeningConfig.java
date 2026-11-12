package com.plataformaempleo.api.screening.infrastructure;

import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.screening.application.ScreeningService;
import com.plataformaempleo.api.screening.application.RankingService;
import com.plataformaempleo.api.screening.domain.port.out.RankingRepository;
import com.plataformaempleo.api.screening.domain.port.out.ScreeningRequestRepository;
import com.plataformaempleo.api.shared.application.AuditRecorder;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.shared.domain.port.out.IdempotencyStore;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de preselección con sus adaptadores. */
@Configuration
class ScreeningConfig {

    @Bean
    RankingService rankingService(RankingRepository results, VacancyRepository vacancies,
                                  @Value("${worksy.screening.provider:baseline}") String provider, Clock clock, AuditRecorder audit) {
        return new RankingService(results, vacancies, provider, clock, audit);
    }

    @Bean
    ScreeningService screeningService(ScreeningRequestRepository requests, JobApplicationRepository applications,
                                      VacancyRepository vacancies, CandidateProfileRepository profiles,
                                      EventOutbox outbox, IdempotencyStore idempotency,
                                      @Value("${worksy.screening.provider:baseline}") String provider, Clock clock, AuditRecorder audit) {
        return new ScreeningService(requests, applications, vacancies, profiles, outbox, idempotency, provider, clock, audit);
    }
}
