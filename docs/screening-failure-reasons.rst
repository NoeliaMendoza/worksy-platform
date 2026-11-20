Motivos de fallo del análisis
============================

La migración Flyway V13 agrega failure_code a screening_requests. El worker
guarda el código junto al estado FAILED y el registro del mensaje procesado
en una transacción. No se guardan mensajes de excepción, texto del CV ni cuerpos
de respuesta externos en este campo.

El contrato OpenAPI de ScreeningRequest agrega failureCode (nullable) en las
respuestas de solicitud, listado y reintento. Solo se devuelve para FAILED,
con los mismos controles existentes de empresa y rol. La interfaz oculta el
motivo si la persona retiró el consentimiento para compartir el CV.
Al reintentar, la actualización condicional FAILED -> PENDING borra el código.

Códigos actuales
----------------

* PDF_NO_TEXT: PDF sin texto extraíble.
* PDF_PASSWORD_PROTECTED: PDF con contraseña.
* PDF_UNREADABLE: archivo dañado o ilegible.
* CV_TEXT_TOO_LONG: más de 100.000 caracteres extraídos.
* CV_NOT_FOUND: documento inexistente en el almacenamiento.
* STORAGE_UNAVAILABLE: fallo temporal al acceder al documento.
* INFERENCE_UNAVAILABLE: servicio de análisis inaccesible o con errores tras
  agotar los intentos.
* INFERENCE_NOT_CONFIGURED: proveedor no configurado (HTTP 501).
* INFERENCE_REJECTED: petición rechazada (HTTP 4xx); no atribuye la causa
  exclusivamente al documento.
* INFERENCE_INVALID_RESPONSE: respuesta vacía o criterios incompletos.
* UNKNOWN: fallo sin categoría específica.

La interfaz traduce únicamente códigos conocidos a mensajes y orientaciones.
Los análisis antiguos, códigos desconocidos y valores ausentes muestran un aviso
general. Nunca muestra un código desconocido como texto de diagnóstico.
Se conserva el reintento manual existente; los avisos aclaran cuándo reintentar
el mismo archivo no resolverá el problema. No se altera la decisión humana.

Despliegue y validación
----------------------

La API debe aplicar V13 antes de arrancar el worker actualizado. La migración
es aditiva y los registros existentes conservan null. No se ha aplicado a una
base de datos de desarrollo ni producción durante este cambio.

Las pruebas unitarias cubren clasificación y persistencia mediante un puerto en
memoria. Las pruebas de interfaz cubren mensajes conocidos, históricos y
desconocidos. Las pruebas de integración agregadas comprueban el código en
PostgreSQL y su exposición autorizada y limpieza al reintentar; requieren Docker.

Validación local (2026-11-16, America/Guayaquil): 97 pruebas de frontend y
17 pruebas unitarias seleccionadas del worker aprobadas; build y lint del
frontend aprobados. La versión final pasó las 2 pruebas de
ScreeningRequestIntegrationTest y las 3 de WorkerScreeningIntegrationTest con
contenedores efímeros, incluida la migración V13. No se ejecutó toda la suite
de integración del proyecto.
