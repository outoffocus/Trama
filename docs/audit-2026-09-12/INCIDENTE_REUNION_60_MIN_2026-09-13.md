# Incidente: reunión de 60 minutos

**Fecha del diagnóstico:** 13 de septiembre de 2026.
**Dispositivo:** Samsung SM-S936B. **Grabación afectada:** ID 5.

## Evidencia

- El PCM sigue presente en el almacenamiento privado y ocupa 110 MB, tamaño coherente con 60 minutos de audio mono PCM a 16 kHz.
- El transcriptor calculó 144 fragmentos de 25 segundos.
- Los fragmentos observados tardaron entre 7 y 22 segundos en decodificarse.
- WorkManager detuvo el trabajo a las 21:09:48 y 21:12:43 mediante `onStopJob` y lo reprogramó.
- Tras cada interrupción, la versión instalada volvió a inicializar Whisper y comenzó de nuevo en el fragmento 0.
- La cancelación llegó al worker como `JobCancellationException` y se registró erróneamente como fallo de transcripción.
- La escucha continua ejecutó Whisper durante el mismo intervalo, compitiendo por CPU y memoria con la reunión.

## Causa

La transcripción era reiniciable, pero no reanudable. Una reunión de una hora necesita decenas de minutos con Whisper Small en este dispositivo; cualquier interrupción de JobScheduler hacía perder todo el avance. Los reintentos acumulaban backoff y el control manual usaba `KEEP`, por lo que no podía reiniciar un trabajo todavía encolado. Después, una transcripción completa tampoco cabía con seguridad en una única petición del modelo local.

## Corrección

- Checkpoint atómico después de cada fragmento y reanudación desde `nextChunkIndex`.
- La cancelación del scheduler se propaga como cancelación y no cambia el estado a `FAILED`.
- Transcripción y análisis se ejecutan como trabajo prolongado en primer plano.
- El reintento explícito usa `REPLACE` para reiniciar el backoff conservando checkpoint y PCM.
- Las instancias de Whisper quedan serializadas para evitar inferencias simultáneas.
- Las transcripciones largas se analizan en segmentos de hasta 6.000 caracteres y se combinan al final.
- Si el análisis agota sus reintentos pero existe transcripción, el estado final es `TRANSCRIPT_ONLY` y el contenido sigue accesible.

## Validación

- `:app:testDebugUnitTest`: 392 pruebas, 0 fallos.
- `:shared:testDebugUnitTest` y `:wear:testDebugUnitTest`: correctos.
- `:app:lintDebug`: 0 errores.
- `:app:assembleDebug`: correcto.
- APK preparada en `app/build/outputs/apk/debug/app-debug.apk`.

La APK corregida aún no se ha instalado y la grabación original no se ha modificado.
