-- V1: tabla de usuarios.
-- company_id queda sin clave foránea hasta que exista la tabla companies (fase F1).

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    company_id    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_users_role CHECK (role IN ('CANDIDATE', 'RECRUITER', 'COMPANY_ADMIN', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_users_company_by_role CHECK (
        (role IN ('RECRUITER', 'COMPANY_ADMIN') AND company_id IS NOT NULL)
        OR (role IN ('CANDIDATE', 'PLATFORM_ADMIN') AND company_id IS NULL)
    )
);

-- El correo es único entre usuarios activos; un usuario borrado no bloquea un nuevo registro.
CREATE UNIQUE INDEX ux_users_email_active ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_company_id ON users (company_id) WHERE company_id IS NOT NULL;
