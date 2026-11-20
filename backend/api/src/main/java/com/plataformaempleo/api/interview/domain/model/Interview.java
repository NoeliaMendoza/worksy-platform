package com.plataformaempleo.api.interview.domain.model;

import com.plataformaempleo.common.domain.DomainException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record Interview(UUID id, UUID applicationId, int version, String title, Instant startsAt,
                        int durationMinutes, String zoneId, Mode mode, String place, String note,
                        Status status, Instant updatedAt) {
    public enum Mode { ONLINE, IN_PERSON }
    public enum Status { INVITED, CONFIRMED, DECLINED, CANCELLED }
    public enum Change { SCHEDULED, UPDATED, CONFIRMED, DECLINED, CANCELLED }
    public enum View { upcoming, history, updates }

    public static Interview schedule(UUID id, UUID applicationId, String title, Instant startsAt, int duration,
                                     String zoneId, Mode mode, String place, String note, Instant now) {
        if (id == null || applicationId == null || startsAt == null || mode == null) throw invalid("Faltan datos de la entrevista");
        startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        if (!startsAt.isAfter(now) || startsAt.isAfter(now.plus(Duration.ofDays(365)))) {
            throw invalid("Elige una fecha futura dentro de los próximos 365 días");
        }
        if (duration < 15 || duration > 180) throw invalid("La duración debe ser de 15 a 180 minutos");
        try { ZoneId.of(zoneId); } catch (DateTimeException | NullPointerException e) { throw invalid("La zona horaria no es válida"); }
        title = clean(title, 120, true);
        place = clean(place, 500, true);
        note = clean(note, 1000, false);
        if (mode == Mode.ONLINE) {
            try {
                URI uri = URI.create(place);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getPort() > 65535) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw invalid("La reunión online necesita un enlace HTTPS válido sin credenciales"); }
        }
        return new Interview(id, applicationId, 0, title, startsAt, duration, zoneId, mode, place, note, Status.INVITED, now);
    }

    public Interview update(String title, Instant startsAt, int duration, String zoneId, Mode mode, String place, String note, Instant now) {
        requireActiveFuture(now);
        Interview next = schedule(id, applicationId, title, startsAt, duration, zoneId, mode, place, note, now);
        return new Interview(id, applicationId, version + 1, next.title, next.startsAt, duration, zoneId, mode,
                next.place, next.note, Status.INVITED, now);
    }

    public Interview respond(Status response, Instant now) {
        if (status != Status.INVITED || !startsAt.isAfter(now)) throw invalid("La invitación ya no admite respuestas");
        if (response != Status.CONFIRMED && response != Status.DECLINED) throw invalid("Elige confirmar o rechazar la invitación");
        return withStatus(response, now);
    }

    public Interview cancel(Instant now) {
        requireActiveFuture(now);
        return withStatus(Status.CANCELLED, now);
    }

    private Interview withStatus(Status next, Instant now) {
        return new Interview(id, applicationId, version + 1, title, startsAt, durationMinutes, zoneId, mode, place, note, next, now);
    }

    private void requireActiveFuture(Instant now) {
        if ((status != Status.INVITED && status != Status.CONFIRMED) || !startsAt.isAfter(now)) {
            throw invalid("Solo puedes cambiar una entrevista activa que aún no haya comenzado");
        }
    }

    public boolean sameInvitation(Interview other) {
        return applicationId.equals(other.applicationId) && title.equals(other.title) && startsAt.equals(other.startsAt)
                && durationMinutes == other.durationMinutes && zoneId.equals(other.zoneId) && mode == other.mode
                && place.equals(other.place) && java.util.Objects.equals(note, other.note);
    }

    private static String clean(String value, int max, boolean required) {
        String cleaned = value == null || value.isBlank() ? null : value.strip();
        if ((required && cleaned == null) || (cleaned != null && cleaned.length() > max)) throw invalid("Revisa los campos obligatorios y su longitud");
        return cleaned;
    }

    private static DomainException invalid(String detail) { return new DomainException("interview.invalid", detail); }

    @Override
    public String toString() { return "Interview[id=" + id + ", version=" + version + ", status=" + status + "]"; }
}
