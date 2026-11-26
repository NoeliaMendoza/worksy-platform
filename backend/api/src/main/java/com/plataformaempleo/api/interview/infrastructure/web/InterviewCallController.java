package com.plataformaempleo.api.interview.infrastructure.web;

import com.plataformaempleo.api.interview.application.InterviewCallService;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interviews/{id}/call")
@Tag(name = "Videollamada", description = "Sala de videollamada de una entrevista: acceso, asistencia y señalización (sin grabación)")
@SecurityRequirement(name = "bearer")
class InterviewCallController {
    private final InterviewCallService service;
    InterviewCallController(InterviewCallService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Disponibilidad de la sala", description = "Si se puede entrar ahora, desde y hasta cuándo, y quién está dentro")
    InterviewCallService.Status status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.status(CurrentActor.from(jwt), id);
    }

    @PostMapping("/join")
    @Operation(summary = "Entrar a la sala",
            description = "Solo la persona candidata de la postulación o alguien de la empresa a cargo de la cita, dentro del horario. "
                    + "Devuelve el token de sala para la señalización por WebSocket y los servidores ICE")
    ResponseEntity<InterviewCallService.Join> join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.join(CurrentActor.from(jwt), id));
    }

    @PostMapping("/leave")
    @Operation(summary = "Salir de la sala")
    ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.leave(CurrentActor.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/end")
    @Operation(summary = "Terminar la llamada para todos (entrevistador)")
    ResponseEntity<Void> end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.end(CurrentActor.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
