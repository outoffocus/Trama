# Fase 0 — línea base y decisiones técnicas

Actualización posterior: se implementaron correcciones y se verificaron 595 tests. Ver [cambios y límites de validación](PHASE_0_FIXES_2026-09-07.md). Este documento conserva los hallazgos de la línea base; sus referencias a fallos sin corregir describen el estado anterior a ese lote.

Fecha: 7 de septiembre de 2026. Código base: `6176b92`.
Estado: iniciada; revisión estática y ejecución fresca de tests completadas. Pruebas físicas pendientes. No se ha modificado código de producción, bases de datos del usuario ni calendarios.

## 1. Entorno y alcance de la evidencia

- Proyecto Android con módulos `app`, `shared`, `wear`; Room v16 y exportación de esquema. Esquemas encontrados: 15 y 16.
- Móvil: minSdk 26, target/compileSdk 35; empaquetado nativo limitado a ARM64. Compatibilidad declarada no equivale a rendimiento de modelos validado.
- ADB arrancó y devolvió una lista vacía. No hay dispositivo conectado; no se ejecutó la app, no se midió Home y no se probaron permisos, alarmas o consumo real.
- El usuario confirmó Samsung S25+ y Galaxy Watch 4; versiones Android/Wear OS pendientes de lectura. No se fija aún un mínimo de RAM o una gama compatible con diarización sin mediciones.
- Primera invocación Gradle: correcta pero todas las tareas estaban actualizadas; no constituye una ejecución nueva. Ejecución fresca con `--rerun-tasks`: BUILD SUCCESSFUL, 33 s, 80 tareas ejecutadas. app: 347 tests; shared: 172; wear: 66. Total: 585, sin fallos, errores ni omitidos. Inventario de suites en `PHASE_0_TEST_RESULTS_2026-09-07.json`.

Comando reproducible desde la raíz del proyecto:

```sh
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest :wear:testDebugUnitTest --offline --rerun-tasks
```

Los tests unitarios no validan los modelos acústicos, batería, notificaciones del proveedor ni migración Room real en Android. La prueba instrumentada existente migra una base vacía v1 hasta v16; no demuestra conservación de registros reales a través de cada versión de origen.

## 2. Inventario y decisión inicial

| Componente | Decisión | Fundamento / condición |
|---|---|---|
| Room, repositorios y transacciones | Conservar y completar cobertura | Hay esquema y migraciones; falta validar registros, relaciones, confirmaciones y restauración. |
| Backup JSON v3 | Corregir antes de depender de él | Omite evidencia de confirmación y no transporta el audio de reuniones. |
| Compose y navegación temporal | Conservar tecnología; reorganizar presentación | Home mezcla operaciones de datos y control de servicios con UI. |
| PCM durable y workers de transcripción/recuperación | Conservar bajo pruebas de interrupción | El audio se conserva para reintentar; verificar corte, almacenamiento lleno y reinicio físico. |
| Gate Vosk + Whisper | Encapsular y medir antes de sustituir | Hay selección de ventanas y fallback a Whisper si falta gate; no hay evidencia nueva de precisión o bajo consumo de jornada. |
| Verificación de voz | Conservar solo para su propósito | Un embedding para comparación de voz no proporciona una transcripción diarizada. |
| Diarización | Incorporar capacidad nueva | El transcriptor une texto de ventanas; Recording no contiene segmentos/hablantes. |
| Calendar Provider | Conservar adaptador y corregir contrato de resultado | Evento insertado y aviso creado no se comprueban como resultados independientes. |
| AlarmClock como alternativa de fecha concreta | Reemplazar para ese caso | La rama inspeccionada traslada hora/minutos; no representa la fecha completa solicitada. |
| Lugares y estancias | Conservar captura; revisar resolución | Reutilización de lugar en radio de 80 m puede confundir locales vecinos. |
| Sincronización reloj | Conservar protocolo sujeto a pruebas | Transferencia, duplicados y propiedad de micrófono requieren dispositivo. |

## 3. Hallazgos priorizados

### F0-01 — la UI modifica la fecha de origen (alta)

`CalendarScreen.kt:310`: un LaunchedEffect selecciona pendientes del reloj sin fecha de vencimiento y creados fuera de hoy, y llama a `updateCreatedAt(id, now)`.

Consecuencia deducida del código: abrir Home puede mover al presente la historia de una captura. Separar fecha original de presentación de pendientes. Preparar prueba con registro del reloj de ayer y comprobar que consultar hoy no lo modifica.

### F0-02 — copia incompleta de confirmaciones (alta)

`DiaryEntry` contiene `userConfirmedAt` y `verificationSource`; `BackupEntry` y su conversión no los incluyen. La restauración no puede reconstruir esa evidencia.

Corregir serialización compatible con copias anteriores y comprobar exportación/importación desde repositorios reales. No inventar confirmaciones al leer una copia antigua.

### F0-03 — copia de reuniones sin audio (alta para recuperación)

`BackupRecording` guarda transcripción y metadatos, pero no archivos PCM. Una copia no permite recuperar el audio de una reunión pendiente de transcripción. No basta serializar una ruta local: hace falta un paquete que transporte archivos o declarar explícitamente el alcance de la copia. Definir también política de retención y no trasladar estados procesables a otra instalación si falta su fuente.

### F0-04 — éxito de evento no garantiza aviso (alta)

`CalendarHelper.kt:351–379`: el evento puede devolver identificador aunque falle el recordatorio, que captura el error internamente. La condición `reminderMinutes > 0` tampoco permite expresar aviso al inicio como un recordatorio explícito de cero minutos.

Modelar ausencia de aviso y cero minutos como valores distintos. Comprobar fila de recordatorio y comportamiento real del calendario elegido; no prometer alarma por la existencia de un evento.

### F0-05 — diarización no integrada (alcance pendiente)

`PcmRecordingTranscriber` procesa ventanas de 25 s y las concatena en un texto. Su resultado no expone hablantes ni segmentos temporales. `RecordingTranscriptionWorker` persiste `result.text`. No hay base para marcar esta capacidad completada.

Ensayo siguiente: segmentación y embeddings locales con agrupación de voces, alineación temporal con ASR y gestión de solapamientos. Evaluar licencia, tamaño y disponibilidad ARM64 de candidatos antes de integrar; no elegir un modelo solo por la existencia de una API.

### F0-06 — escucha prolongada sin validación actual (alta)

Existe servicio de micrófono, VAD, gate y Whisper con restricciones de batería. Son mecanismos implementados, no mediciones de fiabilidad. No se reutilizan cifras históricas como resultados de la versión actual.

Comparar dos condiciones: activación distintiva elegida y expresiones naturales («tengo que…», «faltan…»). Medir falsas activaciones y órdenes omitidas por separado; bajar un umbral para aumentar capturas puede empeorar la carga de revisión.

### F0-07 — diagnóstico incluye texto personal (revisar antes de piloto público)

`PcmRecordingTranscriber` emite texto y `transcriptPreview` en CaptureLog. Auditar retención/exportación antes de recoger diagnósticos de otras personas; las métricas de rendimiento no necesitan transcripciones por defecto.

## 4. Integraciones y límites comprobados documentalmente

### Hallazgos adicionales de procesamiento (revisión de código)

- **F0-08 — acciones de reunión entran como pendientes, no sugerencias (alta).** `RecordingProcessor.saveResult` construye entradas con `status = PENDING`, sin `userConfirmedAt`. Contradice el contrato detectada → propuesta → confirmada. No implica envío automático a Calendar, pero sí mezcla inferencias con tareas pendientes. Corregir estado de creación y revisar todas las consultas/controles consumidores.
- **F0-09 — guardado de reunión no atómico (alta).** `saveResult` marca COMPLETED antes de insertar las acciones una a una y no envuelve ambas operaciones en transacción. `process` omite reprocesado si una reunión figura COMPLETED y tiene alguna acción. Un corte después de la primera inserción puede dejar una reunión aparentemente terminada con acciones incompletas. `tryLocalModel` también borra acciones antes del nuevo guardado. Añadir prueba de fallo intermedio y transacción antes de confiar en reintentos.
- **F0-10 — fallo de recuperación puede parecer éxito del worker (media).** Si `finalizePending` falla, `RecordingRecoveryWorker` registra error y continúa; finalmente devuelve success sin actualizar ese registro ni programar un reintento propio. Queda pendiente de otra invocación externa. Definir fallo recuperable/terminal por sesión y estado visible.
- **F0-11 — resumen semanal no comprueba notificaciones habilitadas (media).** `WeeklyAgendaWorker` llama a notify y registra publicación/success sin verificar permiso/canal. En el S25+ el permiso de notificaciones de Trama estaba denegado. No equiparar fin del worker con entrega visible.

Orden técnico de corrección recomendado: F0-01/F0-02 (fechas y confirmaciones de copia), F0-08/F0-09 (propuestas y atomicidad de reunión), F0-04 (resultado de avisos), F0-10/F0-11 (recuperación y estado visible). Cada corrección debe incluir prueba de comportamiento específica. Estos hallazgos no se han corregido aún; la fase actual establece la línea base.

- **Keep:** la documentación oficial se dirige a administradores de empresa. El recurso notes enumera crear, obtener, listar y borrar, sin método para actualizar el contenido de una nota existente. No basar la compra del producto en añadir automáticamente a una lista personal existente. Decisión inicial: lista local y compartir/copiar de forma asistida, con destino veraz. [Descripción oficial](https://developers.google.com/workspace/keep/api/guides), [recurso notes](https://developers.google.com/workspace/keep/api/reference/rest/v1/notes).
- **Google Tasks:** la API conserva fecha de vencimiento, no permite leer/escribir la hora de vencimiento. No usarla como garantía de aviso a una hora concreta. [Recurso tasks](https://developers.google.com/tasks/reference/rest/v1/tasks).
- **Micrófono Android:** existe foreground service de tipo microphone, pero su inicio está condicionado por permisos while-in-use y restricciones de arranque desde segundo plano/reinicio. «Todo el día» es una intención de servicio iniciada por el usuario, no garantía de reinicio silencioso universal. [Tipos de servicio](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).
- **Calendar actual:** utiliza proveedor Android y sincronización de próximos 60 días. Pendiente probar proveedor/cuenta concreta, permisos revocados, cambios, eventos recurrentes y cancelaciones. No se escribieron eventos durante esta auditoría.

## 5. Protocolo físico y umbrales iniciales de decisión

Estos son objetivos de ingeniería propuestos, no cifras obtenidas ni garantías para todos los dispositivos. Mantener conjunto de evaluación separado del material usado para ajustar modelos.

| Ensayo | Método | Objetivo inicial |
|---|---|---|
| Día y captura manual | Los seis recorridos de producto con fechas/datos conocidos; reiniciar y recuperar | Sin pérdida/duplicación; diario y próximo compromiso reconocibles en unos 10 s. |
| Activación | 100 intentos por modalidad repartidos entre calma, calle y conversación; incluir frases negativas y TV | ≥95% de activaciones deliberadas detectadas; informar resultado por entorno. |
| Jornada sin órdenes | Al menos 8 h de uso representativo con referencia anotada | ≤1 activación falsa por 8 h y cero acciones confirmadas a partir de inferencias no autorizadas. |
| Feedback | Medir fin de activación a acuse y fin de orden a resultado | p95 acuse ≤1 s; p95 guardado/propuesta breve ≤5 s. No usar el acuse como prueba de envío externo. |
| Batería | Dos pares de sesiones de 8 h, con/sin escucha, mismo equipo y condiciones aproximadas | Incremento orientativo ≤5 puntos porcentuales por 8 h en móvil; reportar temperatura y factores de confusión. Medir reloj aparte. |
| Reunión | Sesiones de 10, 30 y 60 min con 2–4 voces, turnos y solapamientos; referencia consentida | Sin pérdida; DER objetivo ≤15% con protocolo publicado, sin forzar identidad en solapamientos. Separar error de ASR y atribución de acciones. |
| Recursos de reunión | Registrar tiempo, pico RAM y fallos por duración | Objetivo inicial procesamiento ≤duración del audio, sin OOM. Capacidad final condicionada a medición. |
| Migración/restauración | Copias sintéticas en instalación de prueba, relaciones y confirmaciones; reinicios/reintentos | Igualdad de contenido y procedencia, sin duplicados y con archivos disponibles cuando se prometen. |
| Calendar/avisos | Calendario de prueba elegido; fecha exacta, cero/30 min, edición y cancelación | Un evento, recordatorio correcto y estado de error visible si falla. |

No evaluar métricas acústicas reproduciendo únicamente texto de tests unitarios. No probar restauraciones destructivas sobre los datos diarios del usuario. Las pruebas de sistema requieren una instalación o datos de prueba y una copia verificada.

## 6. Próximo paso y criterio de cierre

### Cierre de la revisión estática inicial

Revisión de código inicial completada; la fase 0 completa sigue pendiente de los ensayos señalados. No es útil seguir ampliando indefinidamente el inventario antes de corregir los fallos de integridad identificados.

- **F0-12 — error de lectura confundido con calendario vacío (alta).** `CalendarHelper.queryEvents` captura errores y devuelve lista vacía o parcial; `GoogleCalendarSyncManager` elimina importaciones no presentes en esa lista. Un fallo transitorio puede borrar representaciones locales válidas, no los eventos del proveedor. Cambiar a un resultado que distinga éxito completo/error; reconciliar eliminaciones solo tras lectura completa. Pruebas: excepción, cursor nulo y fallo a mitad de lectura conservan importaciones; éxito vacío sí elimina las del ámbito consultado.
- **F0-13 — transferencia al reloj sin confirmación de persistencia en móvil (alta).** `WatchToPhoneSyncer.syncRecordingAudio` considera éxito al completar `putDataItem`; `WatchRecordingService` entonces borra el PCM local. No hay acuse de aplicación que certifique almacenamiento durable en el teléfono en esa ruta. El Data Layer puede conservar el Asset, pero su aceptación no prueba importación exitosa. Añadir identificador estable y acuse tras persistencia; mantener copia hasta ese acuse. Probar teléfono desconectado, error de importación y acuse repetido.
- **F0-14 — identidad de evento ligada a su hora (media).** `buildPayload` incluye startMillis y se usa como clave de igualdad. Reprogramar una ocurrencia puede reemplazar el registro local y perder estado asociado; diseñar identidad de ocurrencias recurrentes antes de añadir relevancia/anticipación persistente.
- **Conservar:** BootReceiver ya solicita reactivación explícita del micrófono tras reinicio y comprueba permisos antes de arrancar ubicación. `ServiceController.requestListenerStart` distingue fallo de inicio y solicita recuperación. No rehacer estas defensas sin motivo; probar su coherencia con la UI y notificaciones denegadas.

Primer lote de implementación recomendado: F0-12 + F0-01 + F0-02, con tests de error de proveedor, conservación de fecha y exportación/importación de confirmaciones. Segundo lote: F0-08/F0-09 y F0-13. Tercero: avisos y recuperación. El diseño F1 puede avanzar sobre estos contratos, sin declarar validadas precisión acústica ni autonomía.

### Continuación: revisión en código a petición del usuario

- Se amplió `DiaryDatabaseMigrationTest` con un registro sintético v1: comprueba texto, identificador, fecha original y origen tras migrar, y que no se inventa evidencia de confirmación. Compilación de AndroidTest correcta. Ejecución instrumentada pendiente: Gradle offline no dispone de dependencias UTP `32.0.1`; no es un fallo observado de migración. No se sustituye esta ejecución por el resultado de los 585 tests unitarios anteriores.
- Búsqueda actual: `SearchScreen` consulta desde dos caracteres y `DiaryDao.search` busca solo `text`/`cleanText` de entradas. No cubre lugares, opiniones o transcripciones de reuniones como fuentes independientes. Ampliar el contrato de recuperación antes de unificar Buscar/Chat.
- Transcripción: `RecordingTranscriptionWorker.retryOrFail` reintenta mientras `runAttemptCount < 3`, después marca FAILED. Verificado en código; no demuestra recuperación física de todos los tipos de interrupción.
- El usuario pide continuar prioritariamente con código y pruebas automatizadas, con comunicación concisa. No continuar navegación manual por el móvil salvo necesidad o solicitud posterior.

1. Completado: resultado fresco de 585 tests e inventario de suites.
2. Dispositivos confirmados: Samsung S25+ y Galaxy Watch 4. Pendiente conectar por ADB para inspección visual/funcional y leer versiones del sistema.
3. Ejecutar los ensayos cortos antes de una jornada de batería y grabaciones largas; registrar resultados en la plantilla adjunta.
4. Resolver primero integridad de fechas y copia; hacer sus correcciones en cambios separados con pruebas específicas antes de migrar la UI.
5. Elegir o descartar candidato de diarización local mediante ensayo y actualizar capacidades/dispositivos soportados.

Fase 0 no cerrada: siguen pendientes ejecución física, restauración real, rendimiento y ensayo de diarización. La inspección permite priorizar trabajo; no certifica una app lista para otros usuarios.
