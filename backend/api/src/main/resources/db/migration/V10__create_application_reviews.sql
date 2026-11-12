-- La decisión humana se guarda aparte del resultado automático.
CREATE TABLE application_reviews (
    application_id UUID PRIMARY KEY REFERENCES applications (id) ON DELETE CASCADE,
    decision VARCHAR(20),
    reviewed_by UUID NOT NULL REFERENCES users (id),
    reviewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_application_review CHECK (decision IS NULL OR decision IN ('preseleccionado', 'en_espera', 'descartado'))
);
