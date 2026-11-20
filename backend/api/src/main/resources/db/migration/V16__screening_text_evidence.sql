-- Fragmentos acotados del texto extraído; el PDF completo continúa en MinIO.
alter table screening_requests
    add column evidence_method varchar(32),
    add column source_text_sha256 varchar(64),
    add constraint ck_screening_evidence_source check (
        (evidence_method is null and source_text_sha256 is null)
        or (evidence_method is not null and source_text_sha256 is not null
            and source_text_sha256 ~ '^[0-9a-f]{64}$'));

alter table screening_answers
    add column evidence jsonb not null default '[]'::jsonb,
    add constraint ck_screening_evidence_array check (
        jsonb_typeof(evidence) = 'array' and jsonb_array_length(evidence) <= 3);
