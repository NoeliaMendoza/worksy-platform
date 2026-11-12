package com.plataformaempleo.api.jobapplication.infrastructure.web;

import com.plataformaempleo.api.jobapplication.application.JobApplicationService;
import com.plataformaempleo.api.jobapplication.application.JobApplicationService.CandidateView;
import com.plataformaempleo.api.jobapplication.application.JobApplicationService.CvLink;
import com.plataformaempleo.api.jobapplication.application.JobApplicationService.RecruiterView;
import com.plataformaempleo.api.jobapplication.domain.model.JobApplication;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptadores de entrada REST de las postulaciones.
 * Contrato nuevo aprobado por el usuario (no figuraba en la sección 6 del diseño técnico).
 */
final class JobApplicationControllers {

    private JobApplicationControllers() {
    }

    @Schema(name = "ApplyRequest")
    record ApplyRequest(@Schema(description = "CV con el que se postula; debe ser propio") @NotNull UUID cvId) {
    }

    @Schema(name = "Application", description = "Postulación recién creada")
    record ApplicationResponse(UUID id, UUID vacancyId, UUID cvId, Instant appliedAt) {
        static ApplicationResponse from(JobApplication a) {
            return new ApplicationResponse(a.id(), a.vacancyId(), a.cvId(), a.appliedAt());
        }
    }

    @Schema(name = "MyApplication", description = "Postulación vista por la persona que se postuló")
    record MyApplicationResponse(UUID id, UUID vacancyId, String vacancyTitle, String companyName,
                                 Vacancy.Status vacancyStatus, UUID cvId, Instant appliedAt) {
        static MyApplicationResponse from(CandidateView v) {
            var a = v.application();
            return new MyApplicationResponse(a.id(), a.vacancyId(), v.vacancyTitle(), v.companyName(),
                    v.vacancyStatus(), a.cvId(), a.appliedAt());
        }
    }

    @Schema(name = "VacancyApplication",
            description = "Postulación vista por la empresa. Sin permiso de la persona, no incluye sus datos.")
    record VacancyApplicationResponse(
            UUID id,
            Instant appliedAt,
            @Schema(description = "false si la persona retiró el permiso de compartir su CV") boolean shared,
            @Schema(description = "Solo si shared es true") String candidateEmail,
            @Schema(description = "Solo si shared es true") UUID cvId) {
        static VacancyApplicationResponse from(RecruiterView v) {
            return new VacancyApplicationResponse(v.applicationId(), v.appliedAt(), v.shared(), v.candidateEmail(),
                    v.cvId());
        }
    }

    @Schema(name = "CvLink", description = "Enlace temporal de solo lectura al PDF del CV")
    record CvLinkResponse(
            @Schema(description = "Abre el PDF directamente desde el almacenamiento; funciona sin sesión hasta expiresAt")
            String url,
            Instant expiresAt) {
        static CvLinkResponse from(CvLink link) {
            return new CvLinkResponse(link.url().toString(), link.expiresAt());
        }
    }

    @RestController
    @Tag(name = "Postulaciones", description = "Postular a vacantes publicadas y seguir las postulaciones")
    static class CandidateApplicationController {

        private final JobApplicationService service;

        CandidateApplicationController(JobApplicationService service) {
            this.service = service;
        }

        @PostMapping("/api/v1/vacancies/{id}/applications")
        @Operation(summary = "Postular a una vacante publicada",
                description = "Exige haber permitido compartir el CV con las empresas a las que se postula.",
                security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "201", description = "Postulación creada")
        @ApiResponse(responseCode = "400", description = "Falta el permiso para compartir el CV")
        @ApiResponse(responseCode = "403", description = "La cuenta no es de candidato")
        @ApiResponse(responseCode = "404", description = "La vacante no está publicada o el CV no es propio")
        @ApiResponse(responseCode = "409", description = "Ya se postuló a esta vacante")
        ResponseEntity<ApplicationResponse> apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                  @Valid @RequestBody ApplyRequest body) {
            var created = service.apply(CurrentActor.from(jwt), id, body.cvId());
            return ResponseEntity.created(URI.create("/api/v1/candidates/me/applications/" + created.id()))
                    .body(ApplicationResponse.from(created));
        }

        @GetMapping("/api/v1/candidates/me/applications")
        @Operation(summary = "Mis postulaciones", description = "De la más reciente a la más antigua.",
                security = @SecurityRequirement(name = "bearer"))
        List<MyApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
            return service.mine(CurrentActor.from(jwt)).stream().map(MyApplicationResponse::from).toList();
        }

        @DeleteMapping("/api/v1/candidates/me/applications/{applicationId}")
        @Operation(summary = "Retirar una postulación", security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "204", description = "Postulación retirada")
        @ApiResponse(responseCode = "404", description = "No existe entre las postulaciones propias")
        ResponseEntity<Void> withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
            service.withdraw(CurrentActor.from(jwt), applicationId);
            return ResponseEntity.noContent().build();
        }
    }

    @RestController
    @Tag(name = "Vacantes de la empresa")
    static class CompanyApplicationController {

        private final JobApplicationService service;

        CompanyApplicationController(JobApplicationService service) {
            this.service = service;
        }

        @GetMapping("/api/v1/companies/me/vacancies/{id}/applications")
        @Operation(summary = "Postulaciones a una vacante de la empresa", description = "En orden de llegada.",
                security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "404", description = "La vacante no existe en esta empresa")
        List<VacancyApplicationResponse> forVacancy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
            return service.forVacancy(CurrentActor.from(jwt), id).stream().map(VacancyApplicationResponse::from).toList();
        }

        @GetMapping("/api/v1/companies/me/vacancies/{id}/applications/{applicationId}/cv")
        @Operation(summary = "Enlace temporal para ver el CV de una postulación",
                description = "Caduca a los 5 minutos. Solo si la persona mantiene el permiso de compartir su CV.",
                security = @SecurityRequirement(name = "bearer"))
        @ApiResponse(responseCode = "403", description = "La persona retiró el permiso de compartir su CV")
        @ApiResponse(responseCode = "404", description = "La vacante o la postulación no existen en esta empresa")
        CvLinkResponse cv(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID applicationId) {
            return CvLinkResponse.from(service.cvLink(CurrentActor.from(jwt), id, applicationId));
        }
    }
}
