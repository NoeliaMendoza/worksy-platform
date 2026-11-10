-- V2: tokens de refresco con rotación.
-- Solo se guarda el SHA-256 del secreto; el secreto viaja únicamente en una cookie HttpOnly.
-- family_id agrupa los tokens de un mismo inicio de sesión para revocarlos juntos.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_refresh_tokens_replaced CHECK (replaced_by IS NULL OR revoked_at IS NOT NULL)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
