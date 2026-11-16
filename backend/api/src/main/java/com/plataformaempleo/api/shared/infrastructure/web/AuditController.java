package com.plataformaempleo.api.shared.infrastructure.web;

import com.plataformaempleo.api.shared.application.AuditService;
import com.plataformaempleo.api.shared.domain.model.AuditPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "Administración de plataforma")
class AuditController {
    private final AuditService service;
    AuditController(AuditService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Registro de acciones de empresas, vacantes y preselección",
            security = @SecurityRequirement(name = "bearer"))
    AuditPage list(@AuthenticationPrincipal Jwt jwt,
                   @RequestParam(name = "q", defaultValue = "") String query,
                   @RequestParam(name = "action", defaultValue = "") String action,
                   @RequestParam(name = "page", defaultValue = "0") int page,
                   @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(CurrentActor.from(jwt), query, action, page, size);
    }
}
