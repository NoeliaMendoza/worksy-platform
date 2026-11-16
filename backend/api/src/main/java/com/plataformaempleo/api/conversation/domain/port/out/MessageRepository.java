package com.plataformaempleo.api.conversation.domain.port.out;

import com.plataformaempleo.api.conversation.domain.model.ConversationSummary;
import com.plataformaempleo.api.conversation.domain.model.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Puerto de salida de las conversaciones. */
public interface MessageRepository {

    Message save(Message message);

    /**
     * Mensajes de una postulación, del más reciente al más antiguo.
     *
     * @param before si no es null, solo los enviados antes de ese momento (para cargar más hacia atrás)
     */
    List<Message> findByApplication(UUID applicationId, Instant before, int limit);

    /** Si alguien de la empresa ya escribió en esta postulación (la persona candidata solo puede responder). */
    boolean hasCompanyMessage(UUID applicationId);

    /** Marca la conversación como leída por esa persona hasta ese momento (nunca retrocede). */
    void markRead(UUID applicationId, UUID userId, Instant at);

    /** Conversaciones con al menos un mensaje en las postulaciones de la persona candidata. */
    List<ConversationSummary> summariesForCandidate(UUID candidateId);

    /** Conversaciones con al menos un mensaje en las vacantes de la empresa, con los no leídos de esa persona. */
    List<ConversationSummary> summariesForCompany(UUID companyId, UUID userId);
}
