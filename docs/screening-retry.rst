Reintento manual de análisis fallidos
====================================

Desde las postulaciones de una vacante, la empresa puede pulsar
``Reintentar análisis`` si el resultado está FAILED y el candidato mantiene
el consentimiento para compartir su CV. La pantalla desactiva el botón mientras
envía la solicitud y vuelve a actualizar el estado durante el procesamiento.

Contrato
--------

``POST /api/v1/companies/me/vacancies/{id}/screenings/{requestId}/retry``

Requiere autenticación de COMPANY_ADMIN o RECRUITER de la empresa propietaria y
``Idempotency-Key``. No necesita cuerpo. Respuesta 202::

    {"request": {"id": "...", "status": "PENDING", "...": "..."}, "replayed": false}

Una repetición con la misma clave devuelve ``replayed: true`` y el estado actual
de esa solicitud. No crea otro evento. Si se usa la clave para otra operación o
solicitud, devuelve 400. Si otro cliente ya inició el análisis o terminó, una
nueva clave devuelve 409; la interfaz actualiza el estado en ese caso.

La API vuelve a comprobar el consentimiento, incluso al recuperar una respuesta
idempotente. Devuelve 403 si se retiró y 404 si el recurso no existe en la empresa.

Persistencia y procesamiento
---------------------------

Una actualización condicional FAILED -> PENDING evita que dos clientes creen dos
reintentos simultáneos. El cambio de estado, el evento de outbox y la clave de
idempotencia se guardan en una transacción. Si falla cualquier paso se deshace
el reintento entero.

Se mantienen el identificador de solicitud, proveedor, autor y fecha originales,
y el contador acumulado de intentos. La versión del modelo y las puntuaciones
se limpian hasta que termine el procesamiento nuevo. El evento guarda el usuario
que pidió el reintento y recibe un identificador de mensaje nuevo: el worker
conserva la deduplicación de los mensajes antiguos. El bloqueo existente en Redis
también se aplica al reintento.

No se modifican las decisiones humanas de ``application_reviews``. Un resultado
COMPLETED o NEEDS_REVIEW se revisa normalmente y no admite este reintento.

Si se pierde la respuesta de red, la interfaz conserva la misma clave para el
siguiente clic. El estado devuelto se aplica a los datos actuales para conservar
las actualizaciones de otras postulaciones.

Limitaciones
------------

El reintento usa el proveedor original. No corrige un PDF escaneado, credenciales
inválidas ni la indisponibilidad persistente del servidor de inferencia. La
empresa puede abrir el CV para revisarlo manualmente.

No requiere migración nueva. La compilación y el lint se comprueban por separado;
la validación funcional de este cambio queda pendiente.
