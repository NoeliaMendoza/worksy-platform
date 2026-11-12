Auditoría inicial de la plataforma
=================================

La migración V11 crea ``audit_events``. La API registra las acciones cubiertas
en la misma transacción que el cambio de negocio. Si falla el registro, el cambio
se deshace. No se crean eventos a partir de operaciones fallidas ni se reconstruye
un historial anterior a esta versión.

Cobertura
---------

* COMPANY_REGISTERED: empresa y cuenta de su administración creadas correctamente.
* VACANCY_CREATED, VACANCY_UPDATED, VACANCY_PUBLISHED, VACANCY_CLOSED: cambios de vacantes.
* SCREENING_REQUESTED: solicitud de un lote con al menos un análisis nuevo.
* REVIEW_PRESELECTED, REVIEW_WAITLISTED, REVIEW_REJECTED, REVIEW_CLEARED: decisiones humanas.

Un reenvío idempotente de la solicitud de análisis no duplica su auditoría.
Un lote sin CV nuevos no genera un evento. Las decisiones humanas registran la
operación solicitada, incluida la retirada de una decisión anterior.

Este alcance no incluye autenticaciones, lecturas de CV, invitaciones de equipo,
consentimientos, resultados automáticos del worker ni reintentos manuales. Tampoco
es un historial de todos los campos antes y después de cada cambio.

Datos registrados
----------------

Cada evento guarda UUID propio, fecha, identificador del autor, rol del autor en
ese momento, empresa, acción, identificador del recurso y su etiqueta.
La etiqueta es el nombre de empresa o título de la vacante; las decisiones apuntan
a la postulación y conservan el título de la vacante como contexto.

Los identificadores de recurso no tienen clave foránea, por lo que la retirada de
una postulación no elimina su evento. El rol y la etiqueta son instantáneas; el
correo del autor y nombre de empresa que muestra la consulta son sus valores
actuales. No se copian CV, hashes de contraseña, tokens o cabeceras de acceso.

No hay endpoints para crear, modificar o eliminar eventos desde el cliente.
El registro no ofrece protección criptográfica frente a cambios directos de un
administrador de la base de datos ni define todavía una política de retención.

Consulta
--------

``GET /api/v1/admin/audit-events?q=&action=&page=0&size=20``

Solo admite PLATFORM_ADMIN. La búsqueda es literal y no distingue mayúsculas:
correo del autor, nombre de empresa o etiqueta del recurso. Admite hasta 254
caracteres. La acción puede ser uno de los valores anteriores o vacía para todas.
Una acción desconocida o paginación inválida produce 400. Las páginas empiezan en
cero y admiten de 1 a 100 eventos. La respuesta tiene ``items``, ``total``, ``page``
y ``size``, ordenados por fecha descendente y UUID para resolver empates.

La ruta de interfaz ``/auditoria`` activa el menú Auditoría y muestra filtros,
actualización manual, paginación, carga, error y ausencia de resultados. Permite
consultar el identificador de recurso para distinguir acciones de postulaciones
de una misma vacante.

Validación
----------

Compilación de Java, fuentes de pruebas adaptadas y frontend, además de lint.
La validación funcional de la migración V11, transacciones, filtros y permisos
queda pendiente. No se ejecutan pruebas funcionales en este avance.
