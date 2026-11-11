package com.plataformaempleo.api.candidate.application;

import com.plataformaempleo.api.candidate.domain.model.CandidateConsents;
import com.plataformaempleo.api.candidate.domain.port.out.CandidateProfileRepository;
import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Consentimientos explícitos del candidato (privacidad, sección 9 del diseño). */
public class ConsentService {

    private final CandidateProfileRepository profiles;
    private final Clock clock;

    public ConsentService(CandidateProfileRepository profiles, Clock clock) {
        this.profiles = Objects.requireNonNull(profiles);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public CandidateConsents get(Actor actor) {
        return profiles.findConsents(actor.requireRole(Role.CANDIDATE)).orElseGet(CandidateConsents::none);
    }

    @Transactional
    public CandidateConsents update(Actor actor, boolean shareWithRecruiters, boolean githubLink) {
        var userId = actor.requireRole(Role.CANDIDATE);
        return profiles.saveConsents(userId, new CandidateConsents(shareWithRecruiters, githubLink, clock.instant()));
    }
}
