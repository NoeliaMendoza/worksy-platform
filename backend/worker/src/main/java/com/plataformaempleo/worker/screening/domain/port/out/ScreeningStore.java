package com.plataformaempleo.worker.screening.domain.port.out;

import com.plataformaempleo.worker.screening.domain.model.ScreeningContext;
import com.plataformaempleo.worker.screening.domain.model.ScreeningOutcome;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida hacia la base de datos compartida con la API. */
public interface ScreeningStore {

    boolean alreadyProcessed(String messageId);

    Optional<ScreeningContext> load(UUID requestId);

    /** Pasa a PROCESSING y suma un intento. */
    void markProcessing(UUID requestId);

    /**
     * En UNA transacción: guarda las respuestas, actualiza la solicitud, registra el mensaje como
     * procesado y deja el evento {@code screening.completed} en el outbox.
     */
    void complete(ScreeningContext context, String messageId, ScreeningOutcome outcome);

    /** Deja la solicitud FAILED (si aún no estaba resuelta) y registra el mensaje como procesado. */
    void fail(UUID requestId, String messageId);

    /** Registra el mensaje como procesado sin tocar la solicitud (duplicado de algo ya resuelto). */
    void markMessageProcessed(String messageId);
}
