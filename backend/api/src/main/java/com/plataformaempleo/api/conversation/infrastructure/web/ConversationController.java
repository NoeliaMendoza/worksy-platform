package com.plataformaempleo.api.conversation.infrastructure.web;

import com.plataformaempleo.api.conversation.application.ConversationService;
import com.plataformaempleo.api.conversation.application.ConversationService.ConversationPage;
import com.plataformaempleo.api.conversation.domain.model.ConversationSummary;
import com.plataformaempleo.api.conversation.domain.model.Message;
import com.plataformaempleo.api.shared.infrastructure.web.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada REST de las conversaciones.
 * Contrato nuevo aprobado por el usuario el 2026-11-15 (fase F3, no figuraba en la sección 6 del diseño técnico).
 * El tiempo real solo avisa de que hay mensajes nuevos; leerlos y escribirlos siempre pasa por aquí.
 */
@RestController
@Tag(name = "Mensajes", description = "Conversaciones entre la empresa y la persona candidata, una por postulación")
class ConversationController {

    @Schema(name = "SendMessageRequest")
    record SendMessageRequest(@Schema(description = "Texto plano, de 1 a 2000 caracteres") @NotNull String body) {
    }

    @Schema(name = "Message")
    record MessageResponse(
            UUID id,
            UUID applicationId,
            String body,
            Instant sentAt,
            @Schema(description = "true si lo escribió la persona candidata; false si alguien de la empresa")
            boolean fromCandidate,
            @Schema(description = "true si lo escribió quien consulta") boolean mine) {
        static MessageResponse from(Message m, UUID me) {
            return new MessageResponse(m.id(), m.applicationId(), m.body(), m.sentAt(), m.fromCandidate(),
                    m.senderId().equals(me));
        }
    }

    @Schema(name = "MessagePage", description = "Mensajes en orden cronológico")
    record MessagePageResponse(
            List<MessageResponse> messages,
            @Schema(description = "Hay mensajes anteriores: pedirlos con before = sentAt del primero") boolean hasMore,
            @Schema(description = "Si quien consulta puede escribir ahora. La persona candidata responde cuando la "
                    + "empresa ya escribió; la empresa escribe mientras la persona comparta sus datos.")
            boolean canWrite) {
    }

    @Schema(name = "Conversation", description = "Una conversación en la bandeja de mensajes")
    record ConversationResponse(
            UUID applicationId,
            String vacancyTitle,
            String companyName,
            @Schema(description = "Solo para la empresa y si la persona comparte sus datos") String candidateEmail,
            String lastMessage,
            Instant lastSentAt,
            boolean lastFromCandidate,
            @Schema(description = "Mensajes de otras personas sin leer") int unread) {
        static ConversationResponse from(ConversationSummary s) {
            return new ConversationResponse(s.applicationId(), s.vacancyTitle(), s.companyName(), s.candidateEmail(),
                    s.lastBody(), s.lastSentAt(), s.lastFromCandidate(), s.unread());
        }
    }

    private final ConversationService service;

    ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/conversations")
    @Operation(summary = "Mi bandeja de mensajes",
            description = "Conversaciones con al menos un mensaje, de la más reciente a la más antigua. "
                    + "Candidatos: las de sus postulaciones. Empresa: las de sus vacantes.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "403", description = "La cuenta no es de candidato ni de una empresa")
    List<ConversationResponse> inbox(@AuthenticationPrincipal Jwt jwt) {
        return service.inbox(CurrentActor.from(jwt)).stream().map(ConversationResponse::from).toList();
    }

    @GetMapping("/api/v1/applications/{applicationId}/messages")
    @Operation(summary = "Mensajes de una postulación",
            description = "Hasta 50 por página. Solo la persona que se postuló y el equipo de la empresa.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "404", description = "La postulación no existe o no participas en ella")
    MessagePageResponse thread(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId,
                               @Parameter(description = "Devuelve los mensajes anteriores a este momento")
                               @RequestParam(required = false) Instant before) {
        var actor = CurrentActor.from(jwt);
        ConversationPage page = service.thread(actor, applicationId, before);
        return new MessagePageResponse(
                page.messages().stream().map(m -> MessageResponse.from(m, actor.userId())).toList(),
                page.hasMore(), page.canWrite());
    }

    @PostMapping("/api/v1/applications/{applicationId}/messages")
    @Operation(summary = "Enviar un mensaje",
            description = "La empresa escribe primero; la persona candidata responde.",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Mensaje enviado")
    @ApiResponse(responseCode = "400", description = "Mensaje vacío o demasiado largo, o la empresa aún no escribió")
    @ApiResponse(responseCode = "403", description = "La persona retiró el permiso de compartir sus datos")
    @ApiResponse(responseCode = "404", description = "La postulación no existe o no participas en ella")
    ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId,
                                         @Valid @RequestBody SendMessageRequest body) {
        var actor = CurrentActor.from(jwt);
        Message sent = service.send(actor, applicationId, body.body());
        return ResponseEntity.created(URI.create("/api/v1/applications/" + applicationId + "/messages"))
                .body(MessageResponse.from(sent, actor.userId()));
    }

    @PostMapping("/api/v1/applications/{applicationId}/messages/read")
    @Operation(summary = "Marcar la conversación como leída", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Marcada como leída hasta ahora")
    @ApiResponse(responseCode = "404", description = "La postulación no existe o no participas en ella")
    ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
        service.markRead(CurrentActor.from(jwt), applicationId);
        return ResponseEntity.noContent().build();
    }
}
