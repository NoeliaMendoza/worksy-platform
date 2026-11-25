package com.plataformaempleo.api.interview.infrastructure.web;

import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Archivo iCalendar (RFC 5545) de una cita, para agregarla a Google Calendar, Outlook o Apple Calendar sin conectar
 * ninguna cuenta. Las horas van en UTC (cada calendario las muestra en la zona de quien lo abre). El UID es fijo por cita
 * y SEQUENCE es su versión: al descargarlo de nuevo, el calendario actualiza el evento en lugar de duplicarlo, y una cita
 * cancelada lo marca como cancelado. Solo lleva lo que ya ve quien lo descarga; nunca datos de otras personas del equipo.
 */
final class InterviewCalendarFile {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private InterviewCalendarFile() { }

    static byte[] build(InterviewRepository.Item item, boolean company, String publicUrl, Instant now) {
        Interview i = item.interview();
        String with = company ? item.candidateEmail() + ", " + item.vacancyTitle() : item.companyName();
        String link = publicUrl.replaceAll("/+$", "") + "/entrevistas?cita=" + i.id();
        StringBuilder description = new StringBuilder(item.vacancyTitle()).append(" en ").append(item.companyName()).append('.');
        if (i.note() != null) description.append("\n\n").append(i.note());
        if (i.status() == Interview.Status.CANCELLED && i.cancelReason() != null) {
            description.append("\n\nCancelada: ").append(i.cancelReason());
        }
        description.append("\n\nConsulta la cita, confírmala o propón otra hora en Worksy: ").append(link);

        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Worksy//Entrevistas//ES");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + i.id() + "@worksy");
        line(ics, "SEQUENCE:" + i.version());
        line(ics, "DTSTAMP:" + UTC.format(now.truncatedTo(ChronoUnit.SECONDS)));
        line(ics, "DTSTART:" + UTC.format(i.startsAt().truncatedTo(ChronoUnit.SECONDS)));
        line(ics, "DTEND:" + UTC.format(i.endsAt().truncatedTo(ChronoUnit.SECONDS)));
        line(ics, "SUMMARY:" + text(i.title() + " con " + with));
        line(ics, "LOCATION:" + text(i.place()));
        if (i.mode() == Interview.Mode.ONLINE) line(ics, "URL:" + i.place());
        line(ics, "DESCRIPTION:" + text(description.toString()));
        line(ics, "STATUS:" + switch (i.status()) {
            case CONFIRMED, COMPLETED -> "CONFIRMED";
            case CANCELLED, DECLINED, NO_SHOW -> "CANCELLED";
            default -> "TENTATIVE";
        });
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Escapa un TEXT de iCalendar: barra invertida, punto y coma, coma y saltos de línea. */
    static String text(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    /** Añade una línea con CRLF, plegada a 75 bytes sin partir caracteres UTF-8 (RFC 5545, 3.1). */
    private static void line(StringBuilder out, String content) {
        int bytes = 0;
        int limit = 75;
        for (int index = 0; index < content.length(); ) {
            int codePoint = content.codePointAt(index);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) {
                out.append("\r\n ");
                bytes = 1;
                limit = 75;
            }
            out.appendCodePoint(codePoint);
            bytes += size;
            index += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
