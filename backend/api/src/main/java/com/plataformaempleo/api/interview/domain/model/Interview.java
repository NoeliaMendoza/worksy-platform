package com.plataformaempleo.api.interview.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Cita entre la empresa y el candidato. Estados guardados:
 * <ul>
 *   <li>{@code INVITED}: por confirmar (recién agendada o con hora, lugar o modalidad cambiados).</li>
 *   <li>{@code CONFIRMED}: el candidato aceptó la hora actual.</li>
 *   <li>{@code DECLINED}: el candidato avisó que no puede; la empresa puede proponer otra hora o cancelar.</li>
 *   <li>{@code CANCELLED}: la empresa la canceló (motivo obligatorio). Terminal.</li>
 *   <li>{@code COMPLETED} / {@code NO_SHOW}: resultado registrado por la empresa después de la hora; corregible 24 h.</li>
 * </ul>
 * Registrar el resultado no cambia la etapa ni decide el proceso: la decisión sigue siendo humana y aparte.
 */
public record Interview(UUID id, UUID applicationId, int version, String title, Instant startsAt,
                        int durationMinutes, String zoneId, Mode mode, String place, String note,
                        Status status, String cancelReason, Instant outcomeAt, Instant updatedAt) {
    /** ONLINE: enlace externo (como hasta ahora). VIDEO: sala de videollamada de Worksy. IN_PERSON: dirección. */
    public enum Mode { ONLINE, IN_PERSON, VIDEO }
    /** Texto que se guarda como lugar de una cita en la sala de Worksy (no hay enlace que pegar). */
    public static final String VIDEO_PLACE = "Videollamada en Worksy";
    public enum Status { INVITED, CONFIRMED, DECLINED, CANCELLED, COMPLETED, NO_SHOW }
    public enum Change {
        SCHEDULED, UPDATED, CONFIRMED, DECLINED, CANCELLED, CHANGE_REQUESTED, CHANGE_WITHDRAWN, CHANGE_ACCEPTED,
        CHANGE_REJECTED, COMPLETED, NO_SHOW
    }
    public enum View { upcoming, pending, history, cancelled, updates, range }

    /**
     * Situación calculada (no se guarda): {@code EXPIRED} = invitación sin respuesta cuya hora ya pasó;
     * {@code AWAITING_OUTCOME} = confirmada, ya empezó y falta registrar el resultado.
     */
    public enum Phase { ACTIVE, EXPIRED, AWAITING_OUTCOME, CLOSED }

    /** Margen para corregir el resultado registrado. */
    public static final Duration OUTCOME_CORRECTION = Duration.ofHours(24);

    public static Interview schedule(UUID id, UUID applicationId, String title, Instant startsAt, int duration,
                                     String zoneId, Mode mode, String place, String note, Instant now) {
        if (id == null || applicationId == null || startsAt == null || mode == null) throw invalid("Faltan datos de la entrevista");
        startsAt = requireFutureStart(startsAt, now);
        if (duration < 15 || duration > 180) throw invalid("La duración debe ser de 15 a 180 minutos");
        try { ZoneId.of(zoneId); } catch (DateTimeException | NullPointerException e) { throw invalid("La zona horaria no es válida"); }
        title = clean(title, 120, true);
        place = mode == Mode.VIDEO ? VIDEO_PLACE : clean(place, 500, true);
        note = clean(note, 1000, false);
        if (mode == Mode.ONLINE) {
            try {
                URI uri = URI.create(place);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getPort() > 65535) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw invalid("La reunión online necesita un enlace HTTPS válido sin credenciales"); }
        }
        return new Interview(id, applicationId, 0, title, startsAt, duration, zoneId, mode, place, note, Status.INVITED,
                null, null, now);
    }

    /** Instante válido para una cita: futuro, dentro de 365 días y con precisión de microsegundos (como PostgreSQL). */
    public static Instant requireFutureStart(Instant startsAt, Instant now) {
        if (startsAt == null) throw invalid("Falta la fecha de la entrevista");
        Instant start = startsAt.truncatedTo(ChronoUnit.MICROS);
        if (!start.isAfter(now) || start.isAfter(now.plus(Duration.ofDays(365)))) {
            throw invalid("Elige una fecha futura dentro de los próximos 365 días");
        }
        return start;
    }

    /**
     * Cambia los detalles. Si cambian la hora, la duración, la zona, la modalidad o el lugar, la persona debe confirmar
     * de nuevo; si solo cambian el título o el mensaje, se conserva su respuesta. Sin cambios, devuelve la misma cita.
     */
    public Interview update(String title, Instant startsAt, int duration, String zoneId, Mode mode, String place, String note, Instant now) {
        requireChangeable(now);
        Interview next = schedule(id, applicationId, title, startsAt, duration, zoneId, mode, place, note, now);
        if (sameDetails(next)) return this;
        Status nextStatus = materialChange(next) ? Status.INVITED : status;
        return new Interview(id, applicationId, version + 1, next.title, next.startsAt, duration, zoneId, mode,
                next.place, next.note, nextStatus, null, null, now);
    }

    /** Si el cambio obliga a confirmar de nuevo (hora, duración, zona, modalidad o lugar). */
    public boolean materialChange(Interview other) {
        return !startsAt.equals(other.startsAt) || durationMinutes != other.durationMinutes || !zoneId.equals(other.zoneId)
                || mode != other.mode || !place.equals(other.place);
    }

    /**
     * Respuesta del candidato. Repetir la misma respuesta no cambia nada (devuelve la misma cita).
     * Confirmar solo desde «por confirmar»; avisar que no puede, también desde «confirmada»; siempre antes de la hora.
     */
    public Interview respond(Status response, Instant now) {
        if (response != Status.CONFIRMED && response != Status.DECLINED) throw invalid("Elige confirmar o avisar que no puedes asistir");
        if (status == response) return this;
        if (!startsAt.isAfter(now)) throw invalid("La entrevista ya comenzó y no admite respuestas");
        boolean allowed = response == Status.CONFIRMED ? status == Status.INVITED
                : status == Status.INVITED || status == Status.CONFIRMED;
        if (!allowed) throw invalid("Esta entrevista ya no admite esa respuesta");
        return withStatus(response, null, null, now);
    }

    /** Acepta una hora propuesta por el candidato: queda confirmada, porque la propuso él. */
    public Interview acceptProposedStart(Instant proposedStart, Instant now) {
        requireChangeable(now);
        Instant start = requireFutureStart(proposedStart, now);
        return new Interview(id, applicationId, version + 1, title, start, durationMinutes, zoneId, mode, place, note,
                Status.CONFIRMED, null, null, now);
    }

    public Interview cancel(String reason, Instant now) {
        requireChangeable(now);
        String cleaned = clean(reason, 300, true);
        if (cleaned.length() < 3) throw invalid("Indica el motivo de la cancelación");
        return withStatus(Status.CANCELLED, cleaned, null, now);
    }

    /**
     * Resultado después de la hora. «Realizada» desde confirmada o desde una invitación sin respuesta (pudo hacerse igual);
     * «No asistió» solo si la persona había confirmado. Se puede corregir durante 24 horas.
     */
    public Interview recordOutcome(Status outcome, Instant now) {
        if (outcome != Status.COMPLETED && outcome != Status.NO_SHOW) throw invalid("Elige si la entrevista se realizó o si la persona no asistió");
        if (startsAt.isAfter(now)) throw invalid("Podrás registrar el resultado cuando empiece la entrevista");
        if (status == outcome) return this;
        boolean correcting = (status == Status.COMPLETED || status == Status.NO_SHOW)
                && outcomeAt != null && now.isBefore(outcomeAt.plus(OUTCOME_CORRECTION));
        boolean allowed = correcting || status == Status.CONFIRMED || (status == Status.INVITED && outcome == Status.COMPLETED);
        if (!allowed) throw invalid("Ya no se puede registrar ese resultado");
        return withStatus(outcome, null, now, now);
    }

    /** Nueva versión sin cambiar los datos (p. ej., una propuesta de otra hora): sirve para avisar y ordenar el historial. */
    public Interview touch(Instant now) {
        return new Interview(id, applicationId, version + 1, title, startsAt, durationMinutes, zoneId, mode, place, note,
                status, cancelReason, outcomeAt, now);
    }

    /** Si todavía se puede reprogramar, cancelar o pedir otra hora. */
    public boolean changeable(Instant now) {
        return (status == Status.INVITED || status == Status.CONFIRMED || status == Status.DECLINED) && startsAt.isAfter(now);
    }

    public Phase phase(Instant now) {
        if (status == Status.CANCELLED || status == Status.DECLINED || status == Status.COMPLETED || status == Status.NO_SHOW) {
            return Phase.CLOSED;
        }
        if (startsAt.isAfter(now)) return Phase.ACTIVE;
        return status == Status.INVITED ? Phase.EXPIRED : Phase.AWAITING_OUTCOME;
    }

    public Instant endsAt() {
        return startsAt.plus(Duration.ofMinutes(durationMinutes));
    }

    private Interview withStatus(Status next, String reason, Instant outcome, Instant now) {
        return new Interview(id, applicationId, version + 1, title, startsAt, durationMinutes, zoneId, mode, place, note,
                next, reason, outcome, now);
    }

    private void requireChangeable(Instant now) {
        if (!changeable(now)) throw invalid("Solo puedes cambiar una entrevista vigente que aún no haya comenzado");
    }

    private boolean sameDetails(Interview other) {
        return title.equals(other.title) && !materialChange(other) && Objects.equals(note, other.note);
    }

    /** Misma invitación (para reintentos con el mismo id). */
    public boolean sameInvitation(Interview other) {
        return applicationId.equals(other.applicationId) && sameDetails(other);
    }

    static String clean(String value, int max, boolean required) {
        String cleaned = value == null || value.isBlank() ? null : value.strip();
        if ((required && cleaned == null) || (cleaned != null && cleaned.length() > max)) throw invalid("Revisa los campos obligatorios y su longitud");
        return cleaned;
    }

    static DomainException invalid(String detail) { return new DomainException("interview.invalid", detail); }

    @Override
    public String toString() { return "Interview[id=" + id + ", version=" + version + ", status=" + status + "]"; }
}
