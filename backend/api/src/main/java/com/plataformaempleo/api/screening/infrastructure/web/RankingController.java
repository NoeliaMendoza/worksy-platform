package com.plataformaempleo.api.screening.infrastructure.web;

import com.plataformaempleo.api.screening.application.RankingService;
import com.plataformaempleo.api.screening.domain.model.RankingResult;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies/me/vacancies/{id}/ranking")
class RankingController {
    private final RankingService service;
    RankingController(RankingService service) { this.service = service; }

    record DecisionBody(RankingResult.Decision decision) { }

    @GetMapping
    RankingService.Ranking get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentActor.from(jwt), id);
    }

    @PutMapping("/{applicationId}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID applicationId,
                @RequestBody DecisionBody body) {
        service.decide(CurrentActor.from(jwt), id, applicationId, body.decision());
    }
}
