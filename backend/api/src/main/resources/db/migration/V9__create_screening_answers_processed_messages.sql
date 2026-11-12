-- V9: respuestas por criterio de cada preselección y registro de mensajes ya procesados (sección 4 del diseño).
-- Las escribe el screening-worker; Flyway sigue viviendo en la API (decisión de F2).

-- Una fila por criterio de la vacante. Es la explicación por pregunta que verá la persona que revisa.
CREATE TABLE screening_answers (
    id           UUID          PRIMARY KEY,
    request_id   UUID          NOT NULL REFERENCES screening_requests (id) ON DELETE CASCADE,
    question_key VARCHAR(16)   NOT NULL,
    choice       VARCHAR(200),
    probability  NUMERIC(5, 4),
    score        NUMERIC(5, 4),
    latency_ms   INTEGER,
    CONSTRAINT ux_screening_answers_question UNIQUE (request_id, question_key),
    CONSTRAINT ck_screening_answers_probability CHECK (probability IS NULL OR (probability >= 0 AND probability <= 1)),
    CONSTRAINT ck_screening_answers_score CHECK (score IS NULL OR (score >= 0 AND score <= 1))
);

-- Idempotencia del consumidor: el mismo mensaje (reentregado por RabbitMQ o publicado dos veces por el
-- outbox) se procesa una sola vez. Se inserta en la MISMA transacción que los resultados.
CREATE TABLE processed_messages (
    message_id   VARCHAR(64) NOT NULL,
    consumer     VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer)
);
