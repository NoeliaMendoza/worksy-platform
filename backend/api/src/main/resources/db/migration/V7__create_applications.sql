-- V7: postulaciones de candidatos a vacantes publicadas.
-- Contrato nuevo aprobado por el usuario (no figuraba en el diseño): POST /api/v1/vacancies/{id}/applications.
-- Retirar una postulación borra la fila: no se conserva el vínculo entre la persona y la vacante.

CREATE TABLE applications (
    id           UUID        PRIMARY KEY,
    vacancy_id   UUID        NOT NULL REFERENCES vacancies (id),
    candidate_id UUID        NOT NULL REFERENCES users (id),
    -- RESTRICT: un CV en uso no se borra sin retirar antes la postulación (el caso de uso lo explica).
    cv_id        UUID        NOT NULL REFERENCES cv_documents (id) ON DELETE RESTRICT,
    applied_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_applications_vacancy_candidate UNIQUE (vacancy_id, candidate_id)
);

CREATE INDEX ix_applications_vacancy ON applications (vacancy_id, applied_at);
CREATE INDEX ix_applications_candidate ON applications (candidate_id, applied_at DESC);
CREATE INDEX ix_applications_cv ON applications (cv_id);
