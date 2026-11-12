Validación local del flujo de preselección
========================================

Fecha: 2026-11-11 (America/Guayaquil).

Se integraron los cambios de los PR #16 (ranking/worker, commit 527c456) y #17
(adaptador Laya, commit 2e2e776) en un worktree de prueba. Los PR permanecen
separados para revisión; esta validación no los combinó en main.

Se utilizó el proyecto Docker aislado ``worksy-smoke`` sin puertos publicados.
Los JAR se compilaron desde el código integrado con Maven 3.9.11 y Java 21,
aprovechando la caché local, y se empaquetaron con eclipse-temurin:21-jre.
La compilación Maven dentro de Docker se detuvo durante la descarga de
dependencias; esa ruta no se completó en esta prueba.

Servicios: PostgreSQL 17, RabbitMQ 4.1, Redis 8, MinIO, API, worker e inferencia.
Solo se generaron cuentas ficticias y un PDF sintético de tres líneas, con
un criterio binario de experiencia en Java. Los servicios de desarrollo existentes
no se modificaron.

Resultados del recorrido completo
--------------------------------

==========  ==================  ===========  =========  ===================
Proveedor   Estado del análisis Puntuación   Confianza  Tiempo de análisis
==========  ==================  ===========  =========  ===================
baseline    COMPLETED           1.0000       0.9955     4.198 s
laya        COMPLETED           1.0000       0.9973     6.256 s
laya        COMPLETED           1.0000       0.9973     3.100 s
==========  ==================  ===========  =========  ===================

Las tres ejecuciones terminaron con código 0 y verificaron:

* Subida del PDF y descarga idéntica por enlace firmado sin sesión.
* Reintento con la misma Idempotency-Key sin crear otra solicitud.
* Paso real por RabbitMQ, extracción del PDF, inferencia y lectura del ranking.
* Decisión humana recuperada desde una nueva consulta a la API.
* Resultado oculto y nuevos enlaces al PDF rechazados tras retirar el consentimiento.
* Limpieza de la postulación y PDF creados por la prueba, y cierre de su vacante.

Los tiempos incluyen cola, extracción y consultas de sondeo. Son observaciones
de una ejecución baseline y dos ejecuciones Laya, no una comparación de rendimiento. La puntuación
y la confianza no miden exactitud ni calibración del modelo.

Laya con pesos reales
--------------------

Checkpoint: ``convaiinnovations/laya-multilingual``.

Revisión fijada: ``1720e3e3357cfe1e281542e223f8273b0890ca34``.

Versión guardada por Worksy:
``laya-ml@1720e3e3357cfe1e281542e223f8273b0890ca34``.

Imagen construida desde el checkout local de Laya con PyTorch CPU 2.14.0,
sin GPU y con cuatro hilos. Docker disponía de 12 CPU y aproximadamente 7.6 GiB.
Se descargaron y precargaron los pesos antes de iniciar las solicitudes.

Tres consultas HTTP directas adicionales con un texto sintético corto y un
criterio tardaron 0.685 s, 0.215 s y 0.237 s. Devolvieron la opción afirmativa
con probabilidad 0.9638. Estas consultas verifican funcionamiento y una latencia
puntual; no representan CV largos, concurrencia ni todos los criterios posibles.

Pendiente antes de un despliegue con datos reales
------------------------------------------------

Medir CV de distintas longitudes y varios criterios por vacante, evaluar errores
con datos de validación y ajustar la calibración. El adaptador rechaza texto
truncado y no cambia silenciosamente al proveedor básico ante un fallo de Laya.

La prueba repetible está en ``scripts/smoke_screening.py`` y las instrucciones en
``scripts/smoke-setup.rst``.
