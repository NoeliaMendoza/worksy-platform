CV que superan el límite de texto
================================

El worker admite hasta 100.000 caracteres de texto extraído del PDF, después
de quitar los espacios exteriores. Si el texto supera ese límite, el análisis
queda FAILED sin consultar al proveedor de inferencia ni guardar un ranking
parcial. El bloqueo se libera y el mensaje se marca como procesado para evitar
repetir un fallo definitivo por una reentrega de RabbitMQ.

El registro del worker indica que el documento requiere revisión manual, sin
incluir su contenido. La API conserva el estado FAILED y expone el código
CV_TEXT_TOO_LONG a la empresa propietaria. La pantalla de postulaciones muestra
el motivo y recomienda revisar el CV manualmente. Reintentar el mismo documento
no resuelve el exceso.

Este límite es independiente del límite de tokens de Laya. Un documento más
corto también puede exceder la ventana del modelo; el adaptador de Laya rechaza
las respuestas que declaran truncamiento.

Las pruebas ScreeningProcessorTest comprueban el rechazo por encima del límite,
la ausencia de llamadas a inferencia y de resultados parciales, la liberación
del bloqueo, la deduplicación y el envío íntegro de un texto en el límite.
