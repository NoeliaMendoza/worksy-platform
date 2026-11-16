package com.plataformaempleo.api.shared.application;

import com.plataformaempleo.api.shared.domain.model.AuditAction;
import com.plataformaempleo.api.shared.domain.port.out.AuditTrail;
import java.time.Clock;
import java.util.UUID;

/** Escribe a través del puerto dentro de la transacción del cambio de negocio. */
public class AuditRecorder {
    private final AuditTrail trail;
    private final Clock clock;

    public AuditRecorder(AuditTrail trail, Clock clock) {
        this.trail = trail;
        this.clock = clock;
    }

    public void record(Actor actor, AuditAction action, UUID targetId, String targetLabel) {
        trail.append(actor.userId(), actor.role(), actor.companyId(), action, targetId, targetLabel, clock.instant());
    }
}
