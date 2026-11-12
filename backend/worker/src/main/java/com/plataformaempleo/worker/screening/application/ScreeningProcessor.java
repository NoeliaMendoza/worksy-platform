package com.plataformaempleo.worker.screening.application;

import com.plataformaempleo.worker.screening.domain.model.Decision;
import com.plataformaempleo.worker.screening.domain.model.ScreeningContext;
import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.plataformaempleo.worker.screening.domain.model.ScreeningOutcome;
import com.plataformaempleo.worker.screening.domain.port.out.CvFiles;
import com.plataformaempleo.worker.screening.domain.port.out.DecisionProvider;
import com.plataformaempleo.worker.screening.domain.port.out.ProcessingLock;
import com.plataformaempleo.worker.screening.domain.port.out.ScreeningStore;
import com.plataformaempleo.worker.screening.domain.port.out.TextExtractor;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Procesa un mensaje {@code screening.requested} (sección 5 del diseño, pasos 4 a 8):
 * descarta duplicados, toma un bloqueo, extrae el texto del CV, pregunta al proveedor de decisión y
 * guarda el resultado. Los registros solo llevan ids: nunca texto del CV ni datos de la persona.
 */
public class ScreeningProcessor {

    /** Bloqueo de 60 s (sección 5 del diseño, paso 4). */
    public static final Duration LOCK_TTL = Duration.ofSeconds(60);
    /** VALOR SUPUESTO: límite de texto enviado al modelo; el diseño prevé dividir por secciones más adelante. */
    public static final int MAX_TEXT_CHARS = 100_000;

    public enum Result { DONE, DUPLICATE, BUSY, FAILED }

    private static final Logger log = LoggerFactory.getLogger(ScreeningProcessor.class);

    private final ScreeningStore store;
    private final CvFiles files;
    private final TextExtractor extractor;
    private final DecisionProvider decisions;
    private final ProcessingLock lock;
    private final double reviewThreshold;

    public ScreeningProcessor(ScreeningStore store, CvFiles files, TextExtractor extractor, DecisionProvider decisions,
                              ProcessingLock lock, double reviewThreshold) {
        this.store = Objects.requireNonNull(store);
        this.files = Objects.requireNonNull(files);
        this.extractor = Objects.requireNonNull(extractor);
        this.decisions = Objects.requireNonNull(decisions);
        this.lock = Objects.requireNonNull(lock);
        this.reviewThreshold = reviewThreshold;
    }

    /**
     * @throws ScreeningFailure reintentable si un servicio externo sigue fallando tras los reintentos;
     *                          la solicitud ya quedó FAILED y el mensaje debe ir a la cola de fallidos
     */
    public Result process(String messageId, UUID requestId) {
        if (store.alreadyProcessed(messageId)) {
            return Result.DUPLICATE;
        }
        String lockKey = "screening:" + requestId;
        if (!lock.tryAcquire(lockKey, LOCK_TTL)) {
            return Result.BUSY;
        }
        try {
            ScreeningContext context = store.load(requestId).orElse(null);
            if (context == null || context.isFinished()) {
                // La solicitud desapareció (postulación retirada) o ya se resolvió con otro mensaje.
                store.markMessageProcessed(messageId);
                return Result.DUPLICATE;
            }
            store.markProcessing(requestId);
            try {
                ScreeningOutcome outcome = analyze(context);
                store.complete(context, messageId, outcome);
                log.info("Preselección {} resuelta: {} (modelo {})", requestId,
                        outcome.needsReview() ? "NEEDS_REVIEW" : "COMPLETED", outcome.modelVersion());
                return Result.DONE;
            } catch (ScreeningFailure failure) {
                store.fail(requestId, messageId);
                log.warn("Preselección {} fallida ({}): {}", requestId,
                        failure.retryable() ? "pasajero" : "definitivo", failure.getMessage());
                if (failure.retryable()) {
                    throw failure;
                }
                return Result.FAILED;
            }
        } finally {
            lock.release(lockKey);
        }
    }

    private ScreeningOutcome analyze(ScreeningContext context) {
        String text = extractor.extract(files.read(context.cvStorageKey())).strip();
        if (text.isEmpty()) {
            throw ScreeningFailure.permanent("El PDF no tiene texto extraíble (¿escaneado?)");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        Decision decision = decisions.decide(context.provider(), text, context.jobDescription(), context.criteria());
        try {
            return ScreeningOutcome.of(context.criteria(), decision, reviewThreshold);
        } catch (IllegalArgumentException e) {
            throw ScreeningFailure.permanent("Respuesta del proveedor incompleta: " + e.getMessage());
        }
    }
}
