package com.plataformaempleo.api.jobapplication.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvDocumentRepository;
import com.plataformaempleo.api.candidate.domain.port.out.CvStorage;
import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationProgress;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationProgressRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.common.events.EventTypes;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Postulaciones. La persona candidata postula con uno de sus CV, ve sus postulaciones y puede retirarlas.
 * La empresa ve quién se postuló a sus vacantes, y solo ve los datos de quien mantiene el permiso de compartirlos.
 */
public class JobApplicationService {

    /** VALOR SUPUESTO: vigencia del enlace para ver un CV. Corta, porque el enlace funciona sin sesión. */
    public static final Duration CV_LINK_VALIDITY = Duration.ofMinutes(5);

    private final JobApplicationRepository applications;
    private final VacancyRepository vacancies;
    private final CompanyRepository companies;
    private final CvDocumentRepository cvs;
    private final CandidateProfileRepository profiles;
    private final UserRepository users;
    private final CvStorage storage;
    private final Clock clock;
    private final ApplicationProgressRepository progress;
    private final EventOutbox outbox;

    public JobApplicationService(JobApplicationRepository applications, VacancyRepository vacancies,
                                 CompanyRepository companies, CvDocumentRepository cvs,
                                 CandidateProfileRepository profiles, UserRepository users, CvStorage storage,
                                 Clock clock, ApplicationProgressRepository progress, EventOutbox outbox) {
        this.applications = Objects.requireNonNull(applications);
        this.vacancies = Objects.requireNonNull(vacancies);
        this.companies = Objects.requireNonNull(companies);
        this.cvs = Objects.requireNonNull(cvs);
        this.profiles = Objects.requireNonNull(profiles);
        this.users = Objects.requireNonNull(users);
        this.storage = Objects.requireNonNull(storage);
        this.clock = Objects.requireNonNull(clock);
        this.progress = Objects.requireNonNull(progress);
        this.outbox = Objects.requireNonNull(outbox);
    }

    // ---- Persona candidata ----

    @Transactional
    public JobApplication apply(Actor actor, UUID vacancyId, UUID cvId) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        Vacancy vacancy = vacancies.findById(vacancyId)
                .filter(v -> v.status() == Vacancy.Status.PUBLISHED)
                .orElseThrow(JobApplicationService::vacancyNotFound);
        cvs.findById(cvId)
                .filter(cv -> cv.belongsTo(candidateId))
                .orElseThrow(() -> new DomainException("cv.not-found", "El CV no existe"));
        if (!sharesWithRecruiters(candidateId)) {
            throw new DomainException("application.consent.required",
                    "Para postular, permite que las empresas a las que te postules vean tu CV");
        }
        if (applications.existsByVacancyAndCandidate(vacancy.id(), candidateId)) {
            throw new DomainException("application.duplicate", "Ya te postulaste a esta vacante");
        }
        JobApplication created = applications.save(JobApplication.submit(vacancy.id(), candidateId, cvId, clock.instant()));
        progress.recordReceived(created.id(), candidateId, created.appliedAt());
        return created;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CandidateView> mine(Actor actor) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        var milestones = progress.milestonesForCandidate(candidateId);
        return applications.findByCandidate(candidateId).stream()
                .map(a -> vacancies.findById(a.vacancyId())
                        .map(v -> new CandidateView(a, v.content().title(), companyName(v.companyId()), v.status(), progress.stage(a.id()),
                                List.copyOf(milestones.getOrDefault(a.id(), List.of()))))
                        .orElseThrow())
                .toList();
    }

    @Transactional
    public void withdraw(Actor actor, UUID applicationId) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        JobApplication application = applications.findById(applicationId)
                .filter(a -> a.belongsTo(candidateId))
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
        applications.delete(application.id());
    }

    /** Postulación vista por quien se postuló: con el título de la vacante y la empresa. */
    public record CandidateView(JobApplication application, String vacancyTitle, String companyName,
                                Vacancy.Status vacancyStatus, ApplicationStage stage, List<ApplicationProgress.Milestone> milestones) {
    }

    // ---- Empresa ----

    @Transactional(readOnly = true)
    public List<RecruiterView> forVacancy(Actor actor, UUID vacancyId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Vacancy vacancy = vacancies.findById(vacancyId)
                .filter(v -> v.companyId().equals(companyId))
                .orElseThrow(JobApplicationService::vacancyNotFound);
        return applications.findByVacancy(vacancy.id()).stream().map(this::recruiterView).toList();
    }

    /**
     * Postulación vista por la empresa. Si la persona retiró el permiso de compartir su CV,
     * la postulación sigue contando pero sin datos personales ({@code shared = false}).
     */
    public record RecruiterView(UUID applicationId, Instant appliedAt, boolean shared, String candidateEmail,
                                UUID cvId, ApplicationStage stage) {
    }

    /**
     * Enlace temporal para que la empresa vea el CV de una postulación a su vacante.
     * Solo mientras la persona mantenga el permiso de compartirlo.
     */
    @Transactional(readOnly = true)
    public CvLink cvLink(Actor actor, UUID vacancyId, UUID applicationId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        vacancies.findById(vacancyId)
                .filter(v -> v.companyId().equals(companyId))
                .orElseThrow(JobApplicationService::vacancyNotFound);
        JobApplication application = applications.findById(applicationId)
                .filter(a -> a.vacancyId().equals(vacancyId))
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
        if (!sharesWithRecruiters(application.candidateId())) {
            throw new DomainException("access.cv-not-shared", "La persona retiró el permiso de compartir su CV");
        }
        var cv = cvs.findById(application.cvId())
                .orElseThrow(() -> new DomainException("cv.not-found", "El CV no existe"));
        return new CvLink(storage.temporaryReadUrl(cv.storageKey(), CV_LINK_VALIDITY),
                clock.instant().plus(CV_LINK_VALIDITY));
    }

    /** Enlace de solo lectura al PDF y el momento en que deja de funcionar. */
    public record CvLink(URI url, Instant expiresAt) {
    }

    private RecruiterView recruiterView(JobApplication a) {
        if (!sharesWithRecruiters(a.candidateId())) {
            return new RecruiterView(a.id(), a.appliedAt(), false, null, null, null);
        }
        String email = users.findById(a.candidateId()).map(User::email).orElse(null);
        return new RecruiterView(a.id(), a.appliedAt(), true, email, a.cvId(), progress.stage(a.id()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationProgress candidateProgress(Actor actor, UUID applicationId) {
        UUID candidateId = actor.requireRole(Role.CANDIDATE);
        applications.findById(applicationId).filter(a -> a.belongsTo(candidateId))
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
        return progress.find(applicationId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationProgress companyProgress(Actor actor, UUID vacancyId, UUID applicationId) {
        requireSharedApplication(actor, vacancyId, applicationId);
        return progress.find(applicationId);
    }

    @Transactional
    public ApplicationProgress moveStage(Actor actor, UUID vacancyId, UUID applicationId,
                                         ApplicationStage expected, ApplicationStage next, String message) {
        JobApplication application = requireSharedApplication(actor, vacancyId, applicationId);
        if (expected == null || next == null || !expected.canMoveTo(next)) {
            throw new DomainException("application.stage.invalid", "Ese cambio de etapa no está permitido");
        }
        String normalized = message == null || message.isBlank() ? null : message.strip();
        if (normalized != null && normalized.length() > 500) {
            throw new DomainException("application.stage.message", "El mensaje no puede superar 500 caracteres");
        }
        var changed = progress.move(applicationId, expected, next, actor.userId(), normalized, clock.instant());
        if (changed.isEmpty()) {
            throw new DomainException("application.stage.conflict", "Otra persona cambió la etapa. Actualiza antes de continuar");
        }
        outbox.append("application", applicationId, EventTypes.APPLICATION_STAGE_CHANGED, Map.of(
                "applicationId", applicationId.toString(), "stageChangeId", changed.getAsLong(),
                "recipientIds", List.of(application.candidateId().toString())));
        return progress.find(applicationId);
    }

    private JobApplication requireSharedApplication(Actor actor, UUID vacancyId, UUID applicationId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        vacancies.findById(vacancyId).filter(v -> v.companyId().equals(companyId))
                .orElseThrow(JobApplicationService::vacancyNotFound);
        JobApplication application = applications.findById(applicationId)
                .filter(a -> a.vacancyId().equals(vacancyId))
                .orElseThrow(() -> new DomainException("application.not-found", "La postulación no existe"));
        if (!sharesWithRecruiters(application.candidateId())) {
            throw new DomainException("access.cv-not-shared", "La persona retiró el permiso de compartir su CV");
        }
        return application;
    }

    // ---- reglas ----

    private boolean sharesWithRecruiters(UUID candidateId) {
        return profiles.findConsents(candidateId).map(CandidateConsents::shareWithRecruiters).orElse(false);
    }

    private String companyName(UUID companyId) {
        return companies.findById(companyId).map(Company::name).orElse("");
    }

    private static DomainException vacancyNotFound() {
        return new DomainException("vacancy.not-found", "La vacante no existe");
    }
}
