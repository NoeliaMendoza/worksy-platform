package com.plataformaempleo.api.conversation.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.conversation.domain.model.ConversationSummary;
import com.plataformaempleo.api.conversation.domain.model.Message;
import com.plataformaempleo.api.conversation.domain.port.out.MessageRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.User;
import com.plataformaempleo.api.identity.domain.port.out.UserRepository;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import com.plataformaempleo.common.events.EventTypes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversaciones entre la empresa y la persona candidata, una por postulación.
 * <ul>
 *   <li>La empresa escribe primero; la persona candidata solo responde.</li>
 *   <li>La empresa solo escribe mientras la persona mantenga el permiso de compartir sus datos.</li>
 *   <li>Cada mensaje deja un evento {@code message.sent} en el outbox con ids (nunca el texto), para que el
 *       servicio de tiempo real avise a los destinatarios conectados.</li>
 * </ul>
 */
public class ConversationService {

    /** VALOR SUPUESTO: cuántos mensajes se devuelven por página. */
    public static final int PAGE_SIZE = 50;

    private final MessageRepository messages;
    private final JobApplicationRepository applications;
    private final VacancyRepository vacancies;
    private final CandidateProfileRepository profiles;
    private final UserRepository users;
    private final EventOutbox outbox;
    private final Clock clock;

    public ConversationService(MessageRepository messages, JobApplicationRepository applications,
                               VacancyRepository vacancies, CandidateProfileRepository profiles,
                               UserRepository users, EventOutbox outbox, Clock clock) {
        this.messages = Objects.requireNonNull(messages);
        this.applications = Objects.requireNonNull(applications);
        this.vacancies = Objects.requireNonNull(vacancies);
        this.profiles = Objects.requireNonNull(profiles);
        this.users = Objects.requireNonNull(users);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Bandeja de mensajes de quien consulta, de la conversación con actividad más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<ConversationSummary> inbox(Actor actor) {
        if (actor.role() == Role.CANDIDATE) {
            return messages.summariesForCandidate(actor.userId());
        }
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return messages.summariesForCompany(companyId, actor.userId());
    }

    /**
     * Una página de la conversación, en orden cronológico, y si quien consulta puede escribir ahora.
     *
     * @param before si no es null, los mensajes anteriores a ese momento
     */
    @Transactional(readOnly = true)
    public ConversationPage thread(Actor actor, UUID applicationId, Instant before) {
        Participant me = participant(actor, applicationId);
        List<Message> page = messages.findByApplication(applicationId, before, PAGE_SIZE + 1);
        boolean hasMore = page.size() > PAGE_SIZE;
        List<Message> chronological = new ArrayList<>(page.subList(0, Math.min(page.size(), PAGE_SIZE)));
        Collections.reverse(chronological);
        return new ConversationPage(chronological, hasMore, canWrite(me));
    }

    @Transactional
    public Message send(Actor actor, UUID applicationId, String text) {
        Participant me = participant(actor, applicationId);
        if (me.candidate() && !messages.hasCompanyMessage(applicationId)) {
            throw new DomainException("message.company-first",
                    "Podrás responder cuando la empresa te escriba por esta postulación");
        }
        if (!me.candidate() && !sharesWithRecruiters(me.application().candidateId())) {
            throw new DomainException("access.cv-not-shared",
                    "La persona retiró el permiso de compartir sus datos; no se le pueden enviar mensajes");
        }
        Message message = messages.save(
                Message.write(applicationId, actor.userId(), me.candidate(), text, clock.instant()));
        messages.markRead(applicationId, actor.userId(), message.sentAt());
        outbox.append("application", applicationId, EventTypes.MESSAGE_SENT, Map.of(
                "messageId", message.id().toString(),
                "applicationId", applicationId.toString(),
                "recipientIds", recipients(me, actor.userId()).stream().map(UUID::toString).toList()));
        return message;
    }

    @Transactional
    public void markRead(Actor actor, UUID applicationId) {
        participant(actor, applicationId);
        messages.markRead(applicationId, actor.userId(), clock.instant());
    }

    /** Página de mensajes en orden cronológico. */
    public record ConversationPage(List<Message> messages, boolean hasMore, boolean canWrite) {
    }

    // ---- reglas ----

    private record Participant(JobApplication application, UUID companyId, boolean candidate) {
    }

    /**
     * Solo participan la persona que se postuló y el equipo de la empresa dueña de la vacante.
     * A cualquier otra persona la postulación le "no existe", para no revelar que existe.
     */
    private Participant participant(Actor actor, UUID applicationId) {
        JobApplication application = applications.findById(applicationId).orElseThrow(ConversationService::notFound);
        UUID companyId = vacancies.findById(application.vacancyId()).orElseThrow(ConversationService::notFound)
                .companyId();
        if (actor.role() == Role.CANDIDATE && application.belongsTo(actor.userId())) {
            return new Participant(application, companyId, true);
        }
        if ((actor.role() == Role.COMPANY_ADMIN || actor.role() == Role.RECRUITER)
                && companyId.equals(actor.companyId())) {
            return new Participant(application, companyId, false);
        }
        throw notFound();
    }

    private boolean canWrite(Participant me) {
        return me.candidate()
                ? messages.hasCompanyMessage(me.application().id())
                : sharesWithRecruiters(me.application().candidateId());
    }

    /** Todos los del otro lado y el resto del equipo de la empresa, menos quien escribe. */
    private Set<UUID> recipients(Participant me, UUID senderId) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(me.application().candidateId());
        users.findActiveByCompany(me.companyId()).stream().map(User::id).forEach(ids::add);
        ids.remove(senderId);
        return ids;
    }

    private boolean sharesWithRecruiters(UUID candidateId) {
        return profiles.findConsents(candidateId).map(CandidateConsents::shareWithRecruiters).orElse(false);
    }

    private static DomainException notFound() {
        return new DomainException("application.not-found", "La postulación no existe");
    }
}
