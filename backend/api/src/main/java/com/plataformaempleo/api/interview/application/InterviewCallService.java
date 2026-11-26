package com.plataformaempleo.api.interview.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.interview.domain.model.CallRoom;
import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.CallRepository;
import com.plataformaempleo.api.interview.domain.port.out.CallTokenIssuer;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.ApplicationProgressRepository;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrar, salir y terminar la sala de videollamada de una entrevista. El acceso lo decide siempre el backend:
 * la persona candidata de esa postulación o alguien de la empresa a cargo de la cita. Conocer la URL no basta.
 */
public class InterviewCallService {
    private final InterviewRepository interviews;
    private final CallRepository calls;
    private final CallTokenIssuer tokens;
    private final JobApplicationRepository applications;
    private final VacancyRepository vacancies;
    private final ApplicationProgressRepository progress;
    private final UserRepository users;
    private final List<String> iceServers;
    private final Clock clock;

    public InterviewCallService(InterviewRepository interviews, CallRepository calls, CallTokenIssuer tokens,
                                JobApplicationRepository applications, VacancyRepository vacancies,
                                ApplicationProgressRepository progress, UserRepository users, List<String> iceServers, Clock clock) {
        this.interviews = interviews; this.calls = calls; this.tokens = tokens; this.applications = applications;
        this.vacancies = vacancies; this.progress = progress; this.users = users; this.iceServers = List.copyOf(iceServers); this.clock = clock;
    }

    public record Peer(UUID userId, CallRoom.Role role, String label, boolean present) { }
    public record Join(UUID sessionId, UUID interviewId, CallRoom.Role role, String roomToken, Instant expiresAt,
                       List<String> iceServers, List<Peer> peers) {
        @Override public String toString() { return "CallJoin[" + sessionId + "]"; }
    }
    public record Status(boolean joinable, String unavailableReason, Instant opensAt, Instant closesAt, List<Peer> present) { }

    private record Access(Interview interview, CallRoom.Role role, UUID candidateId) { }

    @Transactional(readOnly = true)
    public Status status(Actor actor, UUID interviewId) {
        Access access = access(actor, interviewId);
        Instant now = clock.instant();
        String reason = null;
        try { requireJoinable(access, now); } catch (DomainException e) { reason = e.getMessage(); }
        List<Peer> present = calls.findOpen(interviewId).map(s -> peers(access, s.id(), actor.userId()))
                .orElse(List.of()).stream().filter(Peer::present).toList();
        return new Status(reason == null, reason, CallRoom.opensAt(access.interview()), CallRoom.closesAt(access.interview()), present);
    }

    @Transactional
    public Join join(Actor actor, UUID interviewId) {
        Access access = access(actor, interviewId);
        Instant now = clock.instant();
        requireJoinable(access, now);
        CallRoom.Session session = calls.openSession(interviewId, now);
        calls.join(session.id(), actor.userId(), access.role(), now);
        List<Peer> peers = peers(access, session.id(), actor.userId());
        Instant expires = CallRoom.closesAt(access.interview());
        String token = tokens.issue(new CallRoom.Grant(actor.userId(), access.role(), session.id(), interviewId,
                peers.stream().map(Peer::userId).toList(), expires));
        return new Join(session.id(), interviewId, access.role(), token, expires, iceServers, peers);
    }

    @Transactional
    public void leave(Actor actor, UUID interviewId) {
        access(actor, interviewId);
        calls.findOpen(interviewId).ifPresent(s -> calls.leave(s.id(), actor.userId(), clock.instant()));
    }

    /** La persona entrevistadora cierra la sala para todos. Entrar después abre una sala nueva si sigue en horario. */
    @Transactional
    public void end(Actor actor, UUID interviewId) {
        Access access = access(actor, interviewId);
        if (access.role() != CallRoom.Role.INTERVIEWER) throw Actor.forbidden();
        Instant now = clock.instant();
        calls.findOpen(interviewId).ifPresent(s -> {
            calls.leave(s.id(), actor.userId(), now);
            calls.close(s.id(), actor.userId(), now);
        });
    }

    private void requireJoinable(Access access, Instant now) {
        CallRoom.requireJoinable(access.interview(), now);
        ApplicationStage stage = progress.stage(access.interview().applicationId());
        if (stage == ApplicationStage.HIRED || stage == ApplicationStage.REJECTED) {
            throw new DomainException("interview.closed", "El proceso de selección terminó; la sala ya no está disponible");
        }
    }

    /** Las otras personas autorizadas en la sala, con un nombre visible y si están dentro ahora. */
    private List<Peer> peers(Access access, UUID sessionId, UUID self) {
        var present = calls.participants(sessionId);
        List<Peer> peers = new ArrayList<>();
        if (!self.equals(access.candidateId())) peers.add(peer(access.candidateId(), CallRoom.Role.CANDIDATE, present));
        for (UUID interviewer : calls.interviewers(access.interview().id())) {
            if (!interviewer.equals(self)) peers.add(peer(interviewer, CallRoom.Role.INTERVIEWER, present));
        }
        return peers;
    }

    private Peer peer(UUID userId, CallRoom.Role role, List<CallRoom.Participant> present) {
        String label = users.findById(userId).map(u -> u.email()).orElse("Participante");
        boolean inside = present.stream().anyMatch(p -> p.userId().equals(userId) && p.present());
        return new Peer(userId, role, label, inside);
    }

    private Access access(Actor actor, UUID interviewId) {
        Interview interview = interviews.find(interviewId).orElseThrow(InterviewCallService::notFound);
        JobApplication application = applications.findById(interview.applicationId()).orElseThrow(InterviewCallService::notFound);
        if (actor.role() == Role.CANDIDATE) {
            if (!application.belongsTo(actor.userId())) throw notFound();
            return new Access(interview, CallRoom.Role.CANDIDATE, application.candidateId());
        }
        UUID company = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        boolean ownCompany = vacancies.findById(application.vacancyId()).filter(v -> v.companyId().equals(company)).isPresent();
        if (!ownCompany) throw notFound();
        if (!calls.interviewers(interviewId).contains(actor.userId())) {
            throw new DomainException("access.not-interviewer", "Solo las personas a cargo de esta entrevista pueden entrar a la sala");
        }
        return new Access(interview, CallRoom.Role.INTERVIEWER, application.candidateId());
    }

    private static DomainException notFound() { return new DomainException("interview.not-found", "La entrevista o postulación no existe"); }
}
