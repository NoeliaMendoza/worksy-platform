package com.plataformaempleo.api.interview.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Máquina de estados de la cita, sin base de datos. Datos sintéticos. */
class InterviewTest {
    private final Instant now = Instant.parse("2026-11-16T15:00:00Z");
    private Interview invitation() {
        return Interview.schedule(UUID.randomUUID(), UUID.randomUUID(), "Entrevista sintética", now.plus(1, ChronoUnit.DAYS),
                30, "America/Guayaquil", Interview.Mode.ONLINE, "https://meet.example.test/demo", "Mensaje público sintético", now);
    }
    private Interview edit(Interview from, Instant start, String place, String note) {
        return from.update(from.title(), start, from.durationMinutes(), from.zoneId(), from.mode(), place, note, now);
    }

    @Test
    void cambiarHoraOLugarPideConfirmarDeNuevoPeroCambiarElMensajeNo() {
        Interview confirmed = invitation().respond(Interview.Status.CONFIRMED, now);
        Interview moved = edit(confirmed, confirmed.startsAt().plus(1, ChronoUnit.HOURS), confirmed.place(), confirmed.note());
        Interview relocated = edit(confirmed, confirmed.startsAt(), "https://meet.example.test/otra", confirmed.note());
        Interview noteOnly = edit(confirmed, confirmed.startsAt(), confirmed.place(), "Otro mensaje sintético");
        assertThat(moved.status()).isEqualTo(Interview.Status.INVITED);
        assertThat(relocated.status()).isEqualTo(Interview.Status.INVITED);
        assertThat(noteOnly.status()).isEqualTo(Interview.Status.CONFIRMED);
        assertThat(noteOnly.version()).isEqualTo(confirmed.version() + 1);
        assertThat(edit(confirmed, confirmed.startsAt(), confirmed.place(), confirmed.note())).isSameAs(confirmed);
        assertThat(moved.toString()).doesNotContain(confirmed.place(), confirmed.note());
    }

    @Test
    void repetirLaMismaRespuestaNoCambiaNadaYAvisarQueNoPuedeValeTrasConfirmar() {
        Interview confirmed = invitation().respond(Interview.Status.CONFIRMED, now);
        assertThat(confirmed.respond(Interview.Status.CONFIRMED, now)).isSameAs(confirmed);
        Interview declined = confirmed.respond(Interview.Status.DECLINED, now);
        assertThat(declined.status()).isEqualTo(Interview.Status.DECLINED);
        assertThat(declined.respond(Interview.Status.DECLINED, now)).isSameAs(declined);
        assertThatThrownBy(() -> declined.respond(Interview.Status.CONFIRMED, now)).hasMessageContaining("no admite");
        assertThatThrownBy(() -> invitation().respond(Interview.Status.CANCELLED, now)).hasMessageContaining("confirmar");
        Interview first = invitation();
        assertThatThrownBy(() -> first.respond(Interview.Status.CONFIRMED, first.startsAt())).hasMessageContaining("comenzó");
    }

    @Test
    void trasNoPoderAsistirLaEmpresaPuedeProponerOtraHoraOCancelarConMotivo() {
        Interview declined = invitation().respond(Interview.Status.DECLINED, now);
        Interview again = edit(declined, declined.startsAt().plus(2, ChronoUnit.DAYS), declined.place(), declined.note());
        assertThat(again.status()).isEqualTo(Interview.Status.INVITED);
        assertThatThrownBy(() -> declined.cancel(" ", now)).hasMessageContaining("obligatorios");
        assertThatThrownBy(() -> declined.cancel("no", now)).hasMessageContaining("motivo");
        Interview cancelled = declined.cancel("La vacante se pausó", now);
        assertThat(cancelled.status()).isEqualTo(Interview.Status.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("La vacante se pausó");
        assertThatThrownBy(() -> cancelled.respond(Interview.Status.CONFIRMED, now)).hasMessageContaining("no admite");
        assertThatThrownBy(() -> cancelled.cancel("Otra vez", now)).hasMessageContaining("vigente");
    }

    @Test
    void elResultadoSoloDespuesDeLaHoraYSeCorrigeDurante24Horas() {
        Interview confirmed = invitation().respond(Interview.Status.CONFIRMED, now);
        Instant during = confirmed.startsAt().plus(10, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> confirmed.recordOutcome(Interview.Status.COMPLETED, now)).hasMessageContaining("empiece");
        Interview done = confirmed.recordOutcome(Interview.Status.COMPLETED, during);
        assertThat(done.status()).isEqualTo(Interview.Status.COMPLETED);
        assertThat(done.recordOutcome(Interview.Status.COMPLETED, during)).isSameAs(done);
        assertThat(done.recordOutcome(Interview.Status.NO_SHOW, during.plus(1, ChronoUnit.HOURS)).status()).isEqualTo(Interview.Status.NO_SHOW);
        assertThatThrownBy(() -> done.recordOutcome(Interview.Status.NO_SHOW, during.plus(25, ChronoUnit.HOURS))).hasMessageContaining("Ya no");
        Interview unanswered = invitation();
        Instant after = unanswered.startsAt().plus(1, ChronoUnit.HOURS);
        assertThat(unanswered.phase(after)).isEqualTo(Interview.Phase.EXPIRED);
        assertThatThrownBy(() -> unanswered.recordOutcome(Interview.Status.NO_SHOW, after)).hasMessageContaining("Ya no");
        assertThat(unanswered.recordOutcome(Interview.Status.COMPLETED, after).status()).isEqualTo(Interview.Status.COMPLETED);
        assertThat(confirmed.phase(during)).isEqualTo(Interview.Phase.AWAITING_OUTCOME);
        assertThat(done.phase(during)).isEqualTo(Interview.Phase.CLOSED);
        assertThat(confirmed.phase(now)).isEqualTo(Interview.Phase.ACTIVE);
    }

    @Test
    void laPropuestaDeOtraHoraValidaHorariosYAlAceptarlaQuedaConfirmada() {
        Interview first = invitation();
        UUID candidate = UUID.randomUUID();
        Instant a = first.startsAt().plus(1, ChronoUnit.DAYS);
        Instant b = first.startsAt().plus(2, ChronoUnit.DAYS);
        ChangeRequest request = ChangeRequest.open(UUID.randomUUID(), first, candidate, "Tengo un examen", List.of(a, b), now);
        assertThat(request.slots()).containsExactly(a, b);
        assertThat(first.acceptProposedStart(request.slot(1), now).status()).isEqualTo(Interview.Status.CONFIRMED);
        assertThatThrownBy(() -> request.slot(2)).hasMessageContaining("horarios propuestos");
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), first, candidate, "Motivo", List.of(a, a), now)).hasMessageContaining("repiten");
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), first, candidate, "Motivo", List.of(first.startsAt()), now)).hasMessageContaining("distintos");
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), first, candidate, "Motivo", List.of(now.minusSeconds(60)), now)).hasMessageContaining("futura");
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), first, candidate, "Motivo", List.of(a, b, a.plusSeconds(3600), b.plusSeconds(3600)), now)).hasMessageContaining("1 a 3");
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), first, candidate, "", List.of(a), now)).hasMessageContaining("obligatorios");
        Interview declined = first.respond(Interview.Status.DECLINED, now);
        assertThatThrownBy(() -> ChangeRequest.open(UUID.randomUUID(), declined, candidate, "Motivo", List.of(a), now)).hasMessageContaining("vigente");
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
