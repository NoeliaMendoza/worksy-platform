package com.plataformaempleo.worker.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.worker.screening.domain.model.Criterion;
import com.plataformaempleo.worker.screening.domain.model.Decision;
import com.plataformaempleo.worker.screening.domain.model.ScreeningContext;
import com.plataformaempleo.worker.screening.domain.model.ScreeningFailure;
import com.plataformaempleo.worker.screening.domain.model.ScreeningOutcome;
import com.plataformaempleo.worker.screening.domain.port.out.ProcessingLock;
import com.plataformaempleo.worker.screening.domain.port.out.ScreeningStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Reglas del procesamiento con adaptadores en memoria (sin Spring ni Docker). */
class ScreeningProcessorTest {

    private final UUID requestId = UUID.randomUUID();
    private final Criterion criterion = new Criterion("q1", "Spring Boot", "¿Tiene experiencia con Spring Boot?",
            Criterion.Kind.YES_NO, List.of("Sí", "No"));

    private final FakeStore store = new FakeStore();
    private final FakeLock lock = new FakeLock();
    private final AtomicInteger inferenceCalls = new AtomicInteger();
    private String extractedText = "Desarrolladora con experiencia en Spring Boot";
    private RuntimeException inferenceError;
    private double probability = 0.9;

    private final ScreeningProcessor processor = new ScreeningProcessor(store, key -> new byte[] {1},
            pdf -> extractedText,
            (provider, text, description, criteria) -> {
                inferenceCalls.incrementAndGet();
                if (inferenceError != null) {
                    throw inferenceError;
                }
                return new Decision(Map.of("q1", new Decision.Answer("Sí", probability)), "baseline-bow-1", 5);
            },
            lock, 0.6);

    @Test
    void procesaYGuardaElResultadoUnaSolaVez() {
        store.context = context(ScreeningContext.Status.PENDING);

        assertThat(processor.process("m-1", requestId)).isEqualTo(ScreeningProcessor.Result.DONE);
        assertThat(store.completed).hasSize(1);
        assertThat(store.completed.get(0).needsReview()).isFalse();
        assertThat(store.processing).isEqualTo(1);
        assertThat(lock.held).isEmpty();

        // El mismo mensaje otra vez (reentrega de RabbitMQ): no se vuelve a llamar al modelo.
        assertThat(processor.process("m-1", requestId)).isEqualTo(ScreeningProcessor.Result.DUPLICATE);
        assertThat(inferenceCalls).hasValue(1);
    }

    @Test
    void conConfianzaBajaQuedaParaRevision() {
        store.context = context(ScreeningContext.Status.PENDING);
        probability = 0.52;

        processor.process("m-1", requestId);

        assertThat(store.completed.get(0).needsReview()).isTrue();
    }

    @Test
    void siOtraReplicaTieneElBloqueoNoHaceNada() {
        store.context = context(ScreeningContext.Status.PENDING);
        lock.held.add("screening:" + requestId);

        assertThat(processor.process("m-1", requestId)).isEqualTo(ScreeningProcessor.Result.BUSY);
        assertThat(inferenceCalls).hasValue(0);
        assertThat(store.processed).isEmpty();
    }

    @Test
    void unaSolicitudYaResueltaConOtroMensajeSeDescarta() {
        store.context = context(ScreeningContext.Status.COMPLETED);

        assertThat(processor.process("m-2", requestId)).isEqualTo(ScreeningProcessor.Result.DUPLICATE);
        assertThat(store.processed).containsExactly("m-2");
        assertThat(inferenceCalls).hasValue(0);
    }

    @Test
    void unPdfSinTextoFallaSinReintentar() {
        store.context = context(ScreeningContext.Status.PENDING);
        extractedText = "   ";

        assertThat(processor.process("m-1", requestId)).isEqualTo(ScreeningProcessor.Result.FAILED);
        assertThat(store.failed).containsExactly(requestId);
        assertThat(inferenceCalls).hasValue(0);
        assertThat(lock.held).isEmpty();
    }

    @Test
    void unFalloPasajeroDejaFailedYSePropagaParaLaColaDeFallidos() {
        store.context = context(ScreeningContext.Status.PENDING);
        inferenceError = ScreeningFailure.transientFailure("inferencia caída", null);

        assertThatThrownBy(() -> processor.process("m-1", requestId)).isInstanceOf(ScreeningFailure.class);
        assertThat(store.failed).containsExactly(requestId);
        assertThat(lock.held).isEmpty();
    }

    private ScreeningContext context(ScreeningContext.Status status) {
        return new ScreeningContext(requestId, UUID.randomUUID(), status, "baseline", "cv/x.pdf", "Backend Java",
                List.of(criterion));
    }

    // ---- adaptadores en memoria ----

    private static final class FakeLock implements ProcessingLock {
        final Set<String> held = new HashSet<>();

        @Override
        public boolean tryAcquire(String key, Duration ttl) {
            return held.add(key);
        }

        @Override
        public void release(String key) {
            held.remove(key);
        }
    }

    private static final class FakeStore implements ScreeningStore {
        ScreeningContext context;
        final Set<String> processed = new HashSet<>();
        final List<ScreeningOutcome> completed = new ArrayList<>();
        final List<UUID> failed = new ArrayList<>();
        int processing;

        @Override
        public boolean alreadyProcessed(String messageId) {
            return processed.contains(messageId);
        }

        @Override
        public Optional<ScreeningContext> load(UUID requestId) {
            return Optional.ofNullable(context);
        }

        @Override
        public void markProcessing(UUID requestId) {
            processing++;
        }

        @Override
        public void complete(ScreeningContext ctx, String messageId, ScreeningOutcome outcome) {
            processed.add(messageId);
            completed.add(outcome);
        }

        @Override
        public void fail(UUID requestId, String messageId) {
            processed.add(messageId);
            failed.add(requestId);
        }

        @Override
        public void markMessageProcessed(String messageId) {
            processed.add(messageId);
        }
    }
}
