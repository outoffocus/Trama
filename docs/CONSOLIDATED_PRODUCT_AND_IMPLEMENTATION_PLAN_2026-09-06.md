# Trama — plan consolidado de producto e implementación

Fecha: 6 de septiembre de 2026.
Estado: plan solicitado por el usuario; no se ha iniciado la implementación.

Actualización del 7 de septiembre: [plan de renovación de experiencia y arquitectura](IMPLEMENTATION_ROADMAP_2026-09-07.md). Ese documento prevalece en orden de ejecución y navegación, incorpora escucha opcional durante todo el día como recorrido principal de voz y mantiene reuniones con notas, diarización y acciones como alcance requerido.

Revisión: alcance generalizado. Los ejemplos personales sirven como escenarios de validación; no definen módulos, relaciones personales, categorías obligatorias ni reglas fijas del producto.

Este documento reúne las necesidades expresadas en la conversación y establece el orden de trabajo propuesto. Para el alcance de este nuevo desarrollo prevalece sobre las recomendaciones de las dos evaluaciones anteriores. Conserva el calendario diario como entrada y la dirección visual Editorial serena. No modifica por sí solo código, datos, calendarios, listas externas ni avisos del usuario.

## 1. Objetivo y prioridades

**Trama debe permitir ver dónde transcurrió el día, conservar experiencias y recomendaciones, convertir lo que surge en acciones y anticipar compromisos futuros.**

El diario de lugares y la anticipación son los dos ejes principales. La captura rápida, las listas y la recuperación sirven a esos ejes. La app debe ser útil sin escribir una reflexión nocturna y sin mantener el micrófono encendido todo el día.

Orden de prioridad:

1. Integridad de datos e integraciones verificadas.
2. Lugares del día y compromisos que condicionan hoy, mañana y los próximos días.
3. Captura deliberada y resultado correcto: nota, compra, tarea, cita o aviso.
4. Recomendaciones y experiencias recuperables por lugar, persona, tipo o fecha.
5. Comodidad adicional: accesos rápidos, preparación de viajes y manos libres opcional.

La primera entrega útil debe mostrar tanto el día vivido como lo próximo importante; no limitarse a una nueva lista de tareas.

### Público y alcance común

El producto se dirige a personas que quieren recordar experiencias y organizar lo que les importa, con independencia de su profesión, situación familiar o hábitos. Debe servir a quien vive solo, comparte responsabilidades, estudia, trabaja con horarios variables o viaja, sin exigir seleccionar uno de esos perfiles.

El núcleo utiliza conceptos comunes: **lugar, visita, nota, recomendación, lista, tarea, evento y aviso**. Las relaciones con personas, los calendarios compartidos y las categorías son opcionales. No habrá una entidad «guardia de la pareja», un módulo de citas médicas ni una lógica exclusiva para restaurantes.

La personalización inicial se limita a elegir fuentes, destinos, colecciones y preferencias de anticipación. No se construye un editor general de automatizaciones. La primera implementación sigue siendo Android; ampliar público no implica prometer otros sistemas operativos en esta entrega.

Sin cuenta Google, calendario compartido, reloj o permiso de ubicación, deben seguir disponibles las funciones locales correspondientes: notas, recomendaciones, listas si se habilitan y registro manual de visitas. Se muestra únicamente la configuración necesaria para las funciones elegidas.

## 2. Casos de uso y criterios de aceptación

P0: primera versión útil. P1: siguiente incremento necesario para completar la visión. P2: evolución condicionada por evidencia de uso. Las dependencias técnicas se resuelven antes de la función correspondiente.

| ID | Prioridad | Caso | Resultado y comprobación |
|---|---|---|---|
| U01 | P0 | Abrir el día y recordar dónde estuve. | Timeline con lugares, llegada/salida y duración aproximadas. Elegir otro día sin perderlo al volver de un detalle. Identificar los sitios principales en unos 10 segundos en prueba moderada. |
| U02 | P0 | Corregir una estancia equivocada o no identificada. | Cambiar lugar, ajustar intervalo, añadir una visita omitida o descartar un tramo. No fusionar automáticamente dos establecimientos vecinos por cercanía. Conservar fecha original y procedencia de corrección. |
| U03 | P0 | Tener presentes eventos propios o compartidos que afectan a mi organización. | Destacar eventos elegidos como relevantes, su siguiente ocurrencia y si afectan a hoy/mañana. Admitir fuentes propias o compartidas ya accesibles, sin duplicar eventos ni inferir relaciones personales. |
| U04 | P0 | Anticipar compromisos futuros y su preparación. | Vista de próximos siete días y del siguiente compromiso relevante; preparación del día siguiente y aviso puntual según preferencias. Un cambio o cancelación elimina avisos obsoletos. |
| U05 | P0 | Registrar una cita o evento con fecha y hora. | Revisar fecha resuelta, hora, calendario de destino y aviso; crear un único evento y poder abrirlo en el calendario elegido. Si falta claridad, preguntar solo el dato necesario. |
| U06 | P0 | Registrar una acción que debo realizar más adelante. | Guardar tarea; ofrecer hora de aviso o una franja previamente elegida. Nunca afirmar que tiene una alarma si solo se guardó una fecha. Editar/completar cancela o reajusta el aviso propio. |
| U07 | P1 | Añadir uno o varios elementos a una lista. | Incorporarlos a la lista elegida, con acuse, edición, deduplicación apropiada y Deshacer. Permitir lista local o destino compatible; la integración inicial con Keep queda sujeta a I01. |
| U08 | P0 | Guardar algo que no es una tarea. | Nota literal persistida y recuperable por fecha/texto, aunque la IA no extraiga nada o el modelo no esté instalado. |
| U09 | P1 | Conservar una opinión o experiencia asociada a un lugar o visita. | Comentario y valoración opcionales. No pedir opinión en cada estancia ni convertir la ubicación en una valoración. |
| U10 | P1 | Recuperar lugares conocidos y experiencias en una zona. | Búsqueda por ciudad/tipo y respuesta del chat con lugares visitados, fechas y opiniones propias; separar recomendados aún no visitados. Abrir cada fuente. |
| U11 | P1 | Guardar una recomendación o algo que quiero explorar. | Guardar título, tipo, procedencia, comentario original, enlace y lugar si se conocen. La fuente puede ser una persona, web o iniciativa propia. Campos ausentes vacíos; clasificación editable sin formulario obligatorio. |
| U12 | P1 | Recuperar recomendaciones por contenido, procedencia o estado. | Buscar por texto, persona opcional, tipo, colección o estado. Mostrar el registro original y evitar mezclar elementos con título parecido. |
| U13 | P1 | Conservar una actividad u oportunidad que me interesa. | Guardar plan de interés con fecha/enlace solo cuando se conocen. Convertirlo en acción o compromiso únicamente al decidirlo, sin asumir asistencia o participación. |
| U14 | P1 | Recordar que debo tomar una decisión o preparar algo. | Fecha de decisión/preparación independiente de la fecha del evento. Aviso elegido por el usuario, sin inventar plazos, disponibilidad ni pasos necesarios. |
| U15 | P2 | Recuperar información cuando un lugar o actividad vuelve a ser relevante. | Al consultar un destino o colección, reunir experiencias y recomendaciones pertinentes. Propuesta contextual dentro de la app antes de introducir avisos automáticos por proximidad. |
| U16 | P2 | Capturar sin abrir toda la app. | Widget, acceso rápido o reloj con un gesto y resultado verificable. Activación por voz solo tras superar el ensayo definido en la segunda evaluación. |

## 3. Experiencia principal

### Inicio: Mi día

Se conserva fecha, navegación por días y selector mensual. La jerarquía propuesta es:

1. **Lo próximo importante:** próximo evento, tarea o fecha de decisión relevante según proximidad y preferencias explícitas; breve y sin ocultar el día seleccionado. En días históricos, las referencias al presente se etiquetan expresamente.
2. **Tu recorrido:** estancias del día en orden, con fuente y correcciones. Calendario integrado visualmente como «previsto», sin afirmar asistencia.
3. **Lo que guardaste:** notas y acciones del día; recomendaciones enlazadas a su ficha.
4. **Añadir:** acceso visible para hablar o escribir. Grabar reunión queda como acción secundaria explícita.

«Próximos días», «Lugares», «Recomendaciones» y búsqueda tienen accesos estables. No se añaden de entrada cinco pestañas nuevas ni se abandona el calendario. Se validará un croquis de navegación antes de repartir información por nuevas pantallas.

En Inicio se muestran pocas propuestas pendientes y agrupadas por fuente cuando procedan de una reunión. La bandeja no se utiliza como destino de ruido ambiental. La captura deliberada se guarda aunque no tenga clasificación.

### Lugares y experiencias

La primera vista es una lista cronológica, más fácil de leer que una ruta de coordenadas. Mapa opcional desde el detalle. En la ficha de lugar: nombre, ciudad, visitas, opiniones propias y recomendaciones recibidas, separadas.

Una visita puede tener una opinión concreta y el lugar una valoración general. No sobreescribir todas las visitas cuando cambia la opinión actual. Una recomendación ajena conserva su procedencia y no se convierte en valoración del usuario ni prueba de visita.

Para una estancia ambigua: «Lugar por identificar», con selección/corrección sencilla. Identificar automáticamente el establecimiento equivocado es peor que conservar honestamente una visita sin nombre.

### Captura

Hablar o escribir una vez; mostrar contenido, destino y resultado. Accesos opcionales a una lista habitual o a crear una tarea aportan contexto sin exigir una clasificación antes de cada nota. La compra es un ejemplo de lista, no el único tipo permitido.

- Orden local inequívoca y reversible: guardar y ofrecer Deshacer.
- Dictado genérico: guardar nota y proponer conversión; el toque de dictado no autoriza todas las acciones que infiera la IA.
- Escritura en una aplicación conectada: revisión concreta de contenido/destino/fecha, con un único botón de confirmación que ejecuta la acción.
- Ambigüedad de título, producto, persona, día u hora: aclaración breve; conservar mientras tanto la captura.
- Fallo de red o integración: «Guardado en Trama; pendiente de enviar». No mostrar «Añadido a Keep/Calendar» antes de comprobarlo.
- Estado de transferencia del reloj y estado de guardado en el teléfono distintos. Una vibración no sustituye el acuse de qué se añadió.

No se requiere Gemma para guardar texto, consultar lugares, leer calendario o programar reglas simples. ASR/LLM local se reserva a capturas deliberadas y consultas que lo necesiten. La escucha ambiental permanente no es dependencia de estas funciones.

## 4. Anticipación de eventos, tareas y fechas relevantes

El comportamiento común depende de cuándo ocurre algo, si requiere preparación y qué importancia le ha dado la persona. No depende de que el evento sea médico, familiar o laboral. Los calendarios compartidos son una fuente opcional, no un requisito.

Tres momentos con funciones diferentes:

| Momento | Superficie | Comportamiento propuesto |
|---|---|---|
| Organizar la semana | Vista de siete días y resumen semanal opcional. | Mostrar eventos, tareas con fecha y decisiones próximas; destacar lo marcado como importante. |
| Preparar el siguiente día | Una notificación agrupada opcional en el horario elegido. | Resumir compromisos y acciones preparatorias registradas. La hora se adapta a las preferencias, sin asumir jornada diurna. No inventar transporte, cuidados o documentación necesaria. |
| Actuar a una hora | Aviso específico de Calendar o aviso propio explícitamente elegido. | Recordar el compromiso o acción en el momento pactado, con acceso a origen y posposición cuando proceda. |

El vistazo al día estará disponible en Home/widget. No se activan por defecto dos resúmenes diarios más alertas para todo. El primer ajuste de anticipación permite elegir un resumen diario, semanal o ninguno y su horario; los avisos concretos se gestionan aparte.

### Preferencias de relevancia y anticipación

1. Mostrar de base los próximos eventos de las fuentes seleccionadas, sin exigir reglas. Permitir destacar un evento concreto o todas las ocurrencias de una serie.
2. Opcionalmente aplicar una preferencia a un calendario o a coincidencias de texto, con previsualización. No incluir palabras clave de profesiones, parentescos o actividades como reglas obligatorias.
3. Elegir si se incluye en resúmenes, con qué antelación avisar y si hay una acción preparatoria explícita. Ofrecer ajustes sencillos, no un motor de reglas general.
4. Mostrar eventos en curso, siguiente ocurrencia y próximos días, con inicio/fin si constan. Tratar intervalos que cruzan medianoche, día completo, repeticiones y excepciones sin inventar horas.
5. «Ya lo tengo previsto» retira insistencia de planificación; no marca el evento completado ni cancela un aviso puntual elegido aparte. Un cambio significativo puede volver a destacarlo una vez.
6. Respetar acceso de solo lectura en calendarios compartidos: las preferencias son locales. La relación con quien figura en el evento no determina permisos ni relevancia automáticamente.

### Control del ruido y frescura

- Una sola entidad de calendario alimenta timeline, resumen y alertas; no se crea otra copia del evento en Google para destacarlo.
- Identidad por evento/ocurrencia y regla; evitar avisos repetidos al sincronizar.
- Recalcular después de cambio/cancelación y distinguir fecha original de una instancia recurrente de su nueva hora.
- Mostrar última actualización y problema accionable si el calendario deja de ser accesible. No afirmar «agenda despejada» si la lectura falló.
- Usar el calendario como fuente de fechas. Regenerar resúmenes y avisos desde el dato actualizado, no desde un texto de IA antiguo.
- Lectura al abrir, observación del proveedor mientras corresponda y trabajo diferible de reconciliación. Ninguno de ellos acredita sincronización remota instantánea: validar la frescura real del proveedor Google del dispositivo.

## 5. Recomendaciones y planes

Una biblioteca de recomendaciones con búsqueda y filtros por tipo, colección y estado. **Leer**, **Ver**, **Lugares** y **Planes** son colecciones sugeridas, que se pueden renombrar, ocultar o ampliar. No se exige encajar todo en cuatro categorías. Son vistas sobre registros únicos, no copias que mantener.

Cada recomendación conserva como mínimo texto original, título si se identifica, fecha y procedencia. Tipo/colección, persona que recomienda, localidad, URL y estado son opcionales. No exigir carátulas, búsqueda en catálogos o enriquecimiento remoto para guardar.

Estados básicos pendiente/en curso/realizado/descartado, con etiquetas adecuadas al tipo cuando aporten claridad, como «leído» o «visitado». Marcar realizado no inventa una fecha, visita física ni valoración. «Visitado» no significa «me gustó».

Transiciones de ejemplo:

- Recomendación de restaurante → visita detectada/corregida → opinión propia → recuperación futura por ciudad.
- Recomendación de libro → lectura → comentario personal; conservar quién lo recomendó.
- Noticia de concierto → interés → aviso para decidir/comprar → entrada adquirida/decisión de asistir → evento de Calendar. La compra de entradas no se automatiza.

Búsqueda literal y filtros funcionan sin LLM. Chat recupera los mismos registros y enlaza fuentes; no intenta deducir toda la historia de un prompt gigante. Una consulta puede devolver tres grupos: «has visitado», «te recomendaron» y «tu opinión». Si no hay valoración, decirlo.

## 6. Aplicaciones conectadas y autoridad de datos

El producto utiliza destinos opcionales para calendarios y listas. Google es la primera integración por el contexto y el código existentes, no parte obligatoria del modelo de datos ni requisito para todas las personas. No se amplía ahora el alcance a desarrollar conectores para todos los proveedores.

### Calendar: integración principal

Reutilizar primero `CalendarContract`, ya presente en la app, para calendarios Google sincronizados en Android. Seleccionar fuentes legibles y destino escribible; leer calendarios compartidos solo si ya son accesibles. La solicitud de acceso/compartición a otra persona queda fuera de la implementación automática.

Crear evento y recordatorio como una operación coherente, conservar el identificador y comprobar lectura posterior. Guardado en el proveedor del móvil y sincronización con Google no son el mismo estado; verificar el recorrido en la app/web de Calendar durante la aceptación. Si la cuenta no expone los datos necesarios al proveedor, evaluar API de Calendar con OAuth como alternativa explícita, no mantener dos escritores a la vez. [Proveedor Android](https://developer.android.com/identity/providers/calendar-provider).

Calendar admite recordatorios asociados a eventos. Configurar y comprobarlos, incluyendo aviso a la hora de inicio cuando se pida. No deducir que crear un evento programa automáticamente el aviso deseado. [Recordatorios de Calendar](https://developers.google.com/workspace/calendar/api/concepts/reminders).

### Tasks y avisos a una hora

La API de Google Tasks permite CRUD de tareas, pero su campo `due` conserva solo el día y descarta la hora. Las prestaciones de las apps de Google no implican que estén disponibles en esa API. Por ello no se promete crear por API una tarea Google con alarma a una hora determinada. [Contrato oficial de Tasks](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks).

Ruta propuesta:

- Cita con hora: evento en Calendar con recordatorio.
- Tarea sin hora: Trama inicialmente; integración Tasks opcional si se necesita gestionarla allí.
- Tarea con aviso concreto: elegir entre evento de aviso en un calendario Google seleccionado o tarea con aviso propio de Trama. La UI explica dónde vive y quién notificará; nunca crea las dos variantes silenciosamente.
- Diferenciar vencimiento, momento de aviso y duración de un evento. Si se usa una duración sugerida, mostrarla editable; no atribuir al usuario una duración que no dijo.

Un aviso con sonido y una alarma insistente son experiencias distintas. El prototipo comprobará primero avisos con fecha/hora, permisos de notificación y restricciones del dispositivo. Si se requiere alarma propia exacta, se implementa con las capacidades y permisos adecuados; WorkManager se reserva a resúmenes/reconciliación, no a puntualidad exacta. Si la exactitud no está disponible, indicarlo y ofrecer recuperación. [Alarmas en Android](https://developer.android.com/develop/background-work/services/alarms).

### Keep: dependencia que debe resolverse al principio

Caso de integración inicial: añadir elementos a una lista existente, propia o compartida, y poder exportar recomendaciones a Keep si la persona lo elige. Su API oficial está orientada a administración empresarial y el recurso notas no expone actualización general del contenido. No se puede prometer sincronización bidireccional o inserción automática en una lista personal existente como si fuera Calendar. [Guía Keep](https://developers.google.com/workspace/keep/api/guides), [operaciones de notas](https://developers.google.com/workspace/keep/api/reference/rest/v1/notes).

I01 debe probar y documentar el recorrido disponible en la cuenta/dispositivo objetivo, sin enviar datos personales en una prueba no autorizada. Decisión posible:

1. **Keep con paso asistido:** preparar contenido en Trama y compartir/copiar para que el usuario lo guarde en Keep. Verificar si permite el destino requerido; no llamar sincronización a una exportación ni afirmar que modifica la lista existente si solo crea una nota.
2. **Lista propia de Trama:** cumple adición automática, deduplicación y tachado; no equivale a lista compartida de Keep. Compartir/exportar un resumen es opcional.
3. **Otro destino con API soportada:** considerar, por ejemplo, Tasks para una lista personal si el usuario prefiere integración automática. No presentarlo como sustituto de la colaboración de Keep sin verificarla.

Si una persona requiere Keep y el recorrido oficial no lo permite, esa integración queda pendiente y se informa; U07 se valida por separado en los destinos que sí estén soportados. El resto del producto sigue adelante. No se basa la app distribuida en automatizar la UI de Keep ni en endpoints privados.

### Evitar mantenimiento doble

| Dato | Fuente principal propuesta | Papel de Trama |
|---|---|---|
| Evento en un calendario conectado | Calendario de origen | Caché, vista contextual y preferencias locales de anticipación. |
| Visita y opinión propia | Trama | Registro persistente, edición e historial. |
| Recomendación | Trama | Registro único consultable; exportación a Keep opcional y etiquetada como copia. |
| Lista | Trama o destino compatible elegido | Captura y estado verificado; no mantener dos listas maestras. |
| Tarea/aviso | Destino y motor elegidos al crearlo | Referencia estable, resultado, reintentos y control de duplicados. |

Las reglas locales de anticipación no editan eventos de otra persona. Una exportación a Google mueve el contenido elegido fuera del almacenamiento local; la IA y el historial completo siguen locales. La resolución de lugares actual ya consulta servicios remotos con coordenadas: ofrecer control y describirlo con precisión, sin prometer que toda la app carece de red.

## 7. Aprovechar el código existente y corregir sus límites

| Área | Base reutilizable | Trabajo necesario |
|---|---|---|
| Diario | `CalendarScreen`, `TimelineSupport`, `DayRange`. | Jerarquía de Home, estados legibles y eliminar cambio de `createdAt` de entradas antiguas del reloj. |
| Lugares | `DwellDetector`, `PlaceResolver`, `PlaceDetailScreen`, `Place`, eventos DWELL. | Ciudad/identidad estable, ambigüedad, visitas vecinas, correcciones y opinión por visita. Hoy `Place` tiene opinión global y el resolver reutiliza cercanía de hasta 80 m. |
| Agenda | `CalendarHelper`, `GoogleCalendarSyncManager`, `AgendaBriefing`, `WeeklyAgendaWorker`. | Preferencias generales de relevancia, cambios/cancelaciones, rango histórico y búsqueda futura bajo demanda. El sync actual consulta hoy→60 días y usa hora de inicio en el payload de identidad. |
| Avisos | Escritura de recordatorios en `CalendarHelper`; notificaciones existentes. | Resultado verificable de creación+aviso. El helper actual omite minuto cero y puede absorber el fallo del recordatorio mientras devuelve evento creado. No reutilizar el puente a alarma que pierde fecha. |
| Captura | `OfflineDictationCapture`, ASR local, repositorio, workers y Wear. | Llevar dictado breve a Home; separar intención de guardar de ejecución; preservar contenido sin modelo y sin filtros de trigger ambiental. |
| Búsqueda | `SearchScreen`, `ChatQueryInterpreter`, `ChatContextRetriever`, `DiaryAssistant`. | Índice/filtros de lugares, recomendaciones, notas y personas; evidencia estructurada y enlaces internos. |
| Datos | Room v16, esquemas, backup y tests de migración. | Migraciones no destructivas, nuevos campos/entidades mínimos y backup completo de origen, vínculos y valoraciones. |

No se reescribe el pipeline de audio ni toda la arquitectura. Extraer lógica de pantalla a componentes/ViewModels según lo necesiten los recorridos, no abrir una refactorización general como requisito previo.

### Modelo de información mínimo

- Mantener lugares y eventos DWELL como base de visitas. Añadir localidad/ciudad y procedencia de identificación; anotación de visita enlazada a su evento cuando haga falta. No adjudicar una opinión global antigua a una fecha concreta desconocida.
- Distinguir nota y tarea en las entradas existentes. Separar día contextual, captura original, fecha de acción y aviso; no falsificar horas para notas de fecha conocida sin hora.
- Añadir entidad de recomendación con tipo, título/texto, procedencia, persona textual opcional, ciudad, URL, estado y vínculo opcional a lugar/visita/acción. Una recomendación de local aún no visitado no crea una visita ni coordenadas ficticias.
- Añadir preferencias de anticipación y estado por evento/ocurrencia: regla, aviso calculado, reconocimiento de planificación, versión observada y cancelación.
- Mantener referencia externa e idempotencia para acciones enviadas: proveedor, cuenta/calendario/lista, ID, estado local/pendiente/confirmado/error. No reutilizar título y hora como identidad de todo el objeto.
- Para listas locales, usar lista+elemento, con nombre, estado y orden; cantidad opcional cuando sea pertinente. Evitar tipos exclusivos de compra. No desarrollar un CRM de personas ni un sistema genérico de automatizaciones para estas funciones.

## 8. Fases y backlog ejecutable

Cada fase entrega un recorrido comprobable. Los intervalos son estimaciones iniciales de esfuerzo de un desarrollador Android con apoyo puntual de diseño/QA; se recalibran tras F0. No constituyen una fecha comprometida.

### F0 — cerrar contratos e integraciones, 2–4 días

- **I01 Keep:** prototipo de destino existente/compartido y matriz de capacidades; elegir modo asistido o alternativa si no es posible. Salida: demo concreta, límites y decisión, no promesa de integración futura.
- **I02 Calendar/avisos:** leer calendario propio y compartido, crear/leer evento de prueba autorizado con aviso, cambiar/cancelar y verificar día/hora. Separar persistencia local y sync Google.
- **I03 datos:** reproducir cambio temporal de Wear y omisión de confirmaciones en backup; fijar pruebas antes de corregir.
- **I04 UX:** croquis de Mi día + próximos días + captura + ficha de lugar/recomendación; validar U01/U03/U05/U10 con diferentes estilos de vida, incluyendo ausencia de calendario compartido y ubicación desactivada.

Salida: decisiones técnicas suficientes para F1–F4 y backlog sin dependencias ficticias. El bloqueo de Keep no bloquea calendario, lugares o memoria.

### F1 — día fiable, 1–2 semanas

- **D01 integridad:** cronología inmutable, migraciones mínimas, origen/confirmación y restauración completos.
- **D02 visitas:** corregir, añadir visita omitida, distinguir sitios próximos, conservar intervalos aproximados y estado desconocido.
- **D03 Home:** recorrido del día legible, controles de fecha conservados, fuentes previsto/detectado/manual diferenciadas y acceso a lugares.
- **D04 permisos/consumo:** ubicación optativa e independiente del audio; ruta manual útil si se deniega. Medir antes de cambiar agresivamente intervalos GPS.

Salida: U01/U02/U08 funcionales y sin pérdida de datos en reinicio/restauración. Probar un recorrido real con parada corta, dos locales cercanos y visita que cruza medianoche.

### F2 — anticipación de compromisos, 1–2 semanas

- **A01 sincronización:** fuente/calendario seleccionados, cambios, cancelaciones, instancias recurrentes y frescura visible.
- **A02 relevancia:** destacar evento/serie, preferencias opcionales por fuente/coincidencia, próxima ocurrencia/en curso y tratamiento de intervalos nocturnos. Sin reglas específicas de profesión o parentesco.
- **A03 próximas fechas:** vistazo de siete días, siguiente compromiso importante y navegación a fechas fuera de la ventana local. No limitar búsquedas al rango de 60 días actual.
- **A04 planificación:** resumen agrupado opcional del día siguiente, semanal opcional, reconocimiento/posposición y deduplicación de avisos.

Salida: U03/U04 resueltos con eventos existentes sin crear duplicados en Google. Primer piloto del beneficio central: día vivido + mañana importante.

### F3 — captura y acciones que llegan a destino, 1–2 semanas

- **C01 captura:** texto/dictado visible, acuse, edición, tipo propuesto y persistencia anterior al enriquecimiento.
- **C02 cita:** resolver fecha relativa, seleccionar calendario, revisar y crear evento+aviso. Minuto cero, hora ambigua y duración visible incluidos.
- **C03 tarea:** fecha separada de aviso, elección explícita de motor/destino, reprogramación/cancelación y estado comprobable.
- **C04 listas:** añadir varios elementos, completar/reabrir, editar, ordenar y evitar duplicados apropiadamente. Cantidad explícita opcional; conector Keep únicamente con capacidades verificadas en I01. Validar tanto compra como listas de preparativos u otros elementos.

Salida: U05/U06 y U07 según capacidad acordada; U08 con voz además de texto. No se contabiliza como hecha una tarea externa porque solo se abrió otra app.

### F4 — memoria de recomendaciones y experiencias, 1–2 semanas

- **M01 recomendaciones:** biblioteca, filtros y colecciones sugeridas personalizables; texto/procedencia/localidad/URL y estados. Guardar contenido sin categoría también debe funcionar.
- **M02 opinión:** anotación por visita y valoración general; preservar opiniones ajenas frente a propias.
- **M03 búsqueda:** lugares por ciudad/tipo, recomendaciones por persona/estado, notas y fechas. El mismo registro puede encontrarse por diferentes entradas.
- **M04 Chat:** recuperación de registros pertinentes con fuentes clicables y declaración de ausencia de evidencia.
- **M05 planes:** interés→decisión/preparación→acción o compromiso; fecha de decisión independiente de fecha del evento, sin verificación web implícita.

Salida: U09–U14; búsqueda en una localidad con un lugar visitado y valorado, otro recomendado aún no visitado y otro sin opinión, sin mezclarlos. Verificar varios tipos de lugar y recomendaciones fuera de las colecciones sugeridas.

### F5 — piloto y comodidad, dos semanas de uso

- Ejecutar piloto con personas de rutinas variadas, incluyendo quien no comparte calendario, quien tiene horarios variables y quien usa principalmente memoria de lugares. Registrar episodios de utilidad y fallos, no contenido privado por defecto; comparar resultados por escenario, no solo por promedio.
- Atajos de móvil/reloj para el recorrido más frecuente; probar manos ocupadas antes de añadir otra superficie.
- Preparación de viajes por destino consultado; priorizar presentación contextual frente a nuevas notificaciones.
- Corregir interrupciones, sincronización, accesibilidad y consumo observados.
- Activación por frase distintiva, si sigue siendo necesaria, como experimento separado. Aplicar criterios del informe de voz; no condicionar la entrega de este plan a su éxito.

Horizonte orientativo del conjunto: unas 7–12 semanas según integraciones y capacidad disponible; el piloto puede solaparse con los últimos incrementos. La primera entrega útil se obtiene antes y se evalúa al cerrar F2. No añadir funciones P2 para compensar fallos de P0.

## 9. Validación y definición de terminado

### Prueba funcional de extremo a extremo

- Día: abrir, recorrer lugares, corregir uno, volver al día, consultar otro y restaurar desde copia conservando datos.
- Eventos: calendarios propios y compartidos de solo lectura, intervalo nocturno, cambio de una ocurrencia, cancelación y preferencia desactivada. Un aviso por motivo; sin alerta antigua tras cambio conocido.
- Cita: fecha relativa resuelta a fecha visible, hora/duración revisadas, evento y aviso comprobados. Repetir entrega no duplica.
- Tarea: día futuro con hora elegida; aviso llega en el dispositivo de prueba. Revocar permisos produce estado de error/recuperación, no éxito falso.
- Listas: varios elementos, cantidad opcional, duplicado pendiente, elemento completado anteriormente y fallo de destino. En Keep asistido, medir el paso manual y reconocer su limitación.
- Lugares: buscar ciudad con/sin acentos, lugar homónimo, opinión propia vs recomendación y dato ausente. Abrir fuente desde Chat y regresar a resultados.
- Recomendaciones: mismo elemento con dos procedencias, título dudoso, sin persona asociada, colección personalizada y cambio de estado sin perder origen.
- Plan: fecha desconocida, recordatorio de decisión, conversión a compromiso y cancelación; sin compra o reserva automática.
- Diversidad de configuración: sin pareja/persona vinculada, sin Google, sin calendario compartido, sin ubicación y con horario no diurno. No mostrar módulos obligatorios vacíos ni exigir datos ajenos al caso de uso.
- Robustez: sin Gemma, offline, proceso interrumpido, poco almacenamiento, reinicio, doble entrega Wear y errores de restauración.

### Objetivos de uso propuestos

Son puertas internas de evaluación, no prestaciones medidas actualmente:

| Resultado | Meta inicial |
|---|---|
| Entender el día | Identificar lugares principales y próximo compromiso relevante en unos 10 segundos. |
| Reconstrucción | ≥90 % de estancias relevantes anotadas por el participante recuperadas en recorridos soportados; informar por separado nombre erróneo, intervalo erróneo y visita desconocida. No premiar identificar incorrectamente para subir cobertura. |
| Captura | ≥95 % de capturas deliberadas del corpus terminan en contenido/destino correctos; distinguir aclaración, fallo visible y error silencioso. |
| Anticipación | Durante el piloto, la persona reconoce antes de organizarse los compromisos que considera relevantes. Registrar también las sorpresas y no prometer eliminar todo olvido. |
| Recuperación | Al menos 4 de 5 consultas personales con respuesta conocida resueltas en menos de 30 segundos, incluyendo días antiguos. |
| Integridad | Cero pérdida observada de fechas, opiniones, enlaces y confirmaciones en la matriz de fallos; cero duplicados por reintento. |
| Avisos | Fecha/hora/destino verificados en casos de aceptación; permisos y retrasos del sistema visibles. No prometer sonido con notificaciones desactivadas o No molestar. |
| Consumo | Medición A/B en dispositivo y recorrido equivalentes: objetivo provisional de ≤5 puntos porcentuales adicionales en 10 h para ubicación+calendario, con micrófono continuo apagado. Ajustar si las mediciones lo contradicen. |

Pruebas automatizadas de lógica y Room para identidad, estados, migraciones y reintentos; Compose para los recorridos; pruebas físicas para avisos, batería, audio y ubicación. No declarar terminado un flujo solo porque compila o porque el LLM produce JSON correcto.

## 10. Decisiones propuestas y decisiones pendientes reales

Se propone desde ahora: calendario como Home; memoria principal en Trama; captura deliberada; recomendaciones estructuradas sin formularios obligatorios; calendario como fuente de compromisos; anticipación selectiva y configurable; sin escucha ambiental obligatoria.

Antes de implementar su parte se necesita concretar:

- Resultado de I01 y capacidades de los destinos de listas soportados; colaboración opcional diferenciada de lista personal.
- Fuentes de calendario y preferencias de relevancia que cada persona podrá elegir, sin configurar de antemano relaciones ni tipos de evento.
- Horario y frecuencia configurables para resúmenes; ninguna notificación real se activa al aprobar este documento.
- Para avisos concretos, preferencia entre Calendar y alarma propia cuando sean experiencias diferentes.

Estas decisiones no impiden preparar F0 ni corregir integridad, Home y lugares. No se solicita otra aprobación general para el mismo alcance; se resuelven preferencias concretas con un prototipo revisable cuando corresponda.

## 11. Ejemplos de validación, no funciones especiales

| Ejemplo | Capacidad general que pone a prueba |
|---|---|
| Guardia de la pareja, turno propio o examen. | Evento relevante, propio o compartido, con anticipación configurable. |
| Médico de un familiar, reunión o clase. | Cita con fecha/hora, destino y aviso. |
| Llamar al taller, entregar un documento o realizar una gestión. | Tarea con fecha y aviso opcional. |
| Compra, equipaje o material para una actividad. | Lista de elementos, cantidades opcionales y estados. |
| Restaurante en Portonovo, museo en otra ciudad o alojamiento. | Lugar visitado/recomendado, experiencia y búsqueda contextual. |
| Libro, serie, curso o actividad recomendada. | Recomendación con procedencia y estado. |
| Concierto, exposición o convocatoria. | Plan de interés con fecha de decisión y posible conversión a compromiso. |

Ningún ejemplo da lugar por sí solo a un tipo de datos exclusivo, una pantalla obligatoria o una regla activada para todos. El criterio para añadir una especialización será que resuelva una necesidad repetida que el modelo común no cubra bien.

## 12. Mejoras de los escenarios de ejemplo

Cada recorrido se evalúa en cuatro momentos: capturar, guardar en el destino apropiado, recuperar cuando sirve y actualizar/cerrar sin perder historia. Las siguientes mejoras son propuestas de UX aplicables a las capacidades comunes.

| Escenario | Mejora propuesta | Resultado que evaluar |
|---|---|---|
| Ver los sitios del día. | Agrupar desplazamientos y estancias relevantes; permitir corregir el sitio y añadir una nota breve. Separar lo detectado de lo previsto en calendario. | Reconstruir el día sin interpretar coordenadas ni confundir una cita programada con una visita realizada. |
| Recuperar restaurantes de un viaje. | Guardar ciudad, visitas y comentario opcional «volvería/no volvería» o texto libre. Consultar por zona devuelve visitados y recomendados en grupos distintos. Una nueva opinión no borra la anterior. | Elegir qué experiencia repetir y entender por qué, incluso si la opinión cambió entre visitas. |
| Añadir productos a la compra. | Una frase añade varios elementos a la lista elegida; mostrar destino y resultado, conservar cantidades expresas y ofrecer Deshacer. No duplicar menciones repetidas sin intención de aumentar cantidad; «otros dos» sí requiere tratar la cantidad. | Lista utilizable al comprar, con el mínimo trabajo de corrección. La integración Keep conserva los límites de I01. |
| Recordar un evento que condiciona la organización. | Poder marcar cualquier evento o serie como importante y elegir anticipación. Mostrarlo en el vistazo semanal y, si se desea, al preparar el día siguiente. Recalcular ante cambios. | Conocerlo antes de tomar decisiones, no solo recibir un aviso cuando ya va a comenzar. |
| Registrar una cita. | Vista previa de fecha completa, hora, calendario y aviso; permitir añadir preparación explícita como acción vinculada. No inventar necesidades de documentación o desplazamiento. | Llegar con el compromiso y la preparación elegida presentes, sin duplicar el evento. |
| Recordar una llamada o gestión. | Separar día de realización de hora de aviso; usar la preferencia elegida o pedir el dato. Desde el recordatorio permitir completar, posponer o reprogramar. Posponerlo no afirma que cambió un plazo externo. | Reducir recordatorios ignorados y mantener pendientes reales sin insistencia ilimitada. |
| Guardar un libro, serie o lugar recomendado. | Conservar quién lo recomendó, por qué interesó y enlace si existen; no exigirlos. Mostrar pendientes al consultar la colección y permitir registrar experiencia posterior. | Poder elegir y recordar el motivo de la recomendación, además del título. |
| Enterarse de un concierto u oportunidad. | Guardar como interés, con fecha de decisión separada del evento. Ofrecer «recordarme decidir» y convertir en compromiso al decidir participar. Tras la fecha, señalar plan pasado sin borrar el registro. | Evitar oportunidades olvidadas y calendarios llenos de planes no confirmados. |

Priorizar cambios de alto valor con poca carga: destino/resultado claros, corrección y Deshacer, distinción previsto/visitado/recomendado, búsqueda por contexto y anticipación elegida. Después probar sugerencias proactivas más complejas. No activar un aviso por cada visita, recomendación o elemento de una lista.

## Referencias internas

- [Primera evaluación](USABILITY_AND_UTILITY_REVIEW_2026-09-06.md): inventario de problemas y base técnica.
- [Segunda evaluación de voz](VOICE_CAPTURE_DECISION_2026-09-06.md): evidencia histórica, viabilidad y comparación de captura.
- `PRODUCT_SPEC_2026-08-12.md`: dirección visual y navegación previas; actualizar su estado y contratos al iniciar este plan para evitar dos especificaciones activas contradictorias.
