-- Solo códigos seguros; nunca mensajes internos ni contenido del CV.
alter table screening_requests add column failure_code varchar(64);
