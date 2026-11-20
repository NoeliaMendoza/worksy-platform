alter table applications add column stage varchar(16) not null default 'RECEIVED'
    check (stage in ('RECEIVED', 'REVIEW', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED'));

create table application_stage_history (
    id bigserial primary key,
    application_id uuid not null references applications(id) on delete cascade,
    from_stage varchar(16) check (from_stage in ('RECEIVED', 'REVIEW', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    stage varchar(16) not null check (stage in ('RECEIVED', 'REVIEW', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED')),
    changed_by uuid not null references users(id),
    message varchar(500),
    changed_at timestamptz not null
);
create index ix_application_stage_history on application_stage_history(application_id, id);

-- Las postulaciones previas comienzan como recibidas: no se infieren etapas del ranking.
insert into application_stage_history (application_id, stage, changed_by, changed_at)
select id, 'RECEIVED', candidate_id, applied_at from applications;
