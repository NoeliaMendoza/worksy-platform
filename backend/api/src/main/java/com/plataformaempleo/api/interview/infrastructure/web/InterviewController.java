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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.plataformaempleo.api.identity.domain.model.Role;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "Entrevistas", description = "Citas entre la empresa y el candidato: confirmación, propuestas de otra hora, resultado e historial")
@SecurityRequirement(name = "bearer")
class InterviewController {
    private final InterviewService service;
    private final String publicUrl;
    private final Clock clock;
    InterviewController(InterviewService service, @Value("${worksy.auth.public-url}") String publicUrl, Clock clock) {
        this.service = service;
        this.publicUrl = publicUrl;
        this.clock = clock;
    }

    @GetMapping(value = "/{id}/calendar", produces = "text/calendar")
    @Operation(summary = "Archivo .ics de la cita para agregarla a cualquier calendario",
            description = "Sin conectar cuentas externas. Descargarlo de nuevo actualiza el evento (mismo UID, SEQUENCE = versión)")
    ResponseEntity<byte[]> calendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var actor = CurrentActor.from(jwt);
        InterviewRepository.Item item = service.get(actor, id);
        byte[] file = InterviewCalendarFile.build(item, actor.role() != Role.CANDIDATE, publicUrl, clock.instant());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("entrevista-worksy.ics").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file);
    }

    record DetailsBody(@NotBlank @Size(max = 120) String title, @NotNull Instant startsAt,
                       @Min(15) @Max(180) int durationMinutes, @NotBlank @Size(max = 64) String zoneId,
                       @NotNull Interview.Mode mode,
                       @Schema(description = "Enlace https (ONLINE) o dirección (IN_PERSON); se ignora en VIDEO") @Size(max = 500) String place,
                       @Schema(description = "Mensaje visible para el candidato, no notas internas") @Size(max = 1000) String note) {
        @Override public String toString() { return "InterviewDetails[redacted]"; }
    }
    record ScheduleBody(@NotNull UUID id, @NotNull UUID applicationId, @NotNull @Valid DetailsBody details) { }
    record UpdateBody(@NotNull @Min(0) Integer expectedVersion, @NotNull @Valid DetailsBody details,
                      @Schema(description = "Motivo del cambio, visible para el candidato") @Size(max = 300) String reason) {
        @Override public String toString() { return "InterviewUpdate[redacted]"; }
    }
    record VersionBody(@NotNull @Min(0) Integer expectedVersion) { }
    record CancelBody(@NotNull @Min(0) Integer expectedVersion, @NotBlank @Size(min = 3, max = 300) String reason) {
        @Override public String toString() { return "InterviewCancel[redacted]"; }
    }
    record ResponseBody(@NotNull @Min(0) Integer expectedVersion, @NotNull Interview.Status status,
                        @Schema(description = "Motivo opcional al avisar que no puede asistir") @Size(max = 300) String reason) {
        @Override public String toString() { return "InterviewResponse[" + status + "]"; }
    }
    record ChangeRequestBody(@NotNull UUID id, @NotNull @Min(0) Integer expectedVersion, @NotBlank @Size(min = 3, max = 300) String reason,
                             @NotEmpty @Size(max = 3) List<@NotNull Instant> slots) {
        @Override public String toString() { return "ChangeRequest[redacted]"; }
    }
    record WithdrawBody(@NotNull UUID requestId, @NotNull @Min(0) Integer expectedVersion) { }
    record AcceptBody(@NotNull UUID requestId, @NotNull @Min(0) Integer expectedVersion, @NotNull @Min(0) @Max(2) Integer slot) { }
    record RejectBody(@NotNull UUID requestId, @NotNull @Min(0) Integer expectedVersion, @Size(max = 300) String reason) {
        @Override public String toString() { return "ChangeRejection[redacted]"; }
    }
    record OutcomeBody(@NotNull @Min(0) Integer expectedVersion, @NotNull Interview.Status outcome) { }
    record UnreadCount(long unread) { }

    @GetMapping
    @Operation(summary = "Entrevistas propias o de mi empresa",
            description = "Vistas: upcoming, pending (por confirmar), history, cancelled, updates (sin leer) en páginas de 20; "
                    + "range con from y to (hasta 6 semanas, sin paginar) para el calendario. mine=true: solo las que tengo a cargo")
    InterviewService.Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "upcoming") Interview.View view,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                               @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
                               @RequestParam(defaultValue = "false") boolean mine) {
        return service.list(CurrentActor.from(jwt), view, page, from, to, mine);
    }

    @GetMapping("/summary")
    @Operation(summary = "Conteos por pestaña y próxima entrevista")
    InterviewService.Summary summary(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean mine) {
        return service.summary(CurrentActor.from(jwt), mine);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Cantidad de entrevistas con cambios sin leer")
    UnreadCount unread(@AuthenticationPrincipal Jwt jwt) { return new UnreadCount(service.unread(CurrentActor.from(jwt))); }

    @GetMapping("/overlaps")
    @Operation(summary = "Mis citas vigentes que se cruzan con un horario", description = "Aviso para la empresa antes de guardar; no bloquea")
    List<InterviewRepository.Overlap> overlaps(@AuthenticationPrincipal Jwt jwt, @RequestParam Instant startsAt,
                                               @RequestParam int durationMinutes, @RequestParam(required = false) UUID exclude) {
        return service.overlaps(CurrentActor.from(jwt), startsAt, durationMinutes, exclude);
    }

    @GetMapping("/context/{applicationId}")
    @Operation(summary = "Postulación autorizada para agendar una entrevista", description = "Solo empresa propietaria y consentimiento actual")
    InterviewService.Context context(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
        return service.context(CurrentActor.from(jwt), applicationId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Una entrevista con su contexto, su situación y la propuesta abierta, si la hay")
    InterviewRepository.Item get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(CurrentActor.from(jwt), id);
    }

    @PostMapping
    @Operation(summary = "Invitar a una entrevista", description = "La empresa genera un UUID por intención; repetirlo con los mismos detalles no duplica la cita ni el aviso. "
            + "Una cita que se cruza con otra vigente de la misma postulación devuelve 409")
    ResponseEntity<Interview> schedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ScheduleBody body) {
        DetailsBody d = body.details();
        Interview interview = service.schedule(CurrentActor.from(jwt), body.id(), body.applicationId(), d.title(), d.startsAt(),
                d.durationMinutes(), d.zoneId(), d.mode(), d.place(), d.note());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + interview.id())).body(interview);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reprogramar o cambiar los detalles",
            description = "Si cambian la hora, la duración, la zona, la modalidad o el lugar, vuelve a «por confirmar» y la propuesta abierta queda superada; "
                    + "si solo cambian el título o el mensaje, se mantiene la respuesta. Conflictos de versión devuelven 409")
    Interview update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateBody body) {
        DetailsBody d = body.details();
        return service.update(CurrentActor.from(jwt), id, body.expectedVersion(), d.title(), d.startsAt(), d.durationMinutes(),
                d.zoneId(), d.mode(), d.place(), d.note(), body.reason());
    }

    @PostMapping("/{id}/response")
    @Operation(summary = "Confirmar o avisar que no puedo asistir",
            description = "CONFIRMED o DECLINED. Repetir la misma respuesta devuelve 200 sin cambios. No cambia la etapa de selección")
    Interview respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ResponseBody body) {
        return service.respond(CurrentActor.from(jwt), id, body.expectedVersion(), body.status(), body.reason());
    }

    @PostMapping("/{id}/change-requests")
    @Operation(summary = "Proponer otra hora (candidato)", description = "De 1 a 3 horarios y un motivo; la cita sigue en pie. Una propuesta abierta por cita")
    Interview requestChange(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ChangeRequestBody body) {
        return service.requestChange(CurrentActor.from(jwt), id, body.expectedVersion(), body.id(), body.reason(), body.slots());
    }

    @PostMapping("/{id}/change-requests/withdraw")
    @Operation(summary = "Retirar mi propuesta de otra hora")
    Interview withdrawChange(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody WithdrawBody body) {
        return service.withdrawChange(CurrentActor.from(jwt), id, body.expectedVersion(), body.requestId());
    }

    @PostMapping("/{id}/change-requests/accept")
    @Operation(summary = "Aceptar una de las horas propuestas (empresa)", description = "La cita pasa a esa hora y queda confirmada")
    Interview acceptChange(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AcceptBody body) {
        return service.acceptChange(CurrentActor.from(jwt), id, body.expectedVersion(), body.requestId(), body.slot());
    }

    @PostMapping("/{id}/change-requests/reject")
    @Operation(summary = "Mantener la hora actual (empresa)")
    Interview rejectChange(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RejectBody body) {
        return service.rejectChange(CurrentActor.from(jwt), id, body.expectedVersion(), body.requestId(), body.reason());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar una entrevista de mi empresa", description = "Motivo obligatorio, visible para el candidato")
    Interview cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody CancelBody body) {
        return service.cancel(CurrentActor.from(jwt), id, body.expectedVersion(), body.reason());
    }

    @PostMapping("/{id}/outcome")
    @Operation(summary = "Registrar si la entrevista se realizó", description = "COMPLETED o NO_SHOW, desde la hora de inicio; corregible 24 h. "
            + "No cambia la etapa ni decide el proceso")
    Interview outcome(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody OutcomeBody body) {
        return service.recordOutcome(CurrentActor.from(jwt), id, body.expectedVersion(), body.outcome());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historial de cambios de la cita", description = "Indica si cada cambio fue del candidato o de la empresa, sin identificar personas")
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
