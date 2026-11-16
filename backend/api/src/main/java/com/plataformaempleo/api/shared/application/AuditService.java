package com.plataformaempleo.api.shared.application;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.shared.domain.model.AuditPage;
import com.plataformaempleo.api.shared.domain.port.out.AuditTrail;
import com.plataformaempleo.common.domain.DomainException;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

public class AuditService {
    private final AuditTrail trail;
    public AuditService(AuditTrail trail) { this.trail = trail; }

    @Transactional(readOnly = true)
    public AuditPage list(Actor actor, String query, String action, int page, int size) {
        actor.requireRole(Role.PLATFORM_ADMIN);
        String search = query == null ? "" : query.strip();
        if (page < 0 || size < 1 || size > 100 || search.codePointCount(0, search.length()) > 254) {
            throw new DomainException("audit.query.invalid", "La búsqueda admite hasta 254 caracteres y páginas de 1 a 100 eventos");
        }
        AuditAction filter = null;
        if (action != null && !action.isBlank()) {
            try {
                filter = AuditAction.valueOf(action.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new DomainException("audit.action.invalid", "La acción seleccionada no es válida");
            }
        }
        return trail.find(search.toLowerCase(Locale.ROOT), filter, page, size);
    }
}
