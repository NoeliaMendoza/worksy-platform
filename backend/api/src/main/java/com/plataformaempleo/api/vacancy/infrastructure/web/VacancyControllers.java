package com.plataformaempleo.api.vacancy.infrastructure.web;

import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import com.plataformaempleo.api.vacancy.application.VacancyService;
import com.plataformaempleo.api.vacancy.application.VacancyService.PublishedVacancy;
import com.plataformaempleo.api.vacancy.infrastructure.web.VacancyDtos.CompanyVacancyResponse;
import com.plataformaempleo.api.vacancy.infrastructure.web.VacancyDtos.CriterionResponse;
import com.plataformaempleo.api.vacancy.infrastructure.web.VacancyDtos.PublicDetail;
import com.plataformaempleo.api.vacancy.infrastructure.web.VacancyDtos.PublicSummary;
import com.plataformaempleo.api.vacancy.infrastructure.web.VacancyDtos.VacancyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptadores de entrada REST para vacantes: lista pública y gestión de la empresa. */
final class VacancyControllers {

    private VacancyControllers() {
    }

    @RestController
    @RequestMapping("/api/v1/vacancies")
    @Tag(name = "Vacantes públicas", description = "Vacantes publicadas, visibles sin iniciar sesión")
    static class PublicVacancyController {

        private final VacancyService service;

        PublicVacancyController(VacancyService service) {
            this.service = service;
        }

        @GetMapping
        @Operation(summary = "Vacantes publicadas", description = "De la más reciente a la más antigua (máximo 100).")
        List<PublicSummary> list() {
            return service.listPublished().stream().map(PublicVacancyController::summary).toList();
        }

        @GetMapping("/{id}")
        @Operation(summary = "Detalle de una vacante publicada", description = "Incluye las preguntas con las que se evaluará cada CV.")
        @ApiResponse(responseCode = "404", description = "No existe o no está publicada")
        PublicDetail get(@PathVariable UUID id) {
            PublishedVacancy p = service.getPublished(id);
            var v = p.vacancy();
            var c = v.content();
            return new PublicDetail(v.id(), c.title(), p.companyName(), c.description(), c.location(), c.modality(),
                    v.publishedAt(), c.criteria().stream().map(CriterionResponse::from).toList());
        }

        private static PublicSummary summary(PublishedVacancy p) {
            var v = p.vacancy();
            return new PublicSummary(v.id(), v.content().title(), p.companyName(), v.content().location(),
                    v.content().modality(), v.publishedAt());
        }
    }

    @RestController
    @RequestMapping("/api/v1/companies/me/vacancies")
    @Tag(name = "Vacantes de la empresa", description = "Crear, editar, publicar y cerrar vacantes")
    static class CompanyVacancyController {

        private final VacancyService service;

        CompanyVacancyController(VacancyService service) {
            this.service = service;
        }

        @GetMapping
        @Operation(summary = "Vacantes de la empresa", security = @SecurityRequirement(name = "bearer"))
        List<CompanyVacancyResponse> list(@AuthenticationPrincipal Jwt jwt) {
            return service.listForCompany(CurrentActor.from(jwt)).stream().map(CompanyVacancyResponse::from).toList();
        }

        @PostMapping
        @Operation(summary = "Crear una vacante en borrador", security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "201", description = "Vacante creada")
        @ApiResponse(responseCode = "400", description = "Datos o criterios no válidos")
        @ApiResponse(responseCode = "403", description = "La cuenta no pertenece a una empresa")
        ResponseEntity<CompanyVacancyResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody VacancyRequest body) {
            var created = service.create(CurrentActor.from(jwt), body.toContent());
            return ResponseEntity.created(URI.create("/api/v1/companies/me/vacancies/" + created.id()))
                    .body(CompanyVacancyResponse.from(created));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Vacante de la empresa", security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "404", description = "No existe en esta empresa")
        CompanyVacancyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
            return CompanyVacancyResponse.from(service.getForCompany(CurrentActor.from(jwt), id));
        }

        @PutMapping("/{id}")
        @Operation(summary = "Editar una vacante en borrador", security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "400", description = "Datos no válidos o la vacante ya no está en borrador")
        CompanyVacancyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody VacancyRequest body) {
            return CompanyVacancyResponse.from(service.update(CurrentActor.from(jwt), id, body.toContent()));
        }

        @PostMapping("/{id}/publish")
        @Operation(summary = "Publicar una vacante", description = "Necesita al menos un criterio.",
                security = @SecurityRequirement(name = "bearer"))
        CompanyVacancyResponse publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
            return CompanyVacancyResponse.from(service.publish(CurrentActor.from(jwt), id));
        }

        @PostMapping("/{id}/close")
        @Operation(summary = "Cerrar una vacante publicada", security = @SecurityRequirement(name = "bearer"))
        CompanyVacancyResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
            return CompanyVacancyResponse.from(service.close(CurrentActor.from(jwt), id));
        }
    }
}
