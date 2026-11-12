Prueba del recorrido completo
============================

Integra primero los PR de ranking y adaptador Laya. Esta prueba usa únicamente
cuentas y un PDF sintéticos; no utiliza CV reales, claves locales ni los volúmenes
de la instalación de desarrollo. No publica puertos. Las contraseñas de este
Compose son datos sintéticos para este entorno aislado.

Comprueba subida y descarga del PDF con enlace firmado, solicitud idempotente,
análisis del worker, lectura del ranking, persistencia de la decisión humana y
retiro del consentimiento. Al terminar elimina su PDF y postulación y cierra su
vacante. Las dos cuentas sintéticas permanecen hasta eliminar el entorno.

Proveedor básico
----------------

Desde la raíz de Worksy, con Docker en ejecución::

    docker build -t plataforma-empleo/minio:RELEASE.2025-10-15T17-29-55Z infra/minio
    docker compose -f scripts/compose.smoke.yml up -d --build
    docker compose -f scripts/compose.smoke.yml --profile tools run --rm smoke

El resultado es JSON y el proceso termina con código 0 si pasó. Un error termina
con código 1 y el paso que falló. La prueba crea datos; ejecútala contra desarrollo
o contra este entorno separado. También puede ejecutarse con Python y httpx::

    python scripts/smoke_screening.py --api-url http://localhost:8080 --provider baseline

En ese caso, el equipo que ejecuta el script debe poder acceder al host que
``STORAGE_PUBLIC_ENDPOINT`` usa para los enlaces firmados.

Alternativa con los JAR compilados localmente
--------------------------------------------

Si Docker tarda descargando las dependencias de Maven, puedes aprovechar la caché
local. Compila los JAR y utiliza el archivo adicional de Compose. En PowerShell::

    Push-Location backend
    .\mvnw.cmd -pl api,worker -am package -DskipTests
    Pop-Location
    docker compose -f scripts/compose.smoke.yml -f scripts/compose.prebuilt.yml up -d --build
    docker compose -f scripts/compose.smoke.yml -f scripts/compose.prebuilt.yml --profile tools run --rm smoke

En Linux usa ``./mvnw`` desde backend. Esta alternativa empaqueta los JAR de
``backend/api/target`` y ``backend/worker/target``; vuelve a compilarlos si cambias
el código Java. No sustituye la ejecución de las pruebas del backend en CI.

Laya con pesos reales
--------------------

Construye primero la imagen CPU desde un checkout de Laya::

    docker build -t worksy-laya-cpu:review RUTA_AL_CHECKOUT_LAYA

Si Docker rechaza archivos de Windows marcados como reparse points, construye
desde un archivo Git. Con ambos repositorios en el mismo directorio padre::

    git -C ..\laya archive --format=tar --output=../laya-build-context.tar HEAD
    cmd /c "docker build -t worksy-laya-cpu:review - < ..\laya-build-context.tar"

La primera ejecución descarga los pesos multilingües y los conserva en el volumen
``worksy-smoke_laya-model-cache``. La revisión está fijada en Compose; si cambias
el checkpoint, cambia también el identificador de versión. En PowerShell::

    $env:SMOKE_PROVIDER = 'laya'
    $env:SMOKE_LAYA_BASE_URL = 'http://laya:8000'
    docker compose -f scripts/compose.smoke.yml --profile laya up -d --build
    docker compose -f scripts/compose.smoke.yml logs -f laya

Espera a que Laya termine la descarga y la precarga. Sal de los registros con
Ctrl+C y ejecuta la prueba::

    docker compose -f scripts/compose.smoke.yml --profile tools run --rm smoke

Si usaste los JAR locales, conserva también ``-f scripts/compose.prebuilt.yml``
en los comandos de arranque y ejecución de Laya.

El script exige que la solicitud use el proveedor indicado. Un fallo de Laya
no puede pasar como un resultado baseline. El resultado incluye modelo,
puntuación, confianza y tiempo de análisis. El tiempo incluye cola, extracción y
consulta a inferencia; no es un benchmark aislado del modelo. Un único CV verifica
el recorrido técnico, no demuestra exactitud del modelo ni su calibración.

Cerrar el entorno
-----------------

Estos comandos eliminan exclusivamente los contenedores y volúmenes del proyecto
de prueba, incluido el caché del modelo si se usa ``--volumes``::

    docker compose -f scripts/compose.smoke.yml --profile laya --profile tools down

Sin ``--volumes`` se conservan los pesos para repetir la prueba. El Compose de
desarrollo en ``infra/docker-compose.yml`` tiene otro nombre de proyecto y no se
modifica con este comando.
