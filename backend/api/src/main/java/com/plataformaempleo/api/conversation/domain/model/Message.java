package com.plataformaempleo.api.conversation.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensaje de una conversación. Cada conversación pertenece a una postulación y tiene dos lados:
 * la persona candidata y la empresa (cualquier persona de su equipo).
 */
public record Message(UUID id, UUID applicationId, UUID senderId, boolean fromCandidate, String body, Instant sentAt) {

    /** VALOR SUPUESTO: largo máximo de un mensaje, en caracteres. */
    public static final int MAX_LENGTH = 2000;

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    /** Crea un mensaje nuevo. Quita los espacios de los extremos y exige entre 1 y {@link #MAX_LENGTH} caracteres. */
    public static Message write(UUID applicationId, UUID senderId, boolean fromCandidate, String text, Instant now) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            throw new DomainException("message.body.empty", "Escribe un mensaje");
        }
        if (body.codePointCount(0, body.length()) > MAX_LENGTH) {
            throw new DomainException("message.body.too-long",
                    "El mensaje puede tener como máximo " + MAX_LENGTH + " caracteres");
        }
        return new Message(UUID.randomUUID(), applicationId, senderId, fromCandidate, body, now);
    }
}
