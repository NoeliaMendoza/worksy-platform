package com.plataformaempleo.realtime.call.application;

import com.plataformaempleo.realtime.call.domain.model.CallGrant;
import com.plataformaempleo.realtime.call.domain.model.CallSignal;
import com.plataformaempleo.realtime.call.domain.port.out.CallGrantVerifier;
import com.plataformaempleo.realtime.call.domain.port.out.SignalPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reenvía señales WebRTC solo entre personas autorizadas de la misma sala. Rechaza (sin reenviar) cuando el token de sala
 * no es válido, pertenece a otra persona, el destinatario no está en la sala o se superó la frecuencia máxima.
 */
public class CallSignalService {

    /** VALORES SUPUESTOS: al conectar se intercambian decenas de candidatos ICE; 200 señales cada 10 s sobra. */
    static final int MAX_SIGNALS = 200;
    static final Duration WINDOW = Duration.ofSeconds(10);

    private final CallGrantVerifier verifier;
    private final SignalPublisher publisher;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public CallSignalService(CallGrantVerifier verifier, SignalPublisher publisher, Clock clock) {
        this.verifier = Objects.requireNonNull(verifier);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Valida y reenvía una señal. Devuelve el permiso para recordar la sala de esta conexión (y avisar al desconectar). */
    public CallGrant relay(UUID sender, String roomToken, UUID to, String kind, Object data) {
        if (sender == null) throw new SignalRejected("Conéctate antes de enviar señales");
        CallGrant grant;
        try {
            grant = verifier.verify(roomToken);
        } catch (IllegalArgumentException e) {
            throw new SignalRejected("El acceso a la sala no es válido o venció");
        }
        if (!grant.userId().equals(sender)) throw new SignalRejected("El acceso a la sala es de otra persona");
        if (to == null || !grant.allows(to)) throw new SignalRejected("Esa persona no está en esta sala");
        CallSignal.Kind type;
        try {
            type = CallSignal.Kind.parse(kind);
        } catch (IllegalArgumentException e) {
            throw new SignalRejected("Tipo de señal desconocido");
        }
        throttle(sender);
        publisher.publish(grant, new CallSignal(sender, to, type, data));
        return grant;
    }

    /** La conexión se cortó: avisa a las otras personas de la sala para que no esperen a quien ya no está. */
    public void disconnected(CallGrant grant) {
        if (grant == null || grant.expired(clock.instant())) return;
        for (UUID peer : grant.peers()) {
            publisher.publish(grant, new CallSignal(grant.userId(), peer, CallSignal.Kind.BYE, Map.of("reason", "disconnected")));
        }
        recent.remove(grant.userId());
    }

    private void throttle(UUID sender) {
        Instant now = clock.instant();
        Deque<Instant> times = recent.computeIfAbsent(sender, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) times.pollFirst();
            if (times.size() >= MAX_SIGNALS) throw new SignalRejected("Demasiadas señales seguidas");
            times.addLast(now);
        }
    }

    /** Señal rechazada: no se reenvía. No incluye datos de la señal. */
    public static class SignalRejected extends RuntimeException {
        public SignalRejected(String message) {
            super(message);
        }
    }
}
