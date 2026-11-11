package com.plataformaempleo.api.vacancy.infrastructure.persistence;

import com.plataformaempleo.api.vacancy.domain.model.Criterion;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.api.vacancy.infrastructure.persistence.VacancyJpaEntity.CriterionJson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA del puerto {@link VacancyRepository}. Los criterios viajan como JSONB. */
@Component
class VacancyPersistenceAdapter implements VacancyRepository {

    private final SpringDataVacancyRepository jpa;

    VacancyPersistenceAdapter(SpringDataVacancyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Vacancy save(Vacancy v) {
        return toDomain(jpa.saveAndFlush(toEntity(v)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vacancy> findById(UUID id) {
        return jpa.findById(id).map(VacancyPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vacancy> findByCompany(UUID companyId) {
        return jpa.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(VacancyPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vacancy> findPublished(int limit) {
        return jpa.findByStatusOrderByPublishedAtDesc(Vacancy.Status.PUBLISHED.name(), PageRequest.of(0, limit))
                .stream().map(VacancyPersistenceAdapter::toDomain).toList();
    }

    private static VacancyJpaEntity toEntity(Vacancy v) {
        Vacancy.Content c = v.content();
        List<CriterionJson> criteria = c.criteria().stream()
                .map(k -> new CriterionJson(k.key(), k.label(), k.question(), k.kind().name(), k.options()))
                .toList();
        return new VacancyJpaEntity(v.id(), v.companyId(), v.createdBy(), c.title(), c.description(), c.location(),
                c.modality().name(), v.status().name(), criteria, v.createdAt(), v.updatedAt(), v.publishedAt(),
                v.closedAt());
    }

    private static Vacancy toDomain(VacancyJpaEntity e) {
        List<Criterion> criteria = e.getCriteria().stream()
                .map(k -> new Criterion(k.key(), k.label(), k.question(), Criterion.Kind.valueOf(k.kind()), k.options()))
                .toList();
        var content = new Vacancy.Content(e.getTitle(), e.getDescription(), e.getLocation(),
                Vacancy.Modality.valueOf(e.getModality()), criteria);
        return new Vacancy(e.getId(), e.getCompanyId(), e.getCreatedBy(), content, Vacancy.Status.valueOf(e.getStatus()),
                e.getCreatedAt(), e.getUpdatedAt(), e.getPublishedAt(), e.getClosedAt());
    }
}
