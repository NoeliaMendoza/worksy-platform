Empresas en la administración de plataforma
==========================================

La pantalla Empresas de PLATFORM_ADMIN consulta los registros reales de la
plataforma. Incluye nombre, fecha de registro, administradores de empresa,
reclutadores, total de vacantes y cantidad actualmente publicada.

``GET /api/v1/admin/companies?q=&page=0&size=20``

Solo admite el rol PLATFORM_ADMIN. Una cuenta de candidato, reclutador o
administrador de empresa recibe 403. Sin autenticación, la API exige el token.
No devuelve correos de usuarios, datos de postulaciones ni contenido de CV.

La búsqueda encuentra una parte literal del nombre, sin distinguir mayúsculas.
Los caracteres % y _ no actúan como comodines. Admite hasta 160 caracteres.
Las páginas empiezan en cero y el tamaño válido es de 1 a 100 (20 por defecto).
Los parámetros inválidos producen un error 400.

La respuesta contiene ``items``, ``total`` (cantidad que coincide con la búsqueda),
``page`` y ``size``. Cada empresa incluye ``id``, ``name``, ``createdAt``,
``administrators``, ``recruiters``, ``vacancies`` y ``publishedVacancies``.

Las empresas se ordenan por fecha de registro descendente, con UUID para resolver
empates. Se selecciona la página antes de calcular las cantidades. Los equipos
excluyen cuentas eliminadas y las vacantes totales incluyen DRAFT, PUBLISHED y
CLOSED. Los agregados de equipo y vacantes se calculan por separado, evitando
multiplicar los conteos al unir varias tablas.

La interfaz distingue carga, error, lista vacía y búsqueda sin resultados.
Permite actualizar los datos, limpiar el filtro y avanzar o retroceder páginas.
La búsqueda vuelve a la primera página.

No añade aprobación, suspensión ni eliminación de empresas. No cambia el flujo
de registro público ni requiere nuevas migraciones. Las secciones Usuarios y
Auditoría del menú siguen pendientes.

Validación funcional pendiente; compilación y lint se revisan por separado.
