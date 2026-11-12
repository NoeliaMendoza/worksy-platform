package com.plataformaempleo.api.identity.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.identity.domain.model.UserPage;
import com.plataformaempleo.api.identity.domain.port.out.UserDirectory;
import com.plataformaempleo.api.shared.application.Actor;
import com.plataformaempleo.common.domain.DomainException;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

public class UserAdminService {
    private final UserDirectory users;

    public UserAdminService(UserDirectory users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserPage list(Actor actor, String query, String role, int page, int size) {
        actor.requireRole(Role.PLATFORM_ADMIN);
        String search = query == null ? "" : query.strip();
        if (page < 0 || size < 1 || size > 100 || search.codePointCount(0, search.length()) > 254) {
            throw new DomainException("user.directory.invalid", "La búsqueda admite hasta 254 caracteres y páginas de 1 a 100 usuarios");
        }
        Role filter = null;
        if (role != null && !role.isBlank()) {
            try {
                filter = Role.valueOf(role.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomainException("user.directory.role.invalid", "El rol seleccionado no es válido");
            }
        }
        return users.find(search.toLowerCase(Locale.ROOT), filter, page, size);
    }
}
