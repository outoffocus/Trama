# Plan de mejora de TRAMA

Este documento convierte la auditoría técnica en entregas pequeñas y verificables.
Cada fase debe terminar con compilación, pruebas y Android Lint en verde antes de
avanzar a la siguiente.

## Fase 1 — Ciclo de vida y permisos (completada)

- Sustituir el arranque de micrófono desde `BOOT_COMPLETED` y alarmas por una
  reactivación iniciada por el usuario desde una notificación.
- Separar la preferencia persistida de escucha del estado real del servicio.
- Publicar los estados `STOPPED`, `STARTING`, `LISTENING`, `PAUSED` y `FAILED`.
- Comprobar permisos de micrófono, notificaciones y ubicación antes de acceder
  a APIs protegidas tanto en teléfono como en reloj.
- Dejar `testDebugUnitTest` y `lintDebug` en verde en todos los módulos.

## Fase 2 — Captura automática determinista (completada)

- Introducir un `captureId` único y un sobre inmutable para cada ventana de audio.
- Serializar los cambios de estado del capturador mediante un actor/canal.
- Evitar que una detección pendiente pueda asociarse a la transcripción de otra
  ventana.
- Tomar los límites de pre/post-roll del instante de detección, no del instante
  posterior en el que termina el ASR.
- Añadir pruebas de concurrencia, cancelación, rearmado y ventanas solapadas.

Implementado: el gate entrega sus resultados al único propietario del estado de
captura, cada ventana transporta su `captureId` y su transcripción de gate, y las
finalizaciones y ejecuciones de Whisper se procesan secuencialmente. La validación
de rearmado con audio real queda incluida en la prueba física pendiente.

## Fase 3 — Grabaciones duraderas (completada)

- Escribir PCM/WAV progresivamente a un archivo temporal en vez de acumularlo
  en memoria.
- Crear el registro de base de datos al comenzar, con estado pendiente.
- Transcribir con trabajo único reintentable y conservar el audio hasta éxito o
  confirmación de sincronización.
- Recuperar grabaciones incompletas después de cierre forzado o reinicio.

Implementado: teléfono y reloj escriben PCM16 progresivamente en almacenamiento
interno y finalizan los ficheros mediante renombrado atómico. La fila Room se crea
antes de capturar o importar el audio, usando los estados `CAPTURING` y
`TRANSCRIBING`. La transcripción lee ventanas acotadas desde disco y se ejecuta
como trabajo único reintentable; otro trabajo recupera capturas interrumpidas al
arrancar o abrir la aplicación. El audio se conserva cuando falla el ASR y se
elimina explícitamente al borrar la grabación.

## Fase 4 — Calidad de detección (completada)

- Sustituir coincidencias `contains` por límites de palabra y candidatos puntuados.
- Evaluar categoría, contexto gramatical, hablante y confianza antes de aceptar.
- Construir un corpus etiquetado con diagnósticos reales y medir precisión,
  exhaustividad, falsos positivos y coste de ASR.

Implementado: el detector compartido evalúa todos los candidatos mediante límites
léxicos, longitud del trigger, categoría, complemento y contexto de propiedad. Cada
resultado incluye confianza, trigger ganador y razones diagnósticas. La decisión de
guardado combina esa confianza con propiedad gramatical y verificación del hablante;
los casos débiles se conservan como sugerencias. El gate fonético del reloj exige
una señal de intención suficiente y ya no acepta verbos accionables aislados. Un
corpus de regresión etiquetado mide precisión, exhaustividad y errores de categoría;
los diagnósticos persistentes incorporan las señales necesarias para ampliarlo con
feedback real de uso.

## Fase 5 — Persistencia y trabajos idempotentes (completada)

- Eliminar migraciones destructivas y exportar esquemas Room.
- Probar la cadena completa de migraciones hasta la versión actual.
- Hacer atómicos los backups y completar todas las entidades y relaciones.
- Usar trabajo único y transacciones al reprocesar grabaciones.

Implementado: Room exporta y versiona su esquema, la cadena 1→16 dispone de una
prueba Android que crea una base v1 real y valida el esquema final, y se han
eliminado todos los fallbacks destructivos. El backup v3 toma una instantánea
transaccional de las seis entidades, preserva y reconstruye relaciones, escribe
primero una copia local sincronizada y restaura dentro de una única transacción.
Los backups inmediatos, las transcripciones y el procesamiento usan trabajo único.

## Fase 6 — Mantenibilidad, distribución y seguridad (completada)

- Dividir servicios, procesadores y pantallas de gran tamaño por responsabilidad.
- Completar la inyección de dependencias y usar colecciones conscientes del ciclo
  de vida en Compose.
- Descargar modelos bajo demanda y generar artefactos por ABI.
- Definir reglas de copia de seguridad y almacenamiento seguro de credenciales.
- Añadir integración continua para compilación, pruebas, lint y migraciones.

Implementado: las responsabilidades críticas se han separado en almacenamiento,
captura, transcripción, recuperación, políticas de aceptación y escritura de
backup; ViewModels y dependencias compartidas usan Hilt. Todas las colecciones de
Compose son conscientes del ciclo de vida. Gemma continúa descargándose bajo
demanda y los binarios nativos se restringen a las ABI compatibles; Whisper se
mantiene incluido deliberadamente para garantizar grabación y ASR offline desde el
primer inicio. Las claves se cifran con AES-GCM y Android Keystore, y el backup
automático del sistema está deshabilitado para diario, audio y credenciales. CI
ejecuta pruebas, lint, builds, control de deriva de esquema y migración 1→16 en un
emulador.

## Fase 7 — Captura por intención compacta (implementada; calibración física pendiente)

- Sustituir las 461 frases expandidas por un preset corto, gramática estructural y
  un vocabulario de acciones independiente.
- Versionar el preset y compactar preferencias heredadas sin perder categorías o
  frases creadas por el usuario.
- Hacer persistentes las eliminaciones de frases base y ofrecer restauración de la
  configuración recomendada.
- Separar el gate de captura de la clasificación posterior de Recordatorio, Tarea,
  Comunicación o Compromiso.
- Incorporar perfiles Estricto, Equilibrado y Sensible, sincronizados con Wear.
- Medir transcripciones, guardados, borrados sospechosos, triggers dominantes y
  candidatos del siguiente perfil ejecutado en modo sombra.
- Validar matrices de acciones, negaciones, tercera persona, habla reportada,
  hipótesis, errores ASR y límites léxicos.

Implementado: el preset v2 contiene 22 frases explícitas y el detector combina
estructuras gramaticales con un vocabulario normalizado de más de 60 verbos. El
perfil Estricto exige propiedad y complemento; Equilibrado tolera drift del ASR y
formas impersonales, y Sensible incorpora tercera persona y palabras individuales,
siempre marcándolas como débiles. La categoría se calcula después de la
transcripción final. El siguiente perfil más permisivo se ejecuta en sombra sin
activar Whisper y sus candidatos aparecen en el diagnóstico local. Queda pendiente
medir falsas capturas por hora y batería durante jornadas reales de teléfono y
reloj antes de retocar umbrales o vocabulario.

## Fase 8 — Compatibilidad Android con páginas de 16 KB (completada)

- Identificar por separado fallos de alineación del APK y de segmentos ELF.
- Actualizar las dependencias nativas incompatibles sin ocultar el aviso mediante
  modos de compatibilidad.
- Validar los APK finales de teléfono y reloj.
- Impedir regresiones mediante una comprobación reproducible en CI.

Implementado: Vosk Android se actualizó de `0.3.47` a `0.3.75`, lo que corrige
`libvosk.so` ARM64 y eleva JNA a `5.18.1`. Todos los segmentos `PT_LOAD` de
`arm64-v8a` incluidos en los APK de teléfono y reloj están alineados a 16 KB o
más, y ambos APK superan `zipalign -P 16`. Wear mantiene `armeabi-v7a` para no
retirar soporte a relojes antiguos; el requisito de 16 KB cubre `arm64-v8a` y
`x86_64`. El script `scripts/check-16kb-alignment.sh` documenta la comprobación y
el workflow de CI la ejecuta después de construir ambos APK.

## Fase 9 — Claridad de navegación y ajustes (implementada; validación visual pendiente)

- Conservar `CalendarScreen` como Home y mantener la navegación por días y meses.
- Garantizar un acceso visible a todas las rutas públicas.
- Separar ajustes cotidianos de controles técnicos sin eliminar funcionalidad.
- Reagrupar las opciones según objetivos del usuario, no según componentes
  internos.
- Sustituir vocabulario técnico por consecuencias comprensibles.

Implementado: la fecha seleccionada pasa a ser el título de Home, Búsqueda y Chat
son acciones visibles y el menú ofrece notas, lista de grabaciones y Ajustes.
Agenda conserva su acceso en la barra temporal. El día y mes seleccionados usan
estado restaurable al navegar a detalles. Ajustes presenta Captura y contexto,
Agenda y calendarios, Privacidad y copias y Apariencia; IA/modelos, audio,
ubicación y diagnóstico requieren activar `Mostrar opciones avanzadas`. Backup y
reconocimiento de la propia voz dejan de estar enterrados en Avanzado. Queda
pendiente validar densidad, truncado y comprensión en dispositivos físicos.

## Fase 10 — Contexto ambiental separado de tareas (implementada; calibración física pendiente)

- Hacer la función explícitamente opt-in y local.
- Clasificar ambiente en pocas categorías, no en frases ni tareas.
- No persistir audio ni transcripciones ambientales.
- Agrupar señales y limitar el número de bloques diarios.
- Permitir horario y exclusiones por Casa/Trabajo.
- Mantener el audio de aplicaciones del dispositivo siempre excluido.
- Exponer resultados y exclusiones en diagnóstico.

Implementado: las ventanas `uncertain_fallback` pueden producir bloques de Música,
Televisión/radio, Conversación o Reunión. Las frases de intención quedan protegidas,
las señales iguales se agrupan 45 minutos, los cambios tienen 15 minutos de cooldown
y el máximo es 12 bloques nuevos al día. La cronología reutiliza `TimelineEvent`,
por lo que Room v16 no necesita migración. Queda pendiente medir precisión y coste
con jornadas reales etiquetadas; ver `docs/AMBIENT_CONTEXT.md`.
