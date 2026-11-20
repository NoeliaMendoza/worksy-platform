package com.plataformaempleo.api.interview.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewTest {
    private final Instant now = Instant.parse("2026-11-16T15:00:00Z");
    private Interview invitation() {
        return Interview.schedule(UUID.randomUUID(), UUID.randomUUID(), "Entrevista sintética", now.plus(1, ChronoUnit.DAYS),
                30, "America/Guayaquil", Interview.Mode.ONLINE, "https://meet.example.test/demo", "Mensaje público sintético", now);
    }

    @Test
    void confirmarYReprogramarExigeUnaConfirmacionNueva() {
        Interview first = invitation();
        Interview confirmed = first.respond(Interview.Status.CONFIRMED, now);
        Interview changed = confirmed.update(first.title(), first.startsAt().plus(1, ChronoUnit.HOURS), 45,
                first.zoneId(), first.mode(), first.place(), first.note(), now);
        assertThat(confirmed.status()).isEqualTo(Interview.Status.CONFIRMED);
        assertThat(changed.status()).isEqualTo(Interview.Status.INVITED);
        assertThat(changed.version()).isEqualTo(2);
        assertThat(changed.toString()).doesNotContain(first.place(), first.note());
    }

    @Test
    void rechazarOCancelarEsTerminalYNoAdmiteRespuestasFueraDeTiempo() {
        Interview first = invitation();
        assertThatThrownBy(() -> first.respond(Interview.Status.CANCELLED, now)).hasMessageContaining("confirmar o rechazar");
        assertThatThrownBy(() -> first.respond(Interview.Status.CONFIRMED, first.startsAt())).hasMessageContaining("no admite");
        assertThatThrownBy(() -> first.respond(Interview.Status.DECLINED, now).cancel(now)).hasMessageContaining("activa");
        assertThatThrownBy(() -> first.cancel(now).respond(Interview.Status.CONFIRMED, now)).hasMessageContaining("no admite");
    }

    @Test
    void validaFechaDuracionZonaYEnlaceSinConsultarServiciosExternos() {
        Interview first = invitation();
        for (String link : new String[] {"javascript:alert(1)", "http://meet.example.test/demo", "https://user:pass@meet.example.test"}) {
            assertThatThrownBy(() -> Interview.schedule(first.id(), first.applicationId(), first.title(), first.startsAt(), 30,
                    first.zoneId(), first.mode(), link, null, now)).hasMessageContaining("HTTPS");
        }
        assertThatThrownBy(() -> Interview.schedule(first.id(), first.applicationId(), first.title(), now, 30,
                first.zoneId(), first.mode(), first.place(), null, now)).hasMessageContaining("futura");
        assertThatThrownBy(() -> Interview.schedule(first.id(), first.applicationId(), first.title(), first.startsAt(), 5,
                first.zoneId(), first.mode(), first.place(), null, now)).hasMessageContaining("duración");
        assertThatThrownBy(() -> Interview.schedule(first.id(), first.applicationId(), first.title(), first.startsAt(), 30,
                "zona/inventada", first.mode(), first.place(), null, now)).hasMessageContaining("zona horaria");
    }
}
