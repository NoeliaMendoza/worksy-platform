package com.plataformaempleo.api.interview.infrastructure.web;

import com.plataformaempleo.api.interview.application.InterviewService;
import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "Entrevistas", description = "Citas entre la empresa y el candidato, con confirmación e historial")
@SecurityRequirement(name = "bearer")
class InterviewController {
    private final InterviewService service;
    InterviewController(InterviewService service) { this.service = service; }

    record DetailsBody(@NotBlank @Size(max = 120) String title, @NotNull Instant startsAt,
                       @Min(15) @Max(180) int durationMinutes, @NotBlank @Size(max = 64) String zoneId,
                       @NotNull Interview.Mode mode, @NotBlank @Size(max = 500) String place,
                       @Schema(description = "Mensaje visible para el candidato, no notas internas") @Size(max = 1000) String note) {
        @Override public String toString() { return "InterviewDetails[redacted]"; }
    }
    record ScheduleBody(@NotNull UUID id, @NotNull UUID applicationId, @NotNull @Valid DetailsBody details) { }
    record UpdateBody(@NotNull @Min(0) Integer expectedVersion, @NotNull @Valid DetailsBody details) { }
    record VersionBody(@NotNull @Min(0) Integer expectedVersion) { }
    record ResponseBody(@NotNull @Min(0) Integer expectedVersion, @NotNull Interview.Status status) { }
    record UnreadCount(long unread) { }

    @GetMapping
    @Operation(summary = "Entrevistas propias o de mi empresa", description = "Páginas de 20 citas: próximas, historial o actualizaciones sin leer")
    InterviewService.Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "upcoming") Interview.View view,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page) {
        return service.list(CurrentActor.from(jwt), view, page);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Cantidad de entrevistas con cambios sin leer")
    UnreadCount unread(@AuthenticationPrincipal Jwt jwt) { return new UnreadCount(service.unread(CurrentActor.from(jwt))); }

    @GetMapping("/context/{applicationId}")
    @Operation(summary = "Postulación autorizada para agendar una entrevista", description = "Solo empresa propietaria y consentimiento actual")
    InterviewService.Context context(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
        return service.context(CurrentActor.from(jwt), applicationId);
    }

    @PostMapping
    @Operation(summary = "Invitar a una entrevista", description = "La empresa genera un UUID por intención; repetirlo con los mismos detalles no duplica la cita ni el aviso")
    ResponseEntity<Interview> schedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleBody body) {
        DetailsBody d = body.details();
        Interview interview = service.schedule(CurrentActor.from(jwt), body.id(), body.applicationId(), d.title(), d.startsAt(),
                d.durationMinutes(), d.zoneId(), d.mode(), d.place(), d.note());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + interview.id() + "/history")).body(interview);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reprogramar o cambiar los detalles", description = "La invitación vuelve a pendiente de confirmación. Conflictos de versión devuelven 409")
    Interview update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateBody body) {
        DetailsBody d = body.details();
        return service.update(CurrentActor.from(jwt), id, body.expectedVersion(), d.title(), d.startsAt(), d.durationMinutes(),
                d.zoneId(), d.mode(), d.place(), d.note());
    }

    @PostMapping("/{id}/response")
    @Operation(summary = "Confirmar o rechazar una invitación propia", description = "Solo CONFIRMED o DECLINED; no cambia la etapa de selección")
    Interview respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ResponseBody body) {
        return service.respond(CurrentActor.from(jwt), id, body.expectedVersion(), body.status());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar una entrevista de mi empresa")
    Interview cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionBody body) {
        return service.cancel(CurrentActor.from(jwt), id, body.expectedVersion());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historial de cambios de la cita", description = "No expone identificadores internos de los actores")
    List<InterviewRepository.Event> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.history(CurrentActor.from(jwt), id);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marcar como vista la versión mostrada", description = "No oculta cambios posteriores ni retrocede la marca")
    ResponseEntity<Void> read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionBody body) {
        service.markRead(CurrentActor.from(jwt), id, body.expectedVersion());
        return ResponseEntity.noContent().build();
    }
}
