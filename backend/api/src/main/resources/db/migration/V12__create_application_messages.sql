-- V12: mensajes entre la empresa y la persona candidata dentro de una postulación (fase F3).
-- Contrato nuevo aprobado por el usuario el 2026-11-15. La empresa escribe primero; la persona responde.
-- Retirar la postulación borra la conversación (ON DELETE CASCADE), igual que el resto de sus datos.

CREATE TABLE application_messages (
    id             UUID          PRIMARY KEY,
    application_id UUID          NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    sender_id      UUID          NOT NULL REFERENCES users (id),
    -- Lado que escribió: true = la persona candidata, false = alguien de la empresa.
    from_candidate BOOLEAN       NOT NULL,
    body           VARCHAR(2000) NOT NULL,
    sent_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_application_messages_body CHECK (char_length(btrim(body)) > 0)
);

CREATE INDEX ix_application_messages_thread ON application_messages (application_id, sent_at DESC, id DESC);

-- Hasta cuándo leyó cada persona cada conversación (sirve para contar los no leídos).
CREATE TABLE conversation_reads (
    application_id UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users (id),
    last_read_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (application_id, user_id)
);
