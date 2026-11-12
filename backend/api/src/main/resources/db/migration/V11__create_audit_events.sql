-- Registro de las acciones cubiertas por la API; se guarda con el cambio de negocio.
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id UUID NOT NULL REFERENCES users (id),
    actor_role VARCHAR(32) NOT NULL,
    company_id UUID REFERENCES companies (id),
    action VARCHAR(32) NOT NULL,
    target_id UUID NOT NULL,
    target_label VARCHAR(160) NOT NULL,
    CONSTRAINT ck_audit_role CHECK (actor_role IN ('CANDIDATE', 'RECRUITER', 'COMPANY_ADMIN', 'PLATFORM_ADMIN')),
    CONSTRAINT ck_audit_action CHECK (action IN (
        'COMPANY_REGISTERED', 'VACANCY_CREATED', 'VACANCY_UPDATED', 'VACANCY_PUBLISHED', 'VACANCY_CLOSED',
        'SCREENING_REQUESTED', 'REVIEW_PRESELECTED', 'REVIEW_WAITLISTED', 'REVIEW_REJECTED', 'REVIEW_CLEARED'
    ))
);

CREATE INDEX ix_audit_date ON audit_events (occurred_at DESC, id);
CREATE INDEX ix_audit_action_date ON audit_events (action, occurred_at DESC, id);
