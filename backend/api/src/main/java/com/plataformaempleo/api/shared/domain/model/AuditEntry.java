package com.plataformaempleo.api.shared.domain.model;

import com.plataformaempleo.api.identity.domain.model.Role;
import java.time.Instant;
import java.util.UUID;

public record AuditEntry(UUID id, Instant occurredAt, UUID actorId, String actorEmail, Role actorRole,
                         UUID companyId, String companyName, AuditAction action, UUID targetId, String targetLabel) { }
