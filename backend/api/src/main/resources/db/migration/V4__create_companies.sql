-- V4: empresas (inquilinos). Cada reclutador y administrador/a de empresa pertenece a una.
-- Cierra el pendiente de V1: users.company_id pasa a tener clave foránea.

CREATE TABLE companies (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_companies_name_not_blank CHECK (length(btrim(name)) >= 2)
);

ALTER TABLE users
    ADD CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id);
