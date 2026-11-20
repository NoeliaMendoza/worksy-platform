package com.plataformaempleo.api.interview.infrastructure;

import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.interview.application.InterviewService;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationProgressRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InterviewConfig {
    @Bean
    InterviewService interviewService(InterviewRepository interviews, JobApplicationRepository applications, VacancyRepository vacancies,
                                      CandidateProfileRepository profiles, ApplicationProgressRepository progress,
                                      UserRepository users, EventOutbox outbox, Clock clock) {
        return new InterviewService(interviews, applications, vacancies, profiles, progress, users, outbox, clock);
    }
}
