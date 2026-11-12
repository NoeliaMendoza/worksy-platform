package com.plataformaempleo.api.screening.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.screening.domain.model.RankingResult;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.screening.domain.port.out.RankingRepository;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.api.vacancy.domain.port.out.VacancyRepository;
import com.plataformaempleo.common.domain.DomainException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RankingService {
    private final RankingRepository results;
    private final VacancyRepository vacancies;
    private final String provider;
    private final Clock clock;

    public RankingService(RankingRepository results, VacancyRepository vacancies, String provider, Clock clock) {
        this.results = results;
        this.vacancies = vacancies;
        this.provider = provider;
        this.clock = clock;
    }

    public record Ranking(List<RankingResult> candidates, long received, long pending, long failed, long notRequested) { }

    @Transactional(readOnly = true)
    public Ranking get(Actor actor, UUID vacancyId) {
        requireOwnVacancy(actor, vacancyId);
        List<RankingResult> shared = results.findSharedByVacancy(vacancyId, provider);
        List<RankingResult> analyzed = shared.stream().filter(RankingResult::analyzed)
                .sorted(Comparator.comparing(RankingResult::overallScore).reversed()
                        .thenComparing(RankingResult::appliedAt).thenComparing(RankingResult::applicationId))
                .toList();
        return new Ranking(analyzed, shared.size(),
                shared.stream().filter(r -> r.status() == ScreeningRequest.Status.PENDING
                        || r.status() == ScreeningRequest.Status.PROCESSING).count(),
                shared.stream().filter(r -> r.status() == ScreeningRequest.Status.FAILED).count(),
                shared.stream().filter(r -> r.status() == null).count());
    }

    @Transactional
    public void decide(Actor actor, UUID vacancyId, UUID applicationId, RankingResult.Decision decision) {
        requireOwnVacancy(actor, vacancyId);
        results.findSharedByVacancy(vacancyId, provider).stream()
                .filter(r -> r.applicationId().equals(applicationId) && r.analyzed()).findFirst()
                .orElseThrow(() -> new DomainException("ranking.result.not-found", "El resultado no está disponible"));
        results.saveDecision(applicationId, decision, actor.userId(), clock.instant());
    }

    private void requireOwnVacancy(Actor actor, UUID vacancyId) {
        UUID companyId = actor.requireCompany(Role.COMPANY_ADMIN, Role.RECRUITER);
        vacancies.findById(vacancyId).filter(v -> v.companyId().equals(companyId))
                .orElseThrow(() -> new DomainException("vacancy.not-found", "La vacante no existe"));
    }
}
