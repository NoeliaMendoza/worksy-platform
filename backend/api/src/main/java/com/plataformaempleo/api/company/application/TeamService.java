package com.plataformaempleo.api.company.application;

import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.company.domain.port.out.TeamInvitationMailer;
import com.plataformaempleo.api.identity.domain.model.PasswordResetToken;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.SecureTokens;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.PasswordHasher;
import com.plataformaempleo.api.identity.domain.port.out.PasswordResetTokenRepository;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empresa del usuario y su equipo. Toda consulta se limita a la empresa de quien la hace.
 * Invitar crea la cuenta del reclutador sin contraseña conocida y le envía un enlace para elegirla
 * (el mismo mecanismo que recuperar contraseña).
 */
public class TeamService {

    private final CompanyRepository companies;
    private final UserRepository users;
    private final PasswordResetTokenRepository activationTokens;
    private final PasswordHasher hasher;
    private final TeamInvitationMailer mailer;
    private final Clock clock;
    private final Duration invitationTtl;
    private final String activationPageUrl;

    public TeamService(CompanyRepository companies, UserRepository users,
                       PasswordResetTokenRepository activationTokens, PasswordHasher hasher,
                       TeamInvitationMailer mailer, Clock clock, Duration invitationTtl, String activationPageUrl) {
        this.companies = Objects.requireNonNull(companies);
        this.users = Objects.requireNonNull(users);
        this.activationTokens = Objects.requireNonNull(activationTokens);
        this.hasher = Objects.requireNonNull(hasher);
        this.mailer = Objects.requireNonNull(mailer);
        this.clock = Objects.requireNonNull(clock);
        this.invitationTtl = Objects.requireNonNull(invitationTtl);
        this.activationPageUrl = Objects.requireNonNull(activationPageUrl);
    }

    @Transactional(readOnly = true)
    public Company companyOf(Actor actor) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return companies.findById(companyId)
                .orElseThrow(() -> new DomainException("company.not-found", "La empresa no existe"));
    }

    @Transactional(readOnly = true)
    public List<User> members(Actor actor) {
        return users.findActiveByCompany(actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER));
    }

    /** Solo la administración de la empresa invita. El correo no puede tener ya una cuenta activa. */
    @Transactional
    public User inviteRecruiter(Actor actor, String email) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN);
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new DomainException("company.not-found", "La empresa no existe"));
        String normalized = User.normalizeEmail(email);
        if (users.findActiveByEmail(normalized).isPresent()) {
            throw new DomainException("user.email.taken", "Ya existe una cuenta con ese correo");
        }
        User recruiter = users.save(User.register(normalized,
                // Contraseña aleatoria que nadie conoce: la persona elige la suya con el enlace.
                hasher.hash(SecureTokens.newSecret()), Role.RECRUITER, companyId, clock));

        PasswordResetToken.Issued activation = PasswordResetToken.issue(recruiter.id(), clock.instant(), invitationTtl);
        activationTokens.save(activation.token());
        String link = activationPageUrl + "?invitacion=1&token="
                + URLEncoder.encode(activation.secret(), StandardCharsets.UTF_8);
        mailer.sendInvitation(recruiter.email(), company.name(), link, invitationTtl.toHours());
        return recruiter;
    }
}
