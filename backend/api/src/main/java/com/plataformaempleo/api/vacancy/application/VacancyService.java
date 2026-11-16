package com.plataformaempleo.api.vacancy.application;

import com.plataformaempleo.api.company.domain.model.Company;
import com.plataformaempleo.api.company.domain.port.out.CompanyRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.shared.application.AuditRecorder;
import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vacantes. Del lado de la empresa, cada operación se limita a la empresa de quien la hace:
 * una vacante de otra empresa responde "no existe" (nunca revela que existe).
 * Del lado público, solo se ven las publicadas.
 */
public class VacancyService {

    /** VALOR SUPUESTO: máximo de vacantes en la lista pública (sin paginar todavía). */
    public static final int PUBLIC_LIMIT = 100;

    private final VacancyRepository vacancies;
    private final CompanyRepository companies;
    private final Clock clock;
    private final AuditRecorder audit;

    public VacancyService(VacancyRepository vacancies, CompanyRepository companies, Clock clock, AuditRecorder audit) {
        this.vacancies = Objects.requireNonNull(vacancies);
        this.companies = Objects.requireNonNull(companies);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
    }

    // ---- Empresa ----

    @Transactional
    public Vacancy create(Actor actor, Vacancy.Content content) {
        UUID companyId = companyOf(actor);
        Vacancy saved = vacancies.save(Vacancy.draft(companyId, actor.userId(), content, clock.instant()));
        audit.record(actor, AuditAction.VACANCY_CREATED, saved.id(), saved.content().title());
        return saved;
    }

    @Transactional
    public Vacancy update(Actor actor, UUID vacancyId, Vacancy.Content content) {
        Vacancy saved = vacancies.save(ownVacancy(actor, vacancyId).edit(content, clock.instant()));
        audit.record(actor, AuditAction.VACANCY_UPDATED, saved.id(), saved.content().title());
        return saved;
    }

    @Transactional
    public Vacancy publish(Actor actor, UUID vacancyId) {
        Vacancy saved = vacancies.save(ownVacancy(actor, vacancyId).publish(clock.instant()));
        audit.record(actor, AuditAction.VACANCY_PUBLISHED, saved.id(), saved.content().title());
        return saved;
    }

    @Transactional
    public Vacancy close(Actor actor, UUID vacancyId) {
        Vacancy saved = vacancies.save(ownVacancy(actor, vacancyId).close(clock.instant()));
        audit.record(actor, AuditAction.VACANCY_CLOSED, saved.id(), saved.content().title());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Vacancy> listForCompany(Actor actor) {
        return vacancies.findByCompany(companyOf(actor));
    }

    @Transactional(readOnly = true)
    public Vacancy getForCompany(Actor actor, UUID vacancyId) {
        return ownVacancy(actor, vacancyId);
    }

    // ---- Público ----

    @Transactional(readOnly = true)
    public List<PublishedVacancy> listPublished() {
        Map<UUID, String> names = new HashMap<>();
        Function<UUID, String> nameOf = id -> names.computeIfAbsent(id, this::companyName);
        return vacancies.findPublished(PUBLIC_LIMIT).stream()
                .map(v -> new PublishedVacancy(v, nameOf.apply(v.companyId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublishedVacancy getPublished(UUID vacancyId) {
        Vacancy v = vacancies.findById(vacancyId)
                .filter(found -> found.status() == Vacancy.Status.PUBLISHED)
                .orElseThrow(VacancyService::notFound);
        return new PublishedVacancy(v, companyName(v.companyId()));
    }

    /** Vacante publicada junto con el nombre de su empresa (lo único de la empresa que se muestra). */
    public record PublishedVacancy(Vacancy vacancy, String companyName) {
    }

    // ---- reglas ----

    private static UUID companyOf(Actor actor) {
        return actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
    }

    private Vacancy ownVacancy(Actor actor, UUID vacancyId) {
        UUID companyId = companyOf(actor);
        return vacancies.findById(vacancyId)
                .filter(v -> v.companyId().equals(companyId))
                .orElseThrow(VacancyService::notFound);
    }

    private String companyName(UUID companyId) {
        return companies.findById(companyId).map(Company::name).orElse("");
    }

    private static DomainException notFound() {
        return new DomainException("vacancy.not-found", "La vacante no existe");
    }
}
