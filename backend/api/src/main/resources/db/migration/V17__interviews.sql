create table interviews (
    id uuid primary key,
    application_id uuid not null references applications(id) on delete cascade,
    version integer not null default 0 check (version >= 0),
    title varchar(120) not null,
    starts_at timestamptz not null,
    duration_minutes integer not null check (duration_minutes between 15 and 180),
    zone_id varchar(64) not null,
    mode varchar(16) not null check (mode in ('ONLINE', 'IN_PERSON')),
    place varchar(500) not null,
    note varchar(1000),
    status varchar(16) not null check (status in ('INVITED', 'CONFIRMED', 'DECLINED', 'CANCELLED')),
    updated_at timestamptz not null
);
create index ix_interviews_application on interviews(application_id);
create index ix_interviews_schedule on interviews(starts_at, id);
create table interview_events (
    interview_id uuid not null references interviews(id) on delete cascade,
    version integer not null,
    change varchar(16) not null check (change in ('SCHEDULED', 'UPDATED', 'CONFIRMED', 'DECLINED', 'CANCELLED')),
    starts_at timestamptz not null,
    duration_minutes integer not null,
    zone_id varchar(64) not null,
    changed_by uuid not null references users(id),
    changed_at timestamptz not null,
    primary key (interview_id, version)
);
create table interview_reads (
    interview_id uuid not null references interviews(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    version integer not null check (version >= 0),
    primary key (interview_id, user_id)
);
