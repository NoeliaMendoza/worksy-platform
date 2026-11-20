package com.plataformaempleo.api.jobapplication.infrastructure.web;

import com.plataformaempleo.api.jobapplication.application.ApplicationUpdatesService;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationUpdates;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApplicationUpdatesController {
    private final ApplicationUpdatesService service;

    ApplicationUpdatesController(ApplicationUpdatesService service) {
        this.service = service;
    }

    @Schema(name = "ReadApplicationProgress")
    record ReadProgress(@Schema(description = "Último evento del historial que se mostró al candidato") @Positive long lastSeenEventId) { }

    @GetMapping("/api/v1/candidates/me/application-updates")
    @Operation(summary = "Cambios de etapa sin leer en mis postulaciones",
            description = "Total de cambios sin leer y hasta 50 postulaciones con novedades, de la más reciente a la más antigua.",
            security = @SecurityRequirement(name = "bearer"))
    ApplicationUpdates inbox(@AuthenticationPrincipal Jwt jwt) {
        return service.inbox(CurrentActor.from(jwt));
    }

    @PostMapping("/api/v1/candidates/me/applications/{applicationId}/progress/read")
    @Operation(summary = "Marcar el seguimiento mostrado como leído",
            description = "Marca monotónica limitada al evento indicado; no oculta cambios posteriores. Solo postulaciones propias.",
            security = @SecurityRequirement(name = "bearer"))
    ResponseEntity<Void> markSeen(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId,
                                  @Valid @RequestBody ReadProgress body) {
        service.markSeen(CurrentActor.from(jwt), applicationId, body.lastSeenEventId());
        return ResponseEntity.noContent().build();
    }
}
