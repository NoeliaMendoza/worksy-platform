-- Marca monotónica por postulación: leer una pantalla antigua no oculta cambios posteriores.
alter table applications add column stage_seen_history_id bigint not null default 0
    check (stage_seen_history_id >= 0);
