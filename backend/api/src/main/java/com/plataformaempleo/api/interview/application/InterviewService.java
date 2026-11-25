package com.plataformaempleo.api.interview.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.interview.domain.model.ChangeRequest;
import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository.Event;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository.Viewer;
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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de las entrevistas. Cada cambio sube la versión de la cita (control optimista: el cliente envía la versión
 * que vio y recibe 409 si cambió), deja un evento en el historial con su autor y motivo, y avisa por el outbox solo con
 * ids. Las respuestas repetidas no generan cambios ni avisos.
 */
public class InterviewService {
    public static final int PAGE_SIZE = 20;
    /** VALOR SUPUESTO: el calendario pide como mucho 6 semanas (vista de mes completa). */
    private static final Duration MAX_RANGE = Duration.ofDays(42);

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
    public record Summary(InterviewRepository.Counts counts, InterviewRepository.Item next) { }
    private record Application(JobApplication application, Vacancy vacancy) { }

    @Transactional(readOnly = true)
    public Context context(Actor actor, UUID applicationId) {
        Application app = companyApplication(actor, applicationId, true);
        return new Context(applicationId, app.vacancy().content().title(), users.findById(app.application().candidateId()).orElseThrow().email());
    }

    // ---- consultas ----

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(Actor actor, Interview.View view, int page, Instant from, Instant to, boolean mineOnly) {
        if (page < 0 || page > 10000) throw invalid("Página inválida");
        Viewer viewer = viewer(actor);
        Instant now = clock.instant();
        if (view == Interview.View.range) {
            if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
                throw invalid("Indica un rango de fechas de hasta 6 semanas");
            }
            // Un rango trae todas las citas del periodo (un calendario no se pagina).
            var rows = interviews.list(viewer, new InterviewRepository.Query(view, now, from, to, mineOnly, 0, 501));
            if (rows.size() > 500) throw invalid("Hay demasiadas entrevistas en ese rango; elige uno más corto");
            return new Page(rows, false);
        }
        var rows = interviews.list(viewer, new InterviewRepository.Query(view, now, null, null, mineOnly, page * PAGE_SIZE, PAGE_SIZE + 1));
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), PAGE_SIZE))), rows.size() > PAGE_SIZE);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(Actor actor, boolean mineOnly) {
        Viewer viewer = viewer(actor);
        Instant now = clock.instant();
        var next = interviews.list(viewer, new InterviewRepository.Query(Interview.View.upcoming, now, null, null, mineOnly, 0, 1));
        return new Summary(interviews.counts(viewer, mineOnly, now), next.isEmpty() ? null : next.getFirst());
    }

    @Transactional(readOnly = true)
    public InterviewRepository.Item get(Actor actor, UUID id) {
        return interviews.item(id, viewer(actor), clock.instant()).orElseThrow(InterviewService::notFound);
    }

    @Transactional(readOnly = true)
    public long unread(Actor actor) {
        UUID company = actor.role() == Role.CANDIDATE ? null : actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return interviews.unread(actor.userId(), company);
    }

    /** Citas vigentes a cargo de quien agenda que se cruzan con un horario (aviso, no bloqueo). */
    @Transactional(readOnly = true)
    public List<InterviewRepository.Overlap> overlaps(Actor actor, Instant startsAt, int duration, UUID exclude) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        if (startsAt == null || duration < 15 || duration > 180) throw invalid("Indica la hora y una duración de 15 a 180 minutos");
        return interviews.overlapsParticipant(actor.userId(), startsAt, startsAt.plus(Duration.ofMinutes(duration)), exclude);
    }

    @Transactional(readOnly = true)
    public List<Event> history(Actor actor, UUID id) {
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        participant(actor, current);
        return interviews.history(id);
    }

    @Transactional
    public void markRead(Actor actor, UUID id, int version) {
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        participant(actor, current);
        if (version < 0 || version > current.version()) throw invalid("La versión indicada no existe");
        interviews.markRead(id, actor.userId(), version);
    }

    // ---- empresa ----

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
        requireNoApplicationOverlap(proposed);
        interviews.addParticipant(proposed.id(), actor.userId(), "ORGANIZER");
        changed(actor, app, proposed, Interview.Change.SCHEDULED, Event.Actor.COMPANY, null, null);
        return proposed;
    }

    @Transactional
    public Interview update(Actor actor, UUID id, int expectedVersion, String title, Instant startsAt,
                            int duration, String zone, Interview.Mode mode, String place, String note, String reason) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), true);
        requireVersion(current, expectedVersion);
        Interview next = current.update(title, startsAt, duration, zone, mode, place, note, clock.instant());
        if (next == current) return current;
        boolean material = current.materialChange(next);
        if (material) {
            requireNoApplicationOverlap(next);
            supersedeOpenRequest(id, actor);
        }
        saveChange(actor, app, next, expectedVersion, Interview.Change.UPDATED, Event.Actor.COMPANY, cleanReason(reason),
                !current.place().equals(next.place()) || current.mode() != next.mode());
        return next;
    }

    @Transactional
    public Interview cancel(Actor actor, UUID id, int expectedVersion, String reason) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), false);
        requireVersion(current, expectedVersion);
        Interview next = current.cancel(reason, clock.instant());
        supersedeOpenRequest(id, actor);
        saveChange(actor, app, next, expectedVersion, Interview.Change.CANCELLED, Event.Actor.COMPANY, next.cancelReason(), null);
        return next;
    }

    @Transactional
    public Interview acceptChange(Actor actor, UUID id, int expectedVersion, UUID requestId, int slot) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), true);
        requireVersion(current, expectedVersion);
        ChangeRequest request = openRequest(id, requestId);
        Interview next = current.acceptProposedStart(request.slot(slot), clock.instant());
        requireNoApplicationOverlap(next);
        if (!interviews.resolveChangeRequest(request.id(), ChangeRequest.Status.ACCEPTED, actor.userId(), clock.instant())) throw conflict();
        saveChange(actor, app, next, expectedVersion, Interview.Change.CHANGE_ACCEPTED, Event.Actor.COMPANY, null, false);
        return next;
    }

    @Transactional
    public Interview rejectChange(Actor actor, UUID id, int expectedVersion, UUID requestId, String reason) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), false);
        requireVersion(current, expectedVersion);
        ChangeRequest request = openRequest(id, requestId);
        if (!interviews.resolveChangeRequest(request.id(), ChangeRequest.Status.REJECTED, actor.userId(), clock.instant())) throw conflict();
        saveChange(actor, app, current.touch(clock.instant()), expectedVersion, Interview.Change.CHANGE_REJECTED, Event.Actor.COMPANY,
                cleanReason(reason), null);
        return interviews.find(id).orElseThrow();
    }

    @Transactional
    public Interview recordOutcome(Actor actor, UUID id, int expectedVersion, Interview.Status outcome) {
        actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = companyApplication(actor, current.applicationId(), false);
        if (current.status() == outcome) return current;
        requireVersion(current, expectedVersion);
        Interview next = current.recordOutcome(outcome, clock.instant());
        saveChange(actor, app, next, expectedVersion,
                outcome == Interview.Status.COMPLETED ? Interview.Change.COMPLETED : Interview.Change.NO_SHOW, Event.Actor.COMPANY, null, null);
        return next;
    }

    // ---- candidato ----

    @Transactional
    public Interview respond(Actor actor, UUID id, int expectedVersion, Interview.Status response, String reason) {
        actor.requireRole(Role.CANDIDATE);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = participant(actor, current);
        // Repetir la misma respuesta (doble clic, reintento, otro dispositivo) no es un error ni genera avisos.
        if (current.status() == response) return current;
        requireOpenProcess(app);
        requireVersion(current, expectedVersion);
        Interview next = current.respond(response, clock.instant());
        if (response == Interview.Status.DECLINED) supersedeOpenRequest(id, actor);
        saveChange(actor, app, next, expectedVersion,
                response == Interview.Status.CONFIRMED ? Interview.Change.CONFIRMED : Interview.Change.DECLINED, Event.Actor.CANDIDATE,
                response == Interview.Status.DECLINED ? cleanReason(reason) : null, null);
        return next;
    }

    @Transactional
    public Interview requestChange(Actor actor, UUID id, int expectedVersion, UUID requestId, String reason, List<Instant> slots) {
        actor.requireRole(Role.CANDIDATE);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = participant(actor, current);
        requireOpenProcess(app);
        if (requestId == null) throw invalid("Falta el identificador de la solicitud");
        var open = interviews.openChangeRequest(id);
        if (open.isPresent()) {
            // Mismo envío repetido: devuelve la cita tal como está. Otra propuesta distinta: debe retirarse antes.
            if (open.get().id().equals(requestId)) return current;
            throw new DomainException("interview.conflict", "Ya enviaste una propuesta de otra hora. Retírala antes de enviar otra");
        }
        requireVersion(current, expectedVersion);
        ChangeRequest request = ChangeRequest.open(requestId, current, actor.userId(), reason, slots, clock.instant());
        if (!interviews.insertChangeRequest(request)) throw conflict();
        saveChange(actor, app, current.touch(clock.instant()), expectedVersion, Interview.Change.CHANGE_REQUESTED, Event.Actor.CANDIDATE,
                request.reason(), null);
        return interviews.find(id).orElseThrow();
    }

    @Transactional
    public Interview withdrawChange(Actor actor, UUID id, int expectedVersion, UUID requestId) {
        actor.requireRole(Role.CANDIDATE);
        Interview current = interviews.find(id).orElseThrow(InterviewService::notFound);
        Application app = participant(actor, current);
        var open = interviews.openChangeRequest(id);
        if (open.isEmpty() || !open.get().id().equals(requestId)) return current; // ya no estaba abierta
        requireVersion(current, expectedVersion);
        if (!interviews.resolveChangeRequest(requestId, ChangeRequest.Status.WITHDRAWN, actor.userId(), clock.instant())) throw conflict();
        saveChange(actor, app, current.touch(clock.instant()), expectedVersion, Interview.Change.CHANGE_WITHDRAWN, Event.Actor.CANDIDATE, null, null);
        return interviews.find(id).orElseThrow();
    }

    // ---- apoyo ----

    private void saveChange(Actor actor, Application app, Interview next, int expected, Interview.Change change,
                            Event.Actor side, String reason, Boolean placeChanged) {
        if (!interviews.update(next, expected)) throw conflict();
        changed(actor, app, next, change, side, reason, placeChanged);
    }

    private void changed(Actor actor, Application app, Interview interview, Interview.Change change,
                         Event.Actor side, String reason, Boolean placeChanged) {
        interviews.record(interview, change, actor.userId(), side, reason, placeChanged);
        interviews.markRead(interview.id(), actor.userId(), interview.version());
        var recipients = new LinkedHashSet<UUID>();
        recipients.add(app.application().candidateId());
        users.findActiveByCompany(app.vacancy().companyId()).forEach(user -> recipients.add(user.id()));
        recipients.remove(actor.userId());
        outbox.append("interview", interview.id(), EventTypes.INTERVIEW_CHANGED, Map.of(
                "interviewId", interview.id().toString(), "applicationId", app.application().id().toString(),
                "recipientIds", recipients.stream().map(UUID::toString).toList()));
    }

    private void supersedeOpenRequest(UUID interviewId, Actor actor) {
        interviews.openChangeRequest(interviewId).ifPresent(r ->
                interviews.resolveChangeRequest(r.id(), ChangeRequest.Status.SUPERSEDED, actor.userId(), clock.instant()));
    }

    private ChangeRequest openRequest(UUID interviewId, UUID requestId) {
        return interviews.openChangeRequest(interviewId).filter(r -> r.id().equals(requestId))
                .orElseThrow(() -> new DomainException("interview.conflict", "La propuesta ya no está abierta. Actualiza la entrevista"));
    }

    /** Dos citas vigentes de la misma postulación no pueden cruzarse. */
    private void requireNoApplicationOverlap(Interview interview) {
        if (interviews.overlapsApplication(interview.applicationId(), interview.startsAt(), interview.endsAt(), interview.id())) {
            throw new DomainException("interview.overlap", "Esta persona ya tiene otra entrevista de este proceso en ese horario");
        }
    }

    private void requireOpenProcess(Application app) {
        ApplicationStage stage = progress.stage(app.application().id());
        if (stage == ApplicationStage.HIRED || stage == ApplicationStage.REJECTED) {
            throw new DomainException("interview.closed", "El proceso de selección terminó; esta entrevista ya no admite cambios");
        }
    }

    private Viewer viewer(Actor actor) {
        UUID company = actor.role() == Role.CANDIDATE ? null : actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return new Viewer(actor.userId(), company);
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
            throw invalid("No puedes agendar en un proceso finalizado");
        }
        return new Application(app, vacancy);
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String cleaned = reason.strip();
        if (cleaned.length() > 300) throw invalid("El motivo admite hasta 300 caracteres");
        return cleaned;
    }

    private static void requireVersion(Interview interview, int version) { if (interview.version() != version) throw conflict(); }
    private static DomainException invalid(String detail) { return new DomainException("interview.invalid", detail); }
    private static DomainException conflict() { return new DomainException("interview.conflict", "La entrevista cambió o la solicitud ya se usó. Actualiza antes de continuar"); }
    private static DomainException notFound() { return new DomainException("interview.not-found", "La entrevista o postulación no existe"); }
}
