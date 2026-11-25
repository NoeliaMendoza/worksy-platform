-- V21: ciclo de vida completo de las entrevistas (Entrega 1, aprobada el 2026-11-25).
-- Resultado después de la cita (realizada / no asistió), motivo y autoría de cada cambio, responsables de la
-- empresa y solicitudes de cambio de hora del candidato. No se inventan valores para filas existentes:
-- solo se derivan los que ya constan (quién agendó y de qué lado era cada cambio).

alter table interviews drop constraint interviews_status_check;
alter table interviews add constraint interviews_status_check
    check (status in ('INVITED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
-- Motivo visible para el candidato cuando la empresa cancela.
alter table interviews add column cancel_reason varchar(300);
-- Cuándo se registró el resultado: permite corregirlo durante 24 horas.
alter table interviews add column outcome_at timestamptz;

alter table interview_events drop constraint interview_events_change_check;
alter table interview_events add constraint interview_events_change_check
    check (change in ('SCHEDULED', 'UPDATED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'CHANGE_REQUESTED',
                      'CHANGE_WITHDRAWN', 'CHANGE_ACCEPTED', 'CHANGE_REJECTED', 'COMPLETED', 'NO_SHOW'));
alter table interview_events add column actor_role varchar(16) check (actor_role in ('CANDIDATE', 'COMPANY'));
alter table interview_events add column reason varchar(300);
alter table interview_events add column place_changed boolean;

update interview_events e set actor_role = case when a.candidate_id = e.changed_by then 'CANDIDATE' else 'COMPANY' end
from interviews i join applications a on a.id = i.application_id
where i.id = e.interview_id;

-- Personas de la empresa a cargo de la cita. Hoy, quien la agenda; el modelo admite más entrevistadores.
create table interview_participants (
    interview_id uuid        not null references interviews (id) on delete cascade,
    user_id      uuid        not null references users (id),
    role         varchar(16) not null check (role in ('ORGANIZER', 'INTERVIEWER')),
    primary key (interview_id, user_id)
);
create index ix_interview_participants_user on interview_participants (user_id);

insert into interview_participants (interview_id, user_id, role)
select interview_id, changed_by, 'ORGANIZER' from interview_events where change = 'SCHEDULED'
on conflict do nothing;

-- Propuesta de otra hora hecha por el candidato. La cita sigue en pie hasta que la empresa responda.
create table interview_change_requests (
    id           uuid         primary key,
    interview_id uuid         not null references interviews (id) on delete cascade,
    requested_by uuid         not null references users (id),
    reason       varchar(300) not null,
    -- Instantes ISO-8601 propuestos (de 1 a 3), en el orden en que los eligió el candidato.
    slots        jsonb        not null,
    status       varchar(16)  not null check (status in ('OPEN', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED')),
    created_at   timestamptz  not null,
    resolved_at  timestamptz,
    resolved_by  uuid         references users (id),
    constraint ck_change_request_slots check (jsonb_typeof(slots) = 'array' and jsonb_array_length(slots) between 1 and 3),
    constraint ck_change_request_resolution check ((status = 'OPEN') = (resolved_at is null))
);
-- Solo una propuesta abierta por cita: dos envíos simultáneos no crean dos.
create unique index ux_interview_change_requests_open on interview_change_requests (interview_id) where status = 'OPEN';
