Laya en Worksy
=============

Worksy consulta el servidor local de Laya por HTTP, sin importar torch en el
servicio FastAPI. El contrato se verificó en ``laya/laya/serve.py`` del checkout
local: ``POST /v1/systemone``, con ``model``, ``state``, ``questions`` y ``max_len``.

Requisitos
----------

Ejecuta un servidor Laya en tu propio equipo o red. Desde su repositorio,
instalado con el extra ``serve``, puedes usar PowerShell::

    $env:LAYA_PORT = '8002'
    $env:LAYA_MODELS = 'multilingual'
    $env:LAYA_PRELOAD = '1'
    $env:LAYA_DEVICE = 'cpu'
    laya-serve

La primera carga necesita los pesos y puede tardar; esta integración no los
descarga ni los incluye en la imagen de Worksy. Espera a que termine y prueba
una predicción antes de seleccionar Laya. El puerto 8002 evita chocar con el
servicio de inferencia de Worksy (8000).

Configuración
-------------

En el archivo local ``.env`` de Worksy::

    SCREENING_PROVIDER=laya
    LAYA_BASE_URL=http://host.docker.internal:8002
    LAYA_MODEL=multilingual
    LAYA_MODEL_VERSION=laya-ml@revision-de-tu-checkpoint
    LAYA_TIMEOUT_SECONDS=4
    LAYA_MAX_LEN=8192

Sustituye la versión por un identificador del checkpoint desplegado; se guarda
con los resultados (máximo 64 caracteres). El servidor no devuelve un identificador
de pesos fiable en todas sus respuestas; Worksy no inventa uno. Debes actualizar
este identificador si cambias los pesos o la calibración. Si Laya tiene
``LAYA_API_KEY``, configura la misma clave en Worksy. No subas el archivo .env.

``host.docker.internal`` se usa desde Docker. Si ejecutas inferencia directamente
en tu PC, usa ``http://localhost:8002``. La URL es una configuración del operador,
nunca un dato del usuario. Los CV se envían a ese servidor, sin redirecciones.

Recrea los servicios desde la raíz de Worksy::

    docker compose -f infra/docker-compose.yml --env-file .env --profile dev up -d --build api inference screening-worker

El servicio screening-worker forma parte del PR de ranking; debe estar integrado
para procesar las solicitudes. El tiempo del adaptador (4 s) debe ser menor que
el del worker (5 s). Para utilizar CPU con CV largos, mide la latencia antes de
activarlo. Un timeout produce un error, nunca un resultado del proveedor básico.

Decisiones y fallos
------------------

Las preguntas choice mantienen sus opciones. Se usa ``answer_confidence`` cuando
está disponible, o la probabilidad de la opción elegida en modo estricto; el campo
``confidence`` de Laya es entropía y no se utiliza como probabilidad.

Las preguntas score usan los niveles enviados en criteria (en orden) o una escala
de cinco niveles. El score de Laya, entre 0 y N-1, se normaliza a 0..1.
La calibración de las probabilidades con datos de validación sigue siendo necesaria
antes de interpretar la confianza como exactitud. La decisión final es humana.

Sin configuración: 501. Servidor inaccesible, timeout o autenticación rechazada:
503. Respuesta incompleta: 502. Preguntas rechazadas o texto truncado: 422.
No se guardan resultados aparentemente completos de un CV que Laya haya recortado.
Los errores no incluyen el texto del CV, respuestas internas ni claves.
``/healthz`` solo comprueba el proceso de inferencia; no garantiza disponibilidad
del servidor Laya ni que sus pesos estén cargados.

Validación
----------

``python -m pytest`` comprueba la traducción del contrato y los errores con un
transporte HTTP sintético, sin pesos ni GPU. La prueba opcional contra el servidor
real de Laya usa un router sintético y verifica el formato, no la calidad del modelo::

    # Señala la raíz del checkout de Laya antes de ejecutar las pruebas.
    $env:LAYA_SOURCE_DIR = '..\..\laya'
    python -m pytest

Una prueba de inferencia con pesos reales y medidas de exactitud y latencia es
necesaria en el equipo donde se vaya a desplegar.
