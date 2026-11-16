package com.plataformaempleo.api.screening.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.jobapplication.domain.port.out.JobApplicationRepository;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.screening.domain.port.out.ScreeningRequestRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.shared.application.AuditRecorder;
import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.shared.domain.port.out.EventOutbox;
import com.plataformaempleo.api.shared.domain.port.out.IdempotencyStore;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import com.plataformaempleo.common.events.EventTypes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * La empresa pide preseleccionar los CV de una vacante (sección 5 del diseño, pasos 2 y 3).
 * En UNA transacción: crea las solicitudes PENDING, guarda un evento {@code screening.requested}
 * por cada una en el outbox y registra la Idempotency-Key. El envío a RabbitMQ ocurre después.
 */
public class ScreeningService {

    static final String OPERATION = "screening.request";
    static final int MAX_KEY_LENGTH = 100;

    private final ScreeningRequestRepository requests;
    private final JobApplicationRepository applications;
    private final VacancyRepository vacancies;
    private final CandidateProfileRepository profiles;
    private final EventOutbox outbox;
    private final IdempotencyStore idempotency;
    private final String provider;
    private final Clock clock;
    private final AuditRecorder audit;

    public ScreeningService(ScreeningRequestRepository requests, JobApplicationRepository applications,
                            VacancyRepository vacancies, CandidateProfileRepository profiles, EventOutbox outbox,
                            IdempotencyStore idempotency, String provider, Clock clock, AuditRecorder audit) {
        this.requests = Objects.requireNonNull(requests);
        this.applications = Objects.requireNonNull(applications);
        this.vacancies = Objects.requireNonNull(vacancies);
        this.profiles = Objects.requireNonNull(profiles);
        this.outbox = Objects.requireNonNull(outbox);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.provider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * @param applicationIds postulaciones a preseleccionar; vacío = todas las de la vacante
     * @param idempotencyKey obligatorio: repetir la petición con la misma clave no repite el trabajo
     */
    @Transactional
    public Result request(Actor actor, UUID vacancyId, Collection<UUID> applicationIds, String idempotencyKey) {
        Vacancy vacancy = ownVacancy(actor, vacancyId);
        String key = validKey(idempotencyKey);
        List<UUID> selection = applicationIds == null ? List.of() : applicationIds.stream().distinct().sorted().toList();
        String fingerprint = fingerprint(vacancy.id(), selection);

        var previous = idempotency.find(actor.userId(), key);
        if (previous.isPresent()) {
            var entry = previous.get();
            if (!entry.operation().equals(OPERATION) || !entry.fingerprint().equals(fingerprint)) {
                throw new DomainException("idempotency.key.reused",
                        "Esa clave de idempotencia ya se usó con otra petición. Usa una clave nueva");
            }
            return new Result(requests.findByIds(entry.resultIds()), entry.skipped(), true);
        }
        if (vacancy.status() == Vacancy.Status.DRAFT) {
            throw new DomainException("screening.vacancy.draft", "Publica la vacante antes de pedir la preselección");
        }

        List<JobApplication> received = applications.findByVacancy(vacancy.id());
        List<JobApplication> chosen = selection.isEmpty() ? received : pick(received, selection);
        Set<UUID> alreadyRequested = requests.findByVacancy(vacancy.id()).stream()
                .filter(r -> r.provider().equals(provider))
                .map(ScreeningRequest::applicationId)
                .collect(Collectors.toSet());

        var now = clock.instant();
        List<ScreeningRequest> created = new ArrayList<>();
        for (JobApplication application : chosen) {
            // Solo se procesa el CV de quien mantiene el permiso de compartirlo con la empresa.
            if (alreadyRequested.contains(application.id()) || !sharesWithRecruiters(application.candidateId())) {
                continue;
            }
            created.add(ScreeningRequest.pending(vacancy.id(), application.id(), application.cvId(), provider,
                    actor.userId(), now));
        }
        requests.saveAll(created);
        for (ScreeningRequest r : created) {
            outbox.append("screening_request", r.id(), EventTypes.SCREENING_REQUESTED, payload(r));
        }
        int skipped = chosen.size() - created.size();
        idempotency.save(actor.userId(), key,
                new IdempotencyStore.Entry(OPERATION, fingerprint, created.stream().map(ScreeningRequest::id).toList(),
                        skipped));
        if (!created.isEmpty()) {
            audit.record(actor, AuditAction.SCREENING_REQUESTED, vacancy.id(), vacancy.content().title());
        }
        return new Result(created, skipped, false);
    }

    @Transactional(readOnly = true)
    public List<ScreeningRequest> forVacancy(Actor actor, UUID vacancyId) {
        return requests.findByVacancy(ownVacancy(actor, vacancyId).id());
    }

    /**
     * @param created  solicitudes creadas (o las de la petición original, si se repitió)
     * @param skipped  postulaciones no incluidas: ya tenían solicitud o la persona no comparte su CV
     * @param replayed true si la respuesta corresponde a una petición anterior con la misma clave
     */
    public record Result(List<ScreeningRequest> created, int skipped, boolean replayed) {
    }

    // ---- reglas ----

    private Vacancy ownVacancy(Actor actor, UUID vacancyId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        return vacancies.findById(vacancyId)
                .filter(v -> v.companyId().equals(companyId))
                .orElseThrow(() -> new DomainException("vacancy.not-found", "La vacante no existe"));
    }

    private static List<JobApplication> pick(List<JobApplication> received, List<UUID> selection) {
        Map<UUID, JobApplication> byId = received.stream()
                .collect(Collectors.toMap(JobApplication::id, a -> a, (a, b) -> a, LinkedHashMap::new));
        List<JobApplication> chosen = new ArrayList<>();
        for (UUID id : selection) {
            JobApplication a = byId.get(id);
            if (a == null) {
                throw new DomainException("application.not-found", "Una de las postulaciones no existe en esta vacante");
            }
            chosen.add(a);
        }
        return chosen;
    }

    private boolean sharesWithRecruiters(UUID candidateId) {
        return profiles.findConsents(candidateId).map(CandidateConsents::shareWithRecruiters).orElse(false);
    }

    private static String validKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new DomainException("idempotency.key.required",
                    "Falta el encabezado Idempotency-Key (hasta " + MAX_KEY_LENGTH + " caracteres)");
        }
        return key.strip();
    }

    private static String fingerprint(UUID vacancyId, List<UUID> selection) {
        String raw = OPERATION + "|" + vacancyId + "|"
                + (selection.isEmpty() ? "*" : selection.stream().map(UUID::toString).collect(Collectors.joining(",")));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }

    /** Lo mínimo que necesita el trabajador; el texto del CV nunca viaja en el mensaje. */
    private static Map<String, Object> payload(ScreeningRequest r) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("screeningRequestId", r.id().toString());
        payload.put("vacancyId", r.vacancyId().toString());
        payload.put("applicationId", r.applicationId().toString());
        payload.put("cvId", r.cvId().toString());
        payload.put("provider", r.provider());
        return payload;
    }
}
