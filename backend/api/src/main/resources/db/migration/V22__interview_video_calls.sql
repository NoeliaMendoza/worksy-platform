-- V22: videollamada integrada en Worksy (Entrega 2, aprobada el 2026-11-25).
-- La sala pertenece a una entrevista con modalidad VIDEO. Solo se guarda quién entró y cuándo: nunca audio,
-- video, ofertas SDP, candidatos ICE ni direcciones IP (eso viaja solo por el WebSocket y no se conserva).

alter table interviews drop constraint interviews_mode_check;
alter table interviews add constraint interviews_mode_check check (mode in ('ONLINE', 'IN_PERSON', 'VIDEO'));

create table interview_call_sessions (
    id           uuid        primary key,
    interview_id uuid        not null references interviews (id) on delete cascade,
    opened_at    timestamptz not null,
    closed_at    timestamptz,
    closed_by    uuid        references users (id),
    constraint ck_call_session_closed check (closed_at is null or closed_at >= opened_at)
);
-- Una sola sala abierta por entrevista: dos personas que entran a la vez caen en la misma.
create unique index ux_interview_call_sessions_open on interview_call_sessions (interview_id) where closed_at is null;

create table interview_call_participants (
    session_id uuid        not null references interview_call_sessions (id) on delete cascade,
    user_id    uuid        not null references users (id),
    role       varchar(16) not null check (role in ('CANDIDATE', 'INTERVIEWER')),
    joined_at  timestamptz not null,
    left_at    timestamptz,
    primary key (session_id, user_id)
);
