package com.plataformaempleo.api.screening.infrastructure.web;

import com.plataformaempleo.api.screening.application.ScreeningService;
import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada REST de la preselección (sección 6 del diseño: POST de preselecciones con Idempotency-Key). */
@RestController
@RequestMapping("/api/v1/companies/me/vacancies/{id}/screenings")
@Tag(name = "Preselección", description = "Pedir la preselección automática de los CV de una vacante")
class ScreeningController {

    @Schema(name = "ScreeningRequestBody")
    record ScreeningBody(
            @Schema(description = "Postulaciones a preseleccionar; vacío o ausente = todas las de la vacante")
            @Size(max = 500) List<UUID> applicationIds) {
    }

    @Schema(name = "ScreeningRequest", description = "Solicitud de preselección de un CV")
    record ScreeningResponse(UUID id, UUID applicationId, String provider, ScreeningRequest.Status status,
                             String modelVersion, BigDecimal overallScore, BigDecimal confidence,
                             Instant requestedAt, Instant updatedAt) {
        static ScreeningResponse from(ScreeningRequest r) {
            return new ScreeningResponse(r.id(), r.applicationId(), r.provider(), r.status(), r.modelVersion(),
                    r.overallScore(), r.confidence(), r.requestedAt(), r.updatedAt());
        }
    }

    @Schema(name = "ScreeningBatch")
    record BatchResponse(
            List<ScreeningResponse> requests,
            @Schema(description = "Postulaciones no incluidas: ya tenían solicitud o la persona no comparte su CV")
            int skipped,
            @Schema(description = "true si es la respuesta de una petición anterior con la misma Idempotency-Key")
            boolean replayed) {
    }

    @Schema(name = "ScreeningRetry")
    record RetryResponse(ScreeningResponse request, boolean replayed) { }

    private final ScreeningService service;

    ScreeningController(ScreeningService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Pedir la preselección de los CV de una vacante",
            description = "Crea las solicitudes y los eventos screening.requested en una transacción; el trabajador "
                    + "de preselección las procesa en segundo plano. Exige Idempotency-Key.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "202", description = "Solicitudes aceptadas para procesarse")
    @ApiResponse(responseCode = "400", description = "Falta Idempotency-Key, se reutilizó con otra petición o la vacante es un borrador")
    @ApiResponse(responseCode = "404", description = "La vacante o una postulación no existen en esta empresa")
    ResponseEntity<BatchResponse> request(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Parameter(description = "Clave única por intención del usuario (p. ej., un UUID por clic)")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ScreeningBody body) {
        var result = service.request(CurrentActor.from(jwt), id, body == null ? null : body.applicationIds(),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BatchResponse(
                result.created().stream().map(ScreeningResponse::from).toList(), result.skipped(), result.replayed()));
    }

    @GetMapping
    @Operation(summary = "Solicitudes de preselección de una vacante", security = @SecurityRequirement(name = "bearer"))
    List<ScreeningResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.forVacancy(CurrentActor.from(jwt), id).stream().map(ScreeningResponse::from).toList();
    }

    @PostMapping("/{requestId}/retry")
    @Operation(summary = "Reintentar un análisis fallido",
            description = "Mantiene la solicitud y el proveedor original; publica un mensaje nuevo. Exige Idempotency-Key.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "202", description = "Reintento aceptado o respuesta de un reintento anterior")
    @ApiResponse(responseCode = "403", description = "No hay permiso para compartir el CV")
    @ApiResponse(responseCode = "404", description = "La vacante o el análisis no existen en esta empresa")
    @ApiResponse(responseCode = "409", description = "El análisis está en proceso o ya terminó correctamente")
    ResponseEntity<RetryResponse> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @PathVariable UUID requestId,
                                        @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        var result = service.retry(CurrentActor.from(jwt), id, requestId, key);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RetryResponse(ScreeningResponse.from(result.request()), result.replayed()));
    }
}
