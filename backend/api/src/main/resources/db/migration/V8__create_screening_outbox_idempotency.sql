-- V8: solicitudes de preselección, outbox de eventos e idempotencia (secciones 1, 4 y 5 del diseño).

-- Una solicitud por postulación y proveedor de decisión. La versión del modelo se conoce al procesar.
CREATE TABLE screening_requests (
    id             UUID         PRIMARY KEY,
    vacancy_id     UUID         NOT NULL REFERENCES vacancies (id),
    application_id UUID         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    cv_id          UUID         NOT NULL REFERENCES cv_documents (id),
    provider       VARCHAR(32)  NOT NULL,
    model_version  VARCHAR(64),
    status         VARCHAR(16)  NOT NULL,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    overall_score  NUMERIC(5, 4),
    confidence     NUMERIC(5, 4),
    requested_by   UUID         NOT NULL REFERENCES users (id),
    requested_at   TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_screening_application_provider UNIQUE (application_id, provider),
    CONSTRAINT ck_screening_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'NEEDS_REVIEW', 'FAILED')),
    CONSTRAINT ck_screening_score CHECK (overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 1)),
    CONSTRAINT ck_screening_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_screening_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_screening_vacancy_status ON screening_requests (vacancy_id, status);

-- Outbox: el evento se guarda en la MISMA transacción que el cambio de negocio y un publicador
-- lo envía después a RabbitMQ. Así no se pierden eventos si el proceso cae entre ambos pasos.
CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

-- Índice parcial: el publicador solo recorre lo pendiente (sección 4 del diseño).
CREATE INDEX ix_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

-- Idempotency-Key: repetir la misma petición devuelve el mismo resultado sin repetir el trabajo.
CREATE TABLE idempotency_keys (
    actor_id    UUID         NOT NULL REFERENCES users (id),
    idem_key    VARCHAR(100) NOT NULL,
    operation   VARCHAR(64)  NOT NULL,
    fingerprint CHAR(64)     NOT NULL,
    result      JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (actor_id, idem_key)
);
