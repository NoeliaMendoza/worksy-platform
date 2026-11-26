package com.plataformaempleo.realtime.call.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plataformaempleo.realtime.call.domain.model.CallGrant;
import com.plataformaempleo.realtime.call.domain.model.CallSignal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reglas de la señalización sin levantar el servicio. Datos sintéticos. */
class CallSignalServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-26T15:00:00Z");
    private final UUID ana = UUID.randomUUID();
    private final UUID luis = UUID.randomUUID();
    private final UUID intrusa = UUID.randomUUID();
    private final CallGrant anaGrant = new CallGrant(ana, UUID.randomUUID(), UUID.randomUUID(), "CANDIDATE", List.of(luis), NOW.plusSeconds(3600));
    private final List<CallSignal> sent = new ArrayList<>();
    private final CallSignalService service = new CallSignalService(token -> {
        if (token.equals("ana")) return anaGrant;
        if (token.equals("vencido")) throw new IllegalArgumentException("vencido");
        throw new IllegalArgumentException("firma");
    }, (grant, signal) -> sent.add(signal), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void reenviaSoloAlDestinatarioAutorizadoSinTocarLosDatos() {
        var offer = Map.of("type", "offer", "sdp", "v=0 sintético");
        assertThat(service.relay(ana, "ana", luis, "offer", offer)).isEqualTo(anaGrant);
        assertThat(sent).containsExactly(new CallSignal(ana, luis, CallSignal.Kind.OFFER, offer));
    }

    @Test
    void rechazaTokensAjenosOVencidosDestinatariosFueraDeLaSalaYTiposDesconocidos() {
        assertThatThrownBy(() -> service.relay(intrusa, "ana", luis, "offer", null)).hasMessageContaining("otra persona");
        assertThatThrownBy(() -> service.relay(ana, "vencido", luis, "offer", null)).hasMessageContaining("no es válido");
        assertThatThrownBy(() -> service.relay(ana, "ana", intrusa, "offer", null)).hasMessageContaining("no está en esta sala");
        assertThatThrownBy(() -> service.relay(ana, "ana", null, "offer", null)).hasMessageContaining("no está en esta sala");
        assertThatThrownBy(() -> service.relay(ana, "ana", luis, "grabar", null)).hasMessageContaining("desconocido");
        assertThatThrownBy(() -> service.relay(null, "ana", luis, "offer", null)).hasMessageContaining("Conéctate");
        assertThat(sent).isEmpty();
    }

    @Test
    void limitaLaFrecuenciaDeSenalesPorPersona() {
        for (int i = 0; i < CallSignalService.MAX_SIGNALS; i++) service.relay(ana, "ana", luis, "ice", Map.of("n", i));
        assertThatThrownBy(() -> service.relay(ana, "ana", luis, "ice", Map.of())).hasMessageContaining("Demasiadas");
        assertThat(sent).hasSize(CallSignalService.MAX_SIGNALS);
    }

    @Test
    void alDesconectarseAvisaALasDemasPersonasDeLaSala() {
        service.disconnected(anaGrant);
        assertThat(sent).singleElement().satisfies(s -> {
            assertThat(s.to()).isEqualTo(luis);
            assertThat(s.kind()).isEqualTo(CallSignal.Kind.BYE);
        });
        sent.clear();
        service.disconnected(new CallGrant(ana, UUID.randomUUID(), UUID.randomUUID(), "CANDIDATE", List.of(luis), NOW.minusSeconds(1)));
        service.disconnected(null);
        assertThat(sent).isEmpty();
    }
}
