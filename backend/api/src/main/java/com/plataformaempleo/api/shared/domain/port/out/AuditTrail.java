package com.plataformaempleo.api.shared.domain.port.out;

import com.plataformaempleo.api.identity.domain.model.Role;
import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.shared.domain.model.AuditPage;
import java.time.Instant;
import java.util.UUID;

public interface AuditTrail {
    void append(UUID actorId, Role actorRole, UUID companyId, AuditAction action,
                UUID targetId, String targetLabel, Instant occurredAt);

    AuditPage find(String query, AuditAction action, int page, int size);
}
