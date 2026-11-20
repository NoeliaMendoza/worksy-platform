package com.plataformaempleo.api.jobapplication.domain.model;

import com.plataformaempleo.api.screening.domain.model.ScreeningRequest;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Una postulación vista desde la pantalla "Candidatos" de la empresa (todas sus vacantes a la vez).
 * Si la persona no comparte sus datos, la fila sigue existiendo (cuenta como postulación) pero sin nada
 * personal: ni nombre, ni correo, ni CV, ni etapa.
 *
 * <p>No incluye la puntuación del modelo: solo se compara dentro del ranking de cada vacante
 * (decisión de la usuaria del 2026-11-20).
 */
public record CompanyApplication(
        UUID applicationId,
        UUID vacancyId,
        String vacancyTitle,
        Vacancy.Status vacancyStatus,
        Instant appliedAt,
        boolean shared,
        String candidateName,
        String candidateEmail,
        UUID cvId,
        ApplicationStage stage,
        ScreeningRequest.Status screeningStatus,
        String screeningFailureCode,
        Instant nextInterviewAt) {

    public CompanyApplication {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(vacancyId, "vacancyId");
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (!shared && (candidateName != null || candidateEmail != null || cvId != null || stage != null)) {
            throw new IllegalArgumentException("Sin permiso de compartir no puede haber datos personales");
        }
    }

    /** Filtros de la lista. Cualquiera puede ser null (sin filtro). */
    public record Filter(UUID vacancyId, ApplicationStage stage, String text) {

        /** VALOR SUPUESTO: largo máximo del texto de búsqueda. */
        public static final int MAX_TEXT = 100;

        public Filter {
            text = text == null || text.isBlank() ? null : text.strip();
            if (text != null && text.length() > MAX_TEXT) {
                text = text.substring(0, MAX_TEXT);
            }
        }
    }

    /** Una página de resultados y el total que cumple los filtros. */
    public record Page(List<CompanyApplication> items, long total, int page, int size) {

        public Page {
            items = List.copyOf(items);
        }
    }
}
