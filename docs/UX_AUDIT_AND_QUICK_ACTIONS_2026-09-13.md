# Auditoría UX de Trama y propuesta de acciones rápidas

Fecha: 13 de septiembre de 2026

## Decisión recomendada

Trama necesita recuperar los controles de captura en la zona inferior. La mejor solución no es restaurar literalmente el antiguo menú por pulsación larga, sino reutilizar su lógica en un **muelle flotante persistente** situado sobre la navegación inferior.

En el teléfono mostrará tres acciones visibles:

1. **Escuchar**: activa o pausa la escucha continua.
2. **Reunión**: inicia o detiene una grabación larga.
3. **Reloj**: transfiere la escucha al reloj; cuando el reloj tiene el control cambia a **Recuperar**.

En el reloj mostrará las tres decisiones equivalentes en la mitad inferior:

1. **Escuchar aquí**.
2. **Grabar reunión**.
3. **Usar el teléfono**; cuando el teléfono tiene el control cambia a **Recuperar aquí**.

La acción de «Captura directa» del reloj se integrará en una acción secundaria o se eliminará de Home. Ahora compite visualmente con «Grabadora» sin explicar una diferencia de valor para el usuario.

## Evidencia del problema

- Las tres acciones siguen conectadas en `CalendarScreen.kt:680-708`, por lo que no hay que reconstruir su lógica.
- `FloatingMicButton` todavía existe en `CalendarScreen.kt:1585-1688`, pero no tiene ninguna llamada. Es código muerto funcionalmente desconectado.
- Ese control ocultaba las acciones secundarias mediante pulsación larga (`CalendarScreen.kt:1635-1644`). La función no era descubrible aunque su posición sí era buena.
- Escucha, reunión y transferencia están ahora dentro del menú de tres puntos de la cabecera (`CalendarScreen.kt:1901-1943`), lejos del pulgar y mezcladas con Grabaciones y Ajustes.
- La parte inferior está ocupada por controles de fecha, semana y una barra de tres destinos (`CalendarScreen.kt:1335-1379`). El resultado es mucha altura fija y ninguna acción de captura en la zona más accesible.
- En Wear hay cuatro botones iconográficos con igual jerarquía (`WatchHomeScreen.kt:153-239`) y una leyenda separada (`WatchHomeScreen.kt:248-257`). La persona debe memorizar color, icono y significado.

La guía actual de Android define el FAB como una acción principal de alta prioridad anclada normalmente abajo a la derecha. Para tres acciones frecuentes, un muelle con jerarquía explícita evita fingir que las tres son la única acción principal. Wear recomienda objetivos táctiles de al menos 48 dp y chips cuando el icono por sí solo no comunica el resultado.

Referencias:

- https://developer.android.com/develop/ui/compose/components/fab
- https://developer.android.com/develop/ui/compose/components/scaffold
- https://developer.android.com/design/ui/wear/guides/m2-5/components/buttons
- https://developer.android.com/training/wearables/accessibility

## Propuestas comparadas

### A. Muelle flotante persistente — recomendada

Una superficie compacta sobre la barra inferior con tres objetivos de 56 dp y etiquetas breves. «Escuchar» ocupa la posición más cómoda. «Reunión» usa rojo únicamente durante una grabación. «Reloj/Teléfono» usa azul y expresa siempre el destino.

Ventajas:

- Las tres acciones se descubren sin aprendizaje.
- Se alcanzan con una mano.
- Cada estado permanece en el mismo lugar.
- Las etiquetas eliminan la ambigüedad entre escucha y reunión.
- Permite mostrar duración y error cerca de la acción responsable.

Coste:

- Ocupa aproximadamente 72 dp sobre la barra inferior.
- Hay que simplificar la barra de fecha actual para no cubrir contenido.

### B. FAB principal con desplegable al tocar

Un FAB de micrófono abre dos botones etiquetados: «Reunión» y «Reloj». Un toque siempre abre; nunca depende de pulsación larga.

Ventajas:

- Recupera casi literalmente el patrón anterior.
- Usa menos espacio en reposo.

Coste:

- Reunión y transferencia requieren dos toques.
- El icono del micrófono sigue mezclando escucha y captura.
- Las acciones secundarias continúan ocultas hasta abrir el menú.

### C. Acción central «Capturar» y hoja inferior

Un botón central abre una hoja con «Nota», «Dictar», «Reunión», «Escucha continua» y «Cambiar de dispositivo».

Ventajas:

- Escala bien si aparecen nuevas formas de captura.
- Explica cada acción con una línea de texto.

Coste:

- Convierte operaciones frecuentes en un flujo de dos pasos.
- Mezcla creación de contenido con control de un servicio continuo.

La opción A responde mejor al uso diario. La C puede emplearse solo para «Añadir» y contener nota/dictado; no debe sustituir los tres controles operativos.

## Comportamiento exacto del muelle

| Estado real | Escuchar | Reunión | Dispositivo | Mensaje visible |
|---|---|---|---|---|
| Todo detenido | `Escuchar` | `Reunión` | `Reloj` | `Escucha desactivada` |
| Escucha en teléfono | `Pausar` en ámbar | `Reunión` | `Reloj` | `Escuchando en este teléfono` |
| Escucha en reloj | desactivado | desactivado | `Recuperar` en azul | `Escuchando en el reloj` |
| Grabando reunión | `Escuchar` desactivado | `Detener · mm:ss` en rojo | desactivado | `Audio guardándose en este dispositivo` |
| Procesando reunión | disponible si memoria lo permite | `Procesando…` | disponible | progreso por fase y acceso a Grabaciones |
| Error de micrófono | `Reintentar` | disponible si procede | disponible | causa cotidiana y acción para resolverla |

Reglas:

- Ninguna acción esencial requiere pulsación larga.
- Tocar «Reunión» cambia inmediatamente al estado de grabación y ofrece vibración corta.
- Detener una reunión conserva primero el audio y después procesa. La pantalla dice «Audio guardado · preparando transcripción».
- Transferir cambia el texto al destino real. No usar solo «Transferir», porque no indica dirección.
- Si una acción está desactivada, tocar su explicación de estado indica por qué; no se muestra un control aparentemente roto.
- El muelle desaparece en selección múltiple y edición modal, y permanece visible en Día, Acciones y Recuerdos cuando la acción sea segura.

## Arquitectura de información propuesta

Trama puede explicarse como: **«Guarda lo que importa de tu día y recupéralo cuando lo necesites».**

Tres destinos principales:

1. **Día**: qué ocurrió, lugares, reuniones y recuerdos en orden temporal.
2. **Acciones**: tareas confirmadas y una bandeja diferenciada de propuestas por revisar.
3. **Recuerdos**: búsqueda unificada de notas, lugares y reuniones.

«Preguntar a tus recuerdos» pasa a ser una acción dentro de resultados de búsqueda. «Grabaciones» deja de competir como destino principal: es un filtro de Recuerdos y un acceso contextual desde las reuniones del Día.

## Diagnóstico UX completo

### 1. Inicio y navegación

**Hallazgo grave:** Home combina cabecera, píldoras de estado, timeline, controles del día, acceso semanal y barra de navegación. La barra inferior contiene a la vez navegación temporal y navegación global (`CalendarScreen.kt:827-845` y `1335-1379`).

Efecto: cambia el significado de la zona inferior, consume una parte grande de la pantalla y desplaza la acción principal a la cabecera.

Propuesta:

- Barra inferior estable con Día, Acciones y Recuerdos.
- Fecha y semana como control local compacto debajo de la cabecera de Día.
- Muelle de captura inmediatamente sobre la barra global.
- Mantener el título del día y búsqueda; mover Añadir al muelle/hoja de captura.
- Ajustes queda en el menú superior porque es una acción infrecuente.

### 2. Captura manual y escucha

**Hallazgo grave:** el mismo dominio «micrófono» representa dictado puntual, escucha continua y reunión. `handleMicClick` incluso detiene una grabación si está activa (`CalendarScreen.kt:680-689`).

Efecto: el usuario debe inferir qué hará el icono según un estado que puede no haber visto.

Propuesta:

- Tres verbos estables: Escuchar, Dictar y Grabar reunión.
- «Añadir» abre solamente Nota y Dictar.
- «Escuchar» controla el servicio de activadores.
- «Reunión» controla audio largo y muestra temporizador.
- El color refuerza un texto; nunca es la única señal.

### 3. Estados y revisión

**Hallazgo:** el modelo mantiene `PENDING`, `SUGGESTED`, `COMPLETED` y `DISCARDED` (`DiaryEntry.kt:92-95`). En la interfaz aparecen «Por confirmar», «Pendiente», «Completada» y «Descartada», pero una sugerencia puede confirmarse mediante la misma interacción usada para completar una tarea (`AgendaScreen.kt:147-168`).

Efecto: «aceptar que esto es una tarea» y «marcar la tarea como hecha» se parecen demasiado.

Propuesta:

- Bandeja única **Por revisar** con Aceptar, Editar y Descartar.
- Tras aceptar, la entrada entra en Acciones como pendiente.
- Completar solo existe para acciones ya confirmadas.
- Elementos descartados no aparecen en navegación normal; se recuperan desde Papelera durante 30 días.
- Mostrar `Procesando`, `Necesita atención` y `Listo` solo en reuniones, sin mezclar esos estados con los de las acciones extraídas.

### 4. Reuniones

**Fortaleza:** la ficha ya separa notas, acciones y transcripción y ofrece «Volver a transcribir» cuando queda audio (`RecordingDetailScreen.kt:412-424`).

**Problemas:**

- La lista vacía solo dice «No hay grabaciones» (`RecordingsListScreen.kt:154-163`) y no explica cómo empezar.
- «Reprocesar» es un icono sin texto y actúa en lote sobre estados diferentes (`RecordingsListScreen.kt:111-136`).
- El borrado múltiple se ejecuta desde el icono de papelera sin confirmación visible (`RecordingsListScreen.kt:77-89`).
- La transcripción completa es un bloque largo sin búsqueda, copia por sección ni marcas temporales visibles.

Propuesta:

- Vacío: «Aún no has grabado una reunión» + botón `Grabar reunión` conectado al muelle.
- Tarjeta con fase: Audio guardado → Transcribiendo → Preparando notas → Listo.
- Error conserva audio y ofrece `Reintentar` y `Ver detalle`.
- Borrado confirma cuántas reuniones y si elimina audio.
- Ficha con pestañas Notas, Acciones y Transcripción; búsqueda dentro de transcripción.

### 5. Día y lugares

**Fortaleza:** el timeline une estancias, calendario, reuniones y entradas, y la ficha de lugar permite editar visitas y dictar opiniones (`PlaceDetailScreen.kt:118-207`).

**Problemas:**

- Un evento previsto y una estancia observada conviven visualmente y pueden parecer hechos equivalentes.
- No existe un destino claro para explorar todos los lugares; se accede por timeline o búsqueda.
- La ficha de lugar concentra nombre, valoración, opinión, resumen y visitas sin modo lectura/edición claramente separado.

Propuesta:

- Etiquetas de procedencia consistentes: `Calendario`, `Detectado`, `Anotado por ti`, `Reunión`.
- Recuerdos incorpora filtros Lugares, Reuniones y Notas.
- Ficha de lugar abre en lectura; `Editar` activa los campos y una única acción Guardar.
- Evitar resumir una opinión corta: usar IA solo cuando haya varias notas o suficiente contenido.

### 6. Acciones y calendario

**Fortaleza:** Agenda agrupa vencidas, semanas, más adelante y sin fecha; permite completar, posponer y deshacer (`AgendaScreen.kt:219-270`).

**Problemas:**

- La tarjeta estadística aparece antes de la tarea más urgente (`AgendaScreen.kt:210-217`).
- Calendario externo, tarea local y sugerencia pueden compartir aspecto aunque tengan capacidades diferentes.
- «Agenda despejada · disfrútalo» no ofrece volver al Día ni capturar algo (`AgendaScreen.kt:275-294`).

Propuesta:

- Primero: Vencidas y Hoy; después Esta semana.
- Resumen cuantitativo compacto en la cabecera, no como tarjeta dominante.
- Procedencia y resultado visibles: `En Trama`, `En Google Calendar`, `Pendiente de crear`, `Error`.
- La vista previa antes de crear en Calendar debe mostrar título, fecha completa, hora, calendario y aviso.

### 7. Búsqueda y conversación

**Hallazgo:** Búsqueda ya cubre lugares, reuniones y recuerdos. Chat es otra pantalla completa y solo se alcanza pasando una consulta desde búsqueda (`NavGraph.kt:90-105`).

Efecto: hay dos modelos de recuperación y la persona debe decidir cuál usar antes de saber si la respuesta requiere IA.

Propuesta:

- Recuerdos abre con búsqueda universal y filtros.
- Resultados literales aparecen inmediatamente.
- `Preguntar sobre estos resultados` abre conversación conservando filtros y fuentes.
- Cada respuesta mantiene enlaces a entradas, lugares o reuniones; si no existe evidencia dice «No encuentro nada guardado que lo confirme».
- No mantener Chat como destino principal.

### 8. Ajustes

**Fortaleza:** el código ya declara secciones separadas para Voz, Calendario, Datos, Apariencia y Diagnóstico (`SettingsScreen.kt:130-136`).

**Problemas:**

- `SettingsScreen.kt` supera 3.500 líneas y reúne permisos, estado de servicios, modelos, reconocimiento de voz, copias y diagnóstico.
- En la raíz se mezclan interruptores operativos con tarjetas de navegación (`SettingsScreen.kt:555-740`).
- Escucha se controla en Ajustes, en Home y en reloj. La configuración y la operación diaria no tienen una frontera estable.
- Modelo local y copia están dentro de Datos, pero son funciones críticas para reuniones y recuperación y han resultado difíciles de encontrar.

Estructura final:

1. **Captura**: frases de activación, mi voz, duración máxima de reunión, dispositivo preferido.
2. **Calendario y lugares**: calendarios visibles, avisos, registrar lugares y permisos asociados.
3. **Datos y privacidad**: modelo local, copia diaria, exportar/importar, borrar datos.
4. **Apariencia**: tema y tamaño/presentación.
5. **Diagnóstico**: oculto tras gesto de desarrollador; logs, métricas ASR, estado de reloj y pruebas técnicas.

Los interruptores diarios de Escucha y Grabar salen de Ajustes. Ajustes define el comportamiento; el muelle controla el estado presente.

### 9. Reloj

**Hallazgo grave:** cuatro botones iconográficos de 48 dp se reparten en una cuadrícula y después se explican en otra fila. El título, estado, aviso de batería, botones, leyenda y explicación compiten dentro de una pantalla circular.

Propuesta:

- Estado corto arriba: `Escuchando aquí`, `Grabando 12:08` o `En el teléfono`.
- Tres acciones etiquetadas en la mitad inferior.
- En reposo, «Escuchar aquí» tiene énfasis principal; Reunión y Teléfono, secundario.
- Grabando: sustituir todas por un botón grande `Detener` y conservar temporizador.
- Eliminar la leyenda de colores; las etiquetas están junto al control.
- Mantener objetivos de 48 dp como mínimo.
- Añadir tile solo después de validar que el inicio desde la app es claro.

### 10. Accesibilidad y confianza

- Los mini controles antiguos miden 46 dp (`CalendarScreen.kt:1690-1716`), por debajo del objetivo recomendado de 48 dp.
- Las acciones solo iconográficas dependen de TalkBack para ser comprensibles visualmente.
- Confirmar contraste en estados ámbar, rojo y azul en ambos temas.
- Verificar fuente al 200 %, orientación, pantallas de 320 dp y modo de una mano.
- Toda operación destructiva debe indicar alcance: entrada, reunión, audio asociado y derivados.
- La app debe mostrar cuándo el dato quedó persistido antes de iniciar ASR o IA.

## Inventario y decisión

| Superficie | Decisión | Cambio principal |
|---|---|---|
| Día/Home | Modificar | Separar navegación temporal, navegación global y captura |
| Acciones | Mantener y simplificar | Priorizar vencidas/hoy y separar propuestas |
| Recuerdos/Búsqueda | Mantener y ampliar | Filtros de notas, lugares y reuniones |
| Chat | Fusionar | Acción contextual desde Recuerdos |
| Grabaciones | Fusionar como filtro | Conservar acceso contextual y errores recuperables |
| Ficha de reunión | Mantener | Pestañas, progreso y búsqueda de transcripción |
| Ficha de entrada | Mantener | Lenguaje consistente y una acción primaria por estado |
| Ficha de lugar | Modificar | Separar lectura y edición |
| Ajustes | Reestructurar | Cinco grupos; diagnóstico oculto |
| Home del reloj | Rediseñar | Tres acciones etiquetadas y estados mutuamente excluyentes |
| FAB antiguo | Reutilizar parcialmente | Recuperar posición y lógica, eliminar pulsación larga |
| Menú superior de captura | Eliminar duplicación | Dejar Grabaciones y Ajustes; acciones operativas abajo |

## Prioridad de implementación

### P0 — confianza y acción inmediata

1. Restaurar el muelle inferior con tres controles visibles.
2. Máquina de estados única para teléfono/reloj/reunión y bloqueo de combinaciones imposibles.
3. Confirmación de audio guardado antes de procesar reuniones.
4. Borrado de reuniones con alcance explícito y confirmación.
5. Copia/restauración verificable, incluida una decisión explícita sobre audio.

### P1 — simplificación

1. Separar barra global, fecha local y muelle de acciones.
2. Bandeja única Por revisar.
3. Integrar Chat dentro de Recuerdos.
4. Reducir Home del reloj a tres acciones.
5. Vacíos con una acción útil y errores con recuperación.

### P2 — recuperación de valor

1. Filtros de Recuerdos.
2. Búsqueda dentro de transcripciones.
3. Mejorar ficha de lugar y procedencia del timeline.
4. Persistir borradores de captura manual.
5. Accesibilidad con fuente grande, TalkBack y modo de una mano.

### P3 — después de validar uso

1. Tile del reloj para la acción preferida.
2. Acción configurable en botón físico cuando el dispositivo lo permita.
3. Widgets y accesos directos de Android.
4. Personalización opcional del orden de las tres acciones.

## Archivos afectados por la primera entrega

- `app/src/main/java/com/trama/app/ui/screens/CalendarScreen.kt`: conectar un muelle nuevo al `Scaffold`, retirar acciones duplicadas del overflow y borrar los composables flotantes muertos.
- `app/src/main/java/com/trama/app/ui/components/TramaPrimitives.kt`: componente compartido del muelle y estados accesibles.
- `app/src/main/java/com/trama/app/service/ServiceController.kt`: exponer una sola máquina de estado observable para dispositivo y modo.
- `wear/src/main/java/com/trama/wear/ui/screens/WatchHomeScreen.kt`: tres controles etiquetados y estado dominante.
- `wear/src/main/java/com/trama/wear/service/WatchServiceController.kt`: transiciones explícitas y resultados de transferencia.
- `app/src/main/java/com/trama/app/ui/screens/RecordingsListScreen.kt`: vacío accionable y borrado confirmado.

## Validación propuesta

Pruebas con una sola persona, teléfono en una mano y reloj puesto:

1. Desde Home, activar escucha sin mirar la parte superior.
2. Confirmar visualmente y con TalkBack dónde está escuchando Trama.
3. Iniciar una reunión en un toque y detenerla tras 30 segundos.
4. Salir de Home durante la grabación y volver; el control debe seguir visible y mostrar el tiempo correcto.
5. Transferir escucha del teléfono al reloj y recuperarla sin estados simultáneos.
6. Intentar grabar mientras el reloj controla la escucha; la app explica y resuelve el conflicto.
7. Encontrar una reunión fallida y reintentar sin perder el audio.
8. Crear una nota por texto y otra por dictado; ninguna se confunde con escucha continua.
9. Aceptar una sugerencia y después completar la tarea; deben sentirse como acciones distintas.
10. Encontrar un lugar, una reunión y una nota desde Recuerdos.
11. Repetir con fuente al 200 % y TalkBack.
12. Repetir las tres acciones esenciales con la mano no dominante.

Objetivos iniciales:

- 100 % de éxito al identificar las tres acciones sin explicación.
- Inicio de escucha o reunión en un toque desde cualquiera de los tres destinos principales.
- Cero activaciones por pulsación larga desconocida.
- Cero estados simultáneos contradictorios entre teléfono y reloj.
- Objetivos táctiles mínimos de 48 dp.
- Estado persistido visible en menos de 500 ms; el procesamiento posterior puede continuar aparte.

## Riesgos

- Tres controles flotantes pueden tapar contenido si se conserva intacta la barra inferior actual. La simplificación de fecha y semana forma parte de la misma entrega.
- Cambiar controles sin unificar la máquina de estados puede crear discrepancias visuales. La UI debe derivarse de un estado compartido, no de varios booleanos independientes.
- Transferir entre teléfono y reloj depende de conectividad. La interfaz necesita `Transfiriendo…`, confirmación del destino y recuperación ante timeout.
- Una reunión larga puede coincidir con falta de almacenamiento. Antes de comenzar debe comprobar espacio y mostrar el límite disponible sin esperar al fallo final.
