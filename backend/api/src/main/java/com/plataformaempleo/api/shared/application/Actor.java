package com.plataformaempleo.api.shared.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.common.domain.DomainException;
import java.util.Objects;
import java.util.UUID;

/**
 * Quién hace la petición, tal como lo acredita el token de acceso.
 * Los casos de uso lo reciben siempre: es la base de las reglas por rol y por empresa.
 */
public record Actor(UUID userId, Role role, UUID companyId) {

    public Actor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }

    /** Exige pertenecer a una empresa con uno de los roles indicados; devuelve el id de la empresa. */
    public UUID requireCompany(Role... allowed) {
        for (Role r : allowed) {
            if (r == role && companyId != null) {
                return companyId;
            }
        }
        throw forbidden();
    }

    public static DomainException forbidden() {
        return new DomainException("access.denied", "No tienes permiso para hacer esto");
    }
}
