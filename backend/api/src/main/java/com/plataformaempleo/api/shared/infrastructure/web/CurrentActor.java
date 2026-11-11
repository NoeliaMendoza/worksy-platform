package com.plataformaempleo.api.shared.infrastructure.web;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.application.Actor;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Construye el {@link Actor} a partir del token de acceso ya validado por Spring Security. */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static Actor from(Jwt jwt) {
        String company = jwt.getClaimAsString("company_id");
        return new Actor(UUID.fromString(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")),
                company == null ? null : UUID.fromString(company));
    }
}
