-- V5: vacantes de cada empresa.
-- Los criterios son objetos de valor de la vacante (se crean, cambian y borran con ella),
-- por eso se guardan como un arreglo JSONB con la forma que espera el servicio de inferencia.

CREATE TABLE vacancies (
    id           UUID         PRIMARY KEY,
    company_id   UUID         NOT NULL REFERENCES companies (id),
    created_by   UUID         NOT NULL REFERENCES users (id),
    title        VARCHAR(160) NOT NULL,
    description  TEXT         NOT NULL,
    location     VARCHAR(120) NOT NULL,
    modality     VARCHAR(16)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    criteria     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    CONSTRAINT ck_vacancies_modality CHECK (modality IN ('PRESENCIAL', 'HIBRIDO', 'REMOTO')),
    CONSTRAINT ck_vacancies_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_vacancies_criteria_array CHECK (jsonb_typeof(criteria) = 'array'),
    CONSTRAINT ck_vacancies_description_size CHECK (length(description) <= 50000),
    CONSTRAINT ck_vacancies_published CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT ck_vacancies_closed CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL)
);

CREATE INDEX ix_vacancies_company ON vacancies (company_id, created_at DESC);
CREATE INDEX ix_vacancies_published ON vacancies (published_at DESC) WHERE status = 'PUBLISHED';
