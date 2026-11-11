package com.plataformaempleo.api.vacancy.infrastructure.web;

import com.plataformaempleo.api.vacancy.domain.model.Criterion;
import com.plataformaempleo.api.vacancy.domain.model.Vacancy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos JSON de vacantes. Las reglas de negocio las valida el dominio. */
final class VacancyDtos {

    private VacancyDtos() {
    }

    @Schema(name = "VacancyRequest", description = "Contenido editable de una vacante (solo en borrador)")
    record VacancyRequest(
            @Schema(example = "Desarrollador/a backend Java") String title,
            @Schema(example = "Buscamos una persona para el equipo de pagos...") @Size(max = 50_000) String description,
            @Schema(example = "Lima") String location,
            @Schema(example = "HIBRIDO") @NotNull Vacancy.Modality modality,
            @Valid @Size(max = Vacancy.MAX_CRITERIA) List<CriterionRequest> criteria) {

        Vacancy.Content toContent() {
            List<Vacancy.CriterionInput> inputs = criteria == null ? List.of()
                    : criteria.stream().map(c -> new Vacancy.CriterionInput(c.label(), c.question(), c.kind(), c.options())).toList();
            return Vacancy.Content.of(title, description, location, modality, inputs);
        }
    }

    @Schema(name = "CriterionRequest")
    record CriterionRequest(
            @Schema(example = "Spring Boot") String label,
            @Schema(example = "¿Tiene experiencia profesional con Spring Boot?") String question,
            @Schema(example = "YES_NO") @NotNull Criterion.Kind kind,
            @Schema(description = "Solo para CHOICE: entre 2 y 6 opciones; la primera es la más favorable") List<String> options) {
    }

    @Schema(name = "Criterion")
    record CriterionResponse(String key, String label, String question, Criterion.Kind kind, List<String> options) {
        static CriterionResponse from(Criterion c) {
            return new CriterionResponse(c.key(), c.label(), c.question(), c.kind(), c.options());
        }
    }

    @Schema(name = "CompanyVacancy", description = "Vacante vista por su empresa")
    record CompanyVacancyResponse(UUID id, String title, String description, String location, Vacancy.Modality modality,
                                  Vacancy.Status status, List<CriterionResponse> criteria, Instant createdAt,
                                  Instant updatedAt, Instant publishedAt, Instant closedAt) {
        static CompanyVacancyResponse from(Vacancy v) {
            var c = v.content();
            return new CompanyVacancyResponse(v.id(), c.title(), c.description(), c.location(), c.modality(), v.status(),
                    c.criteria().stream().map(CriterionResponse::from).toList(), v.createdAt(), v.updatedAt(),
                    v.publishedAt(), v.closedAt());
        }
    }

    @Schema(name = "PublicVacancySummary", description = "Vacante publicada en la lista pública")
    record PublicSummary(UUID id, String title, String companyName, String location, Vacancy.Modality modality,
                         Instant publishedAt) {
    }

    @Schema(name = "PublicVacancy", description = "Vacante publicada con su descripción y lo que se evaluará")
    record PublicDetail(UUID id, String title, String companyName, String description, String location,
                        Vacancy.Modality modality, Instant publishedAt, List<CriterionResponse> criteria) {
    }
}
