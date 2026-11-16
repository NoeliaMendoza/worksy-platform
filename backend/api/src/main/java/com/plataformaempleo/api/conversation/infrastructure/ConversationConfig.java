package com.plataformaempleo.api.conversation.infrastructure;

import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.conversation.application.ConversationService;
import com.plataformaempleo.api.conversation.domain.port.out.MessageRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta el caso de uso de conversaciones con sus adaptadores. */
@Configuration
class ConversationConfig {

    @Bean
    ConversationService conversationService(MessageRepository messages, JobApplicationRepository applications,
                                            VacancyRepository vacancies, CandidateProfileRepository profiles,
                                            UserRepository users, EventOutbox outbox, Clock clock) {
        return new ConversationService(messages, applications, vacancies, profiles, users, outbox, clock);
    }
}
