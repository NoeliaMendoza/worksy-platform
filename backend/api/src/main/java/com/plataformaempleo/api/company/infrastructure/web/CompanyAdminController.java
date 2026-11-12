package com.plataformaempleo.api.company.infrastructure.web;

import com.plataformaempleo.api.company.application.CompanyAdminService;
import com.plataformaempleo.api.company.domain.model.CompanyPage;
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
@RequestMapping("/api/v1/admin/companies")
@Tag(name = "Administración de plataforma")
class CompanyAdminController {
    private final CompanyAdminService service;

    CompanyAdminController(CompanyAdminService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Empresas registradas con cantidades de equipo y vacantes",
            security = @SecurityRequirement(name = "bearer"))
    CompanyPage list(@AuthenticationPrincipal Jwt jwt,
                     @RequestParam(name = "q", defaultValue = "") String query,
                     @RequestParam(name = "page", defaultValue = "0") int page,
                     @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(CurrentActor.from(jwt), query, page, size);
    }
}
