package com.plataformaempleo.api.jobapplication.infrastructure.web;

import com.plataformaempleo.api.jobapplication.application.CompanyApplicationsService;
import com.plataformaempleo.api.jobapplication.domain.model.ApplicationStage;
import com.plataformaempleo.api.jobapplication.domain.model.CompanyApplication;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST de la pantalla "Candidatos".
 * Contrato nuevo aprobado por la usuaria el 2026-11-20 (no figuraba en la sección 6 del diseño técnico).
 */
@RestController
@Tag(name = "Candidatos", description = "Postulaciones de todas las vacantes de la empresa")
class CompanyApplicationsController {

    @Schema(name = "CompanyApplication",
            description = "Postulación vista por la empresa. Sin permiso de la persona (shared = false), no trae "
                    + "nombre, correo, CV, etapa, análisis ni entrevista. Nunca incluye la puntuación del modelo.")
    record CompanyApplicationResponse(
            UUID applicationId,
            UUID vacancyId,
            String vacancyTitle,
            Vacancy.Status vacancyStatus,
            Instant appliedAt,
            boolean shared,
            @Schema(description = "Solo si shared es true y la persona lo indicó en su perfil") String candidateName,
            @Schema(description = "Solo si shared es true") String candidateEmail,
            @Schema(description = "Solo si shared es true") UUID cvId,
            @Schema(description = "Solo si shared es true") ApplicationStage stage,
            @Schema(description = "Estado del análisis automático; solo si shared es true y se pidió")
            ScreeningRequest.Status screeningStatus,
            @Schema(description = "Motivo seguro si el análisis falló") String screeningFailureCode,
            @Schema(description = "Próxima entrevista pendiente o confirmada") Instant nextInterviewAt) {
        static CompanyApplicationResponse from(CompanyApplication a) {
            return new CompanyApplicationResponse(a.applicationId(), a.vacancyId(), a.vacancyTitle(), a.vacancyStatus(),
                    a.appliedAt(), a.shared(), a.candidateName(), a.candidateEmail(), a.cvId(), a.stage(),
                    a.screeningStatus(), a.screeningFailureCode(), a.nextInterviewAt());
        }
    }

    @Schema(name = "CompanyApplicationPage")
    record PageResponse(List<CompanyApplicationResponse> items, long total, int page, int size) {
    }

    private final CompanyApplicationsService service;

    CompanyApplicationsController(CompanyApplicationsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/companies/me/applications")
    @Operation(summary = "Postulaciones de todas las vacantes de la empresa",
            description = "De la más reciente a la más antigua. La búsqueda no distingue mayúsculas ni acentos y "
                    + "solo mira nombre y correo de quien comparte sus datos (y el título de la vacante).",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "400", description = "Página o tamaño fuera de rango")
    @ApiResponse(responseCode = "403", description = "La cuenta no es de una empresa")
    ResponseEntity<PageResponse> search(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Solo esta vacante") @RequestParam(required = false) UUID vacancyId,
            @Parameter(description = "Solo esta etapa (excluye a quien no comparte sus datos)")
            @RequestParam(required = false) ApplicationStage stage,
            @Parameter(description = "Texto a buscar (hasta 100 caracteres)") @RequestParam(required = false) String q,
            @Parameter(description = "Página, desde 0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamaño de página, de 1 a 100 (25 por defecto)")
            @RequestParam(required = false) Integer size) {
        var result = service.search(CurrentActor.from(jwt), new CompanyApplication.Filter(vacancyId, stage, q), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PageResponse(
                result.items().stream().map(CompanyApplicationResponse::from).toList(),
                result.total(), result.page(), result.size()));
    }

    @GetMapping("/api/v1/companies/me/applications/{applicationId}")
    @Operation(summary = "Una postulación de la empresa", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "404", description = "No existe entre las postulaciones de la empresa")
    ResponseEntity<CompanyApplicationResponse> find(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CompanyApplicationResponse.from(service.find(CurrentActor.from(jwt), applicationId)));
    }
}
