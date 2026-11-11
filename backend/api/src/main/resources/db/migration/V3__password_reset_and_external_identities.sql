-- V3: recuperación de contraseña y cuentas externas (Google, LinkedIn, GitHub).

-- Enlaces de recuperación: de un solo uso y de vida corta. Solo se guarda el SHA-256 del secreto.
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    CONSTRAINT ux_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_password_reset_tokens_user_pending ON password_reset_tokens (user_id) WHERE used_at IS NULL;

-- Vínculo de un usuario con su cuenta en un proveedor externo.
-- La clave natural es (provider, subject): el correo del proveedor puede cambiar, el subject no.
CREATE TABLE user_identities (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(32)  NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    email       VARCHAR(254),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_user_identities_provider_subject UNIQUE (provider, subject),
    CONSTRAINT ck_user_identities_provider_lowercase CHECK (provider = lower(provider))
);

CREATE INDEX ix_user_identities_user ON user_identities (user_id);
