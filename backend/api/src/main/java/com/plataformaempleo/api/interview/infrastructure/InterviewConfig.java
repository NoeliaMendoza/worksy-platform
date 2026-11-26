package com.plataformaempleo.api.interview.infrastructure;

import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.interview.application.InterviewCallService;
import com.plataformaempleo.api.interview.application.InterviewService;
import com.plataformaempleo.api.interview.domain.port.out.CallRepository;
import com.plataformaempleo.api.interview.domain.port.out.CallTokenIssuer;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Servidores ICE para la videollamada ({@code worksy.call.ice-servers}, separados por comas). Sin valores, solo se
     * conectan equipos de la misma red; el TURN propio con credenciales temporales llega en la Entrega 3.
     */
    @Bean
    InterviewCallService interviewCallService(InterviewRepository interviews, CallRepository calls, CallTokenIssuer tokens,
                                              JobApplicationRepository applications, VacancyRepository vacancies,
                                              ApplicationProgressRepository progress, UserRepository users,
                                              @Value("${worksy.call.ice-servers:}") String[] iceServers, Clock clock) {
        List<String> servers = Arrays.stream(iceServers).map(String::strip).filter(s -> s.matches("^stuns?:[^\\s]+$")).toList();
        return new InterviewCallService(interviews, calls, tokens, applications, vacancies, progress, users, servers, clock);
    }
}
