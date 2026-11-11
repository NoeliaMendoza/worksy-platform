-- V6: perfil del candidato (consentimientos) y metadatos de sus CV.
-- El PDF NUNCA se guarda aquí: vive en el almacenamiento de objetos (MinIO) bajo storage_key.

CREATE TABLE candidate_profiles (
    user_id        UUID         PRIMARY KEY REFERENCES users (id),
    full_name      VARCHAR(160),
    headline       VARCHAR(200),
    location       VARCHAR(120),
    -- Los consentimientos controlan qué ve el reclutador. Por defecto, nada.
    consent_share  BOOLEAN      NOT NULL DEFAULT false,
    consent_github BOOLEAN      NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE cv_documents (
    id           UUID         PRIMARY KEY,
    candidate_id UUID         NOT NULL REFERENCES users (id),
    -- Llave aleatoria en el almacenamiento: no contiene nombres ni datos del candidato.
    storage_key  VARCHAR(200) NOT NULL,
    sha256       CHAR(64)     NOT NULL,
    mime         VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    language     VARCHAR(8),
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_cv_documents_storage_key UNIQUE (storage_key),
    -- El hash evita que el mismo candidato suba dos veces el mismo archivo.
    CONSTRAINT ux_cv_documents_candidate_sha UNIQUE (candidate_id, sha256),
    CONSTRAINT ck_cv_documents_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cv_documents_mime CHECK (mime = 'application/pdf'),
    CONSTRAINT ck_cv_documents_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_cv_documents_status CHECK (status IN ('UPLOADED'))
);

CREATE INDEX ix_cv_documents_candidate ON cv_documents (candidate_id, uploaded_at DESC);
