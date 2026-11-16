package com.plataformaempleo.api.conversation.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Una conversación en la bandeja de mensajes: de qué postulación es, con quién y su último mensaje.
 *
 * @param candidateEmail solo para la empresa y solo si la persona mantiene el permiso de compartir sus datos
 * @param unread         mensajes del otro lado posteriores a la última lectura de quien consulta
 */
public record ConversationSummary(UUID applicationId, String vacancyTitle, String companyName, String candidateEmail,
                                  String lastBody, Instant lastSentAt, boolean lastFromCandidate, int unread) {
}
