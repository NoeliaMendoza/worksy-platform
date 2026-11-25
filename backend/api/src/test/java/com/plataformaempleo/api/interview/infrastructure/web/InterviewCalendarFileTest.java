package com.plataformaempleo.api.interview.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.plataformaempleo.api.interview.domain.model.Interview;
import com.plataformaempleo.api.interview.domain.port.out.InterviewRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Archivo .ics de una cita. Datos sintéticos. */
class InterviewCalendarFileTest {
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private InterviewRepository.Item item(Interview.Status status, String note, String cancelReason, int version) {
        Interview interview = new Interview(ID, UUID.randomUUID(), version, "Entrevista técnica", Instant.parse("2026-11-26T15:00:00Z"), 45,
                "America/Guayaquil", Interview.Mode.ONLINE, "https://reunion.ejemplo.test/sala", note, status, cancelReason, null, NOW);
        return new InterviewRepository.Item(interview, "Backend Java", "Andina Cargo (demo)", "persona@ejemplo.test",
                "seleccion@ejemplo.test", false, true, Interview.Phase.ACTIVE, null);
    }

    private String build(InterviewRepository.Item item, boolean company) {
        return new String(InterviewCalendarFile.build(item, company, "https://worksy.ejemplo.test/", NOW), StandardCharsets.UTF_8);
    }

    @Test
    void generaUnEventoEnUtcConUidFijoYVersionComoSecuencia() {
        String ics = build(item(Interview.Status.CONFIRMED, null, null, 3), false);
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("UID:" + ID + "@worksy\r\n", "SEQUENCE:3\r\n", "DTSTART:20261126T150000Z\r\n",
                "DTEND:20261126T154500Z\r\n", "STATUS:CONFIRMED\r\n", "URL:https://reunion.ejemplo.test/sala\r\n",
                "SUMMARY:Entrevista técnica con Andina Cargo (demo)\r\n");
        // El candidato no recibe correos del equipo; el enlace vuelve a la cita en Worksy.
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).doesNotContain("seleccion@ejemplo.test", "persona@ejemplo.test")
                .contains("https://worksy.ejemplo.test/entrevistas?cita=" + ID);
    }

    @Test
    void laEmpresaVeAQuienEntrevistaYUnaCanceladaQuedaCanceladaConSuMotivo() {
        String ics = build(item(Interview.Status.CANCELLED, null, "La vacante se pausó", 4), true);
        assertThat(ics).contains("STATUS:CANCELLED\r\n", "SUMMARY:Entrevista técnica con persona@ejemplo.test\\, Backend Java\r\n");
        assertThat(ics.replace("\r\n ", "")).contains("Cancelada: La vacante se pausó").doesNotContain("seleccion@ejemplo.test");
        assertThat(build(item(Interview.Status.INVITED, null, null, 0), false)).contains("STATUS:TENTATIVE\r\n");
    }

    @Test
    void escapaElTextoYPliegaLineasLargasSinPartirCaracteres() {
        String note = "Trae: portafolio; laptop, y\nun proyecto \\ explicación ñandú ".repeat(6);
        String ics = build(item(Interview.Status.INVITED, note, null, 1), false);
        assertThat(InterviewCalendarFile.text("a;b,c\\d\ne")).isEqualTo("a\\;b\\,c\\\\d\\ne");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).as(line).isLessThanOrEqualTo(75);
        }
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("Trae: portafolio\\; laptop\\, y\\nun proyecto \\\\ explicación ñandú");
        assertThat(Arrays.stream(ics.split("\r\n")).filter(l -> l.startsWith("DESCRIPTION:")).count()).isEqualTo(1);
    }
}
