package com.plataformaempleo.api.company.infrastructure;

import com.plataformaempleo.api.company.application.CompanyRegistrationService;
import com.plataformaempleo.api.company.application.TeamService;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.company.domain.port.out.TeamInvitationMailer;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.identity.infrastructure.security.AuthProperties;
import com.plataformaempleo.api.shared.application.AuditRecorder;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso de empresa con sus adaptadores. */
@Configuration
class CompanyConfig {

    @Bean
    CompanyRegistrationService companyRegistrationService(CompanyRepository companies, UserRepository users,
                                                          PasswordHasher hasher, Clock clock, AuditRecorder audit) {
        return new CompanyRegistrationService(companies, users, hasher, clock, audit);
    }

    /** VALOR SUPUESTO: la invitación vale 72 horas (confirmar con el diseño técnico). */
    @Bean
    TeamService teamService(CompanyRepository companies, UserRepository users,
                            PasswordResetTokenRepository activationTokens, PasswordHasher hasher,
                            TeamInvitationMailer mailer, Clock clock, AuthProperties auth,
                            @Value("${worksy.company.invitation-ttl:72h}") Duration invitationTtl) {
        return new TeamService(companies, users, activationTokens, hasher, mailer, clock, invitationTtl,
                auth.publicUrl() + "/restablecer");
    }
}
