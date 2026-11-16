Usuarios en la administración de plataforma
==========================================

La ruta ``/usuarios`` muestra las cuentas activas al rol PLATFORM_ADMIN y activa
la opción Usuarios del menú. Incluye correo, rol, empresa y fecha de registro;
la cuenta con la que se inició sesión se marca como ``Tu cuenta``.

``GET /api/v1/admin/users?q=&role=&page=0&size=20``

El caso de uso exige PLATFORM_ADMIN antes de consultar datos. Los demás roles
reciben 403 y las peticiones sin autenticación requieren token.

La búsqueda encuentra una parte literal del correo o del nombre de empresa,
sin distinguir mayúsculas. Admite hasta 254 caracteres; % y _ no son comodines.
El filtro de rol admite CANDIDATE, RECRUITER, COMPANY_ADMIN y PLATFORM_ADMIN;
vacío significa todos. Un rol desconocido produce 400. La página empieza en
cero y el tamaño válido es de 1 a 100 (20 por defecto).

La respuesta contiene ``items``, ``total`` (coincidencias de los filtros),
``page`` y ``size``. Cada cuenta contiene ``id``, ``email``, ``role``,
``companyId``, ``companyName`` y ``createdAt``. La empresa puede ser nula en
cuentas de candidato o administración de plataforma.

Se excluyen cuentas con ``deleted_at`` y se ordena por fecha de creación
descendente, usando UUID para resolver empates. Las credenciales, tokens,
documentos y contenido de CV no se consultan ni se devuelven.

Los filtros se aplican al pulsar Filtrar usuarios y vuelven a la primera página.
La pantalla incluye actualización manual, limpieza de filtros, carga, error,
lista vacía, búsqueda sin resultados y navegación entre páginas.

Este cambio es de consulta: no crea, elimina o suspende cuentas, ni cambia roles.
No crea un administrador por defecto ni requiere migraciones nuevas. La
validación funcional queda pendiente; compilación y lint se comprueban aparte.
