package com.plataformaempleo.api.interview.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationProgressRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import com.plataformaempleo.common.events.EventTypes;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class InterviewService {
    public static final int PAGE_SIZE = 20;
    private final InterviewRepository interviews;
    private final JobApplicationRepository applications;
    private final VacancyRepository vacancies;
    private final CandidateProfileRepository profiles;
    private final ApplicationProgressRepository progress;
    private final UserRepository users;
    private final EventOutbox outbox;
    private final Clock clock;

    public InterviewService(InterviewRepository interviews, JobApplicationRepository applications, VacancyRepository vacancies,
                            CandidateProfileRepository profiles, ApplicationProgressRepository progress,
                            UserRepository users, EventOutbox outbox, Clock clock) {
        this.interviews = interviews; this.applications = applications; this.vacancies = vacancies;
        this.profiles = profiles; this.progress = progress; this.users = users; this.outbox = outbox; this.clock = clock;
    }

    public record Context(UUID applicationId, String vacancyTitle, String candidateEmail) { }
    public record Page(List<InterviewRepository.Item> items, boolean hasMore) { }
    private record Application(JobApplication application, Vacancy vacancy) { }

    @Transactional(readOnly = true)
    public Context context(Actor actor, UUID applicationId) {
        Application app = companyApplication(actor, applicationId, true);
        return new Context(applicationId, app.vacancy().content().title(), users.findById(app.application().candidateId()).orElseThrow().email());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(Actor actor, Interview.View view, int page) {
        if (page < 0 || page > 10000) throw new DomainException("interview.invalid", "Página inválida");
        UUID company = actor.role() == Role.CANDIDATE ? null : actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        var rows = interviews.list(actor.userId(), company, view, clock.instant(), page * PAGE_SIZE, PAGE_SIZE + 1);
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), PAGE_SIZE))), rows.size() > PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public long unread(Actor actor) {
        UUID company = actor.role() == Role.CANDIDATE ? null : actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return interviews.unread(actor.userId(), company);
    }

    @Transactional
    public Interview schedule(Actor actor, UUID id, UUID applicationId, String title, Instant startsAt,
                              int duration, String zone, Interview.Mode mode, String place, String note) {
        Application app = companyApplication(actor, applicationId, true);
        Interview proposed = Interview.schedule(id, applicationId, title, startsAt, duration, zone, mode, place, note, clock.instant());
        if (!interviews.insert(proposed)) {
            Interview stored = interviews.find(id).orElseThrow(InterviewService::conflict);
            if (!stored.applicationId().equals(applicationId)) throw notFound();
            if (!stored.sameInvitation(proposed)) throw conflict();
            return stored;
        }
        changed(actor, app, proposed, Interview.Change.SCHEDULED);
        return proposed;
    }

    @Transactional
    public Interview update(Actor actor, UUID id, int expectedVersion, String title, Instant startsAt,
                            int duration, String zone, Interview.Mode mode, String place, String note) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), true);
        requireVersion(current, expectedVersion);
        Interview next = current.update(title, startsAt, duration, zone, mode, place, note, clock.instant());
        saveChange(actor, app, next, expectedVersion, Interview.Change.UPDATED);
        return next;
    }

    @Transactional
    public Interview respond(Actor actor, UUID id, int expectedVersion, Interview.Status response) {
        actor.requireRole(Role.CANDIDATE);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = participant(actor, current);
        requireVersion(current, expectedVersion);
        Interview next = current.respond(response, clock.instant());
        saveChange(actor, app, next, expectedVersion, response == Interview.Status.CONFIRMED ? Interview.Change.CONFIRMED : Interview.Change.DECLINED);
        return next;
    }

    @Transactional
    public Interview cancel(Actor actor, UUID id, int expectedVersion) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), false);
        requireVersion(current, expectedVersion);
        Interview next = current.cancel(clock.instant());
        saveChange(actor, app, next, expectedVersion, Interview.Change.CANCELLED);
        return next;
    }

    @Transactional(readOnly = true)
    public List<InterviewRepository.Event> history(Actor actor, UUID id) {
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        participant(actor, current);
        return interviews.history(id);
    }

    @Transactional
    public void markRead(Actor actor, UUID id, int version) {
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        participant(actor, current);
        if (version < 0 || version > current.version()) throw new DomainException("interview.invalid", "La versión indicada no existe");
        interviews.markRead(id, actor.userId(), version);
    }

    private void saveChange(Actor actor, Application app, Interview next, int expected, Interview.Change change) {
        if (!interviews.update(next, expected)) throw conflict();
        changed(actor, app, next, change);
    }

    private void changed(Actor actor, Application app, Interview interview, Interview.Change change) {
        interviews.record(interview, change, actor.userId());
        interviews.markRead(interview.id(), actor.userId(), interview.version());
        var recipients = new LinkedHashSet<UUID>();
        recipients.add(app.application().candidateId());
        users.findActiveByCompany(app.vacancy().companyId()).forEach(user -> recipients.add(user.id()));
        recipients.remove(actor.userId());
        outbox.append("interview", interview.id(), EventTypes.INTERVIEW_CHANGED, Map.of(
                "interviewId", interview.id().toString(), "applicationId", app.application().id().toString(),
                "recipientIds", recipients.stream().map(UUID::toString).toList()));
    }

    private Application participant(Actor actor, Interview interview) {
        if (actor.role() == Role.CANDIDATE) {
            JobApplication app = applications.findById(interview.applicationId()).filter(a -> a.belongsTo(actor.userId())).orElseThrow(InterviewService::notFound);
            return new Application(app, vacancies.findById(app.vacancyId()).orElseThrow(InterviewService::notFound));
        }
        return companyApplication(actor, interview.applicationId(), false);
    }

    private Application companyApplication(Actor actor, UUID applicationId, boolean scheduling) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        JobApplication app = applications.findById(applicationId).orElseThrow(InterviewService::notFound);
        Vacancy vacancy = vacancies.findById(app.vacancyId()).filter(v -> v.companyId().equals(companyId)).orElseThrow(InterviewService::notFound);
        if (!profiles.findConsents(app.candidateId()).map(CandidateConsents::shareWithRecruiters).orElse(false)) {
            throw new DomainException("access.cv-not-shared", "La persona retiró el permiso de compartir sus datos");
        }
        ApplicationStage stage = progress.stage(applicationId);
        if (scheduling && (stage == ApplicationStage.HIRED || stage == ApplicationStage.REJECTED)) {
            throw new DomainException("interview.invalid", "No puedes agendar en un proceso finalizado");
        }
        return new Application(app, vacancy);
    }

    private static void requireVersion(Interview interview, int version) { if (interview.version() != version) throw conflict(); }
    private static DomainException conflict() { return new DomainException("interview.conflict", "La entrevista cambió o la solicitud ya se usó. Actualiza antes de continuar"); }
    private static DomainException notFound() { return new DomainException("interview.not-found", "La entrevista o postulación no existe"); }
}
