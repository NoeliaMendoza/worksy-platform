package com.plataformaempleo.api.identity.infrastructure.web;

import com.plataformaempleo.api.identity.application.UserAdminService;
import com.plataformaempleo.api.identity.domain.model.UserPage;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
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
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Administración de plataforma")
class UserAdminController {
    private final UserAdminService service;

    UserAdminController(UserAdminService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Cuentas activas de la plataforma con búsqueda y filtro por rol",
            security = @SecurityRequirement(name = "bearer"))
    UserPage list(@AuthenticationPrincipal Jwt jwt,
                  @RequestParam(name = "q", defaultValue = "") String query,
                  @RequestParam(name = "role", defaultValue = "") String role,
                  @RequestParam(name = "page", defaultValue = "0") int page,
                  @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(CurrentActor.from(jwt), query, role, page, size);
    }
}
