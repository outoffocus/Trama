# Estudio de simplificación, usabilidad y diseño

Fecha de corte: `2026-08-11`.

> Actualización `2026-08-12`: la dirección vigente está en
> [`MVP_USEFUL_EFFICIENT_PLAN_2026-08-12.md`](MVP_USEFUL_EFFICIENT_PLAN_2026-08-12.md).
> Tras un segundo diagnóstico real, calendario y traza de ubicación pasan a ser
> núcleo del MVP; la escucha continua queda opcional.

## Conclusión

El MVP vendible no debe intentar vender “una memoria de todo”. La propuesta más
clara que el código actual puede sostener es:

> Trama convierte lo que dices o grabas en tareas fiables dentro de un calendario
> diario, de forma local y privada.

El calendario de Home debe mantenerse como eje. La simplificación consiste en
reducir entradas, decisiones y ajustes visibles; no en añadir otra navegación ni
en eliminar de golpe código recuperable.

## Evidencia usada

Se revisaron `NavGraph`, `CalendarScreen`, `AgendaScreen`, `SettingsScreen`, los
detalles, el manifest, los servicios de audio/ubicación, las rutas de compartir,
la captura de pantalla, Wear, Room, backup y los tests. También se toma como
evidencia el diagnóstico real de una jornada en casa: 37 transcripciones de
fallback, ninguna tarea ambiental guardada y ninguna captura involuntaria de TV.

Esto permite evaluar producto y coste técnico, pero no demuestra demanda ni
retención. No hay analítica de uso, entrevistas ni pruebas moderadas; cualquier
prioridad comercial de este documento es una hipótesis explícita, no un dato de
mercado inventado.

## Diagnóstico verificable

| Hallazgo | Evidencia actual | Consecuencia |
| --- | --- | --- |
| Home temporal es coherente | `CalendarScreen` reúne día, mes, timeline y agenda | Mantenerlo; es el modelo mental más sólido de Trama |
| La acción primaria es ambigua | El FAB activa/desactiva escucha; una pulsación larga revela Grabar y Wear | La función importante depende de un gesto oculto |
| Agenda puede quedar inaccesible | Su acceso solo se renderiza si `upcomingThisWeekCount > 0` | Añadir acceso permanente sin cambiar el calendario |
| Hay código de UI huérfano | `DayTimelineScreen` no tiene ruta; `CalendarImportedEventCard` no tiene llamada | Retirar o integrar después de verificar; no crear rutas artificiales |
| Ajustes sigue siendo demasiado denso | `SettingsScreen`: 3700 líneas, 13 toggles, 13 sliders, 8 campos y 6 secciones | Mantener divulgación progresiva y reducir decisiones básicas |
| El producto mezcla demasiadas promesas | Voz automática, grabaciones, calendario, agenda, chat, lugares, capturas, share, resúmenes, Wear | Una sola promesa comercial; el resto, complementos |
| La precisión conservadora funciona | La jornada real no produjo tareas ambientales falsas | Preservar perfil Preciso y confirmación humana |
| El valor de lifelogging pasivo aún no está probado | La jornada tampoco creó contexto de TV | Mantener Contexto ambiental como beta opt-in |
| Los permisos no son contextuales | Al pedir micrófono, `MainActivity` agrupa audio, ubicación, calendario y notificaciones | Separar cada permiso por función y momento |
| Falta validación de interacción | 58 tests JVM y 1 test de migración; ningún test Compose | Añadir pruebas de recorridos y dispositivo físico |
| Hay objetivos táctiles pequeños | Cabecera de 38 dp; acciones rápidas de 32/34/46 dp | Elevar objetivos interactivos a al menos 48 dp |
| No hay adaptación de gran pantalla | No se usan `WindowSizeClass` ni navegación adaptativa | Añadir list-detail/rail solo si se soportan tablet y plegable |
| El lenguaje no está centralizado | `strings.xml` solo contiene `app_name`; hay al menos 184 textos UI literales | Centralizar copy para consistencia, accesibilidad y revisión |

Las recomendaciones de navegación siguen la guía oficial de Android: navegación
primaria común para destinos del mismo nivel, una sola acción de máxima prioridad
en el FAB y acciones infrecuentes en overflow ([Layouts and navigation
patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns)).
Android también recomienda no sobrecargar cada vista y mantener accesibles las
interacciones esenciales ([Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)).

## MVP vendible propuesto

### Núcleo visible

1. Home calendario/timeline con navegación actual por día y mes.
2. Crear una tarea escribiendo o mediante una frase personal explícita.
3. Bandeja visible de sugerencias: confirmar, editar o descartar.
4. Completar y posponer; Agenda siempre accesible.
5. Grabación manual con transcripción local y tareas sugeridas.
6. Lectura de calendarios seleccionados del sistema.
7. Búsqueda básica.
8. Exportar, importar y borrar datos con lenguaje claro.
9. Procesamiento local; sin claves ni modelos cloud.

### Complementos conservados, fuera de la promesa principal

- Contexto ambiental: `Beta`, opt-in, cuatro categorías y feedback de acierto.
- Compartir texto o audio desde Android: acceso externo, sin ocupar Home.
- Wear OS: extensión opcional, no requisito para entender el móvil.
- Modelo Gemma: descarga asistida; sus controles técnicos quedan en modo experto.

### Ocultar del MVP básico, sin borrar todavía

- Chat, hasta medir que recupera información mejor que Búsqueda y Agenda.
- Valoraciones y opiniones de lugares. La traza de ubicación y las estancias se
  mantienen en el núcleo por decisión de producto; deben optimizarse y medirse.
- Capturas de pantalla: hoy desaparecen si no hay acciones y exigen un modelo
  multimodal; no cumplen todavía un contrato de “memoria guardada”.
- Resumen diario y agenda semanal automática: se solapan con Home/Agenda y deben
  demostrar uso antes de ocupar Ajustes básicos.
- Editor completo de frases/categorías, prompts, umbrales, URL de modelo, pre/post
  roll, radios, colores individuales y diagnóstico: modo experto.

No se debe eliminar una capacidad por intuición. Primero se oculta del recorrido
básico, se mide y después se decide mantener, mejorar o retirar.

## Navegación principal recomendada

No añadir una barra inferior de destinos: hoy no existen 3–5 áreas de igual peso;
Home es el tiempo y Agenda es su proyección futura. La guía de Android reserva la
barra de navegación para destinos primarios del mismo nivel. La solución mantiene
la forma de gestionar el calendario solicitada por el usuario.

### Home

- Cabecera: fecha seleccionada, Buscar y overflow.
- Estado: una línea accionable (`Escucha activa`, `Pausada`, `Necesita atención`),
  sin mensajes técnicos salvo error.
- Timeline: `Revisar`, `Pendiente de otros días`, `Hoy`, `Completado`.
- Barra inferior: controles de día, semana y un acceso `Agenda` permanente, incluso
  con contador cero.
- Un único FAB expandido `Capturar`.

Al pulsar `Capturar`, una hoja inferior muestra acciones con texto:

1. `Decir una tarea`.
2. `Grabar una conversación`.
3. `Escribir`.

La escucha automática se controla desde el estado o Ajustes, no mediante el mismo
botón que representa grabar. Se elimina la dependencia de pulsación larga. Si Chat
demuestra valor, puede volver como destino principal; mientras tanto vive en
overflow.

### Sin pantallas huérfanas

- `Agenda`, `Grabaciones`, `Ajustes`, `Chat` y `Búsqueda` deben tener una entrada
  estable, no condicionada a tener datos.
- `EntryDetail`, `RecordingDetail` y `PlaceDetail` siguen siendo destinos hijos del
  elemento que los abre.
- `DayTimelineScreen` debe eliminarse si Home ya cubre el histórico; no añadir una
  ruta solo para justificar un archivo.
- Cada feature oculta debe conservar un punto de entrada experto o retirarse junto
  con su ruta, estado y documentación.

## Ajustes profesionales

### Básico

1. `Captura`: Preciso/Equilibrado, escucha activa y Contexto ambiental Beta.
2. `Calendarios`: fuentes visibles.
3. `Privacidad y datos`: voz opcional, backup, exportar/importar y borrar todo.
4. `Apariencia`: Sistema/Claro/Oscuro y tamaño de contenido.

### Experto

- frases y categorías personalizadas;
- Gemma, umbral y prompts;
- audio pre/post-roll y diagnóstico;
- ubicación y radios;
- colores semánticos;
- aprendizaje y herramientas de prueba.

Cada ajuste debe explicar efecto, coste y valor recomendado. Los valores técnicos
no deben aparecer en el nivel básico.

## Onboarding y confianza

No existe onboarding: la app abre Home directamente. El MVP necesita cuatro pasos
cortos y omitibles:

1. Promesa: tareas desde voz y grabaciones, todo local.
2. Demostración de una frase y del resultado `Sugerida`/`Confirmada`.
3. Activación voluntaria del micrófono.
4. Calendario, notificaciones, ubicación y voz propia se solicitan solo al activar
   cada función.

Nunca pedir micrófono, ubicación y calendario en el mismo diálogo lógico. Antes de
cada permiso se explica qué desbloquea y qué ocurre al rechazarlo.

## Accesibilidad y diseño visual

- Objetivos táctiles de 48 dp como mínimo, también en cabecera y tarjetas.
- Etiquetas únicas y descriptivas para cada acción; el icono no sustituye al texto
  en flujos principales.
- No usar solo color para distinguir pendiente, confirmado, completado o ambiente.
- Mantener tipografía y espaciado de Material 3; eliminar variantes visuales que no
  expresen estado.
- Probar tamaño de fuente 200 %, TalkBack, contraste claro/oscuro, pantalla compacta,
  paisaje, teclado e insets.

Estas exigencias proceden de las guías oficiales de Android sobre etiquetas,
acciones y señales no dependientes del color ([Accessibility
principles](https://developer.android.com/guide/topics/ui/accessibility/principles))
y sobre objetivos táctiles de al menos 48 dp ([Make apps more
accessible](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)).
Si se publica para tablet/plegable, debe usarse un patrón adaptativo list-detail y
navegación por tamaño de ventana ([Build adaptive
apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)).

## Plan de ejecución del MVP

### P0 — claridad y confianza

- Hacer Agenda permanentemente accesible.
- Sustituir el FAB/long-press por `Capturar` con tres acciones etiquetadas.
- Separar permisos por función.
- Añadir onboarding y un recorrido de primera captura.
- Mover Chat y controles técnicos fuera de la cabecera básica.

### P1 — reducir superficie

- Simplificar Ajustes al esquema Básico/Experto anterior.
- Marcar Contexto ambiental como Beta y añadir corrección `Acertado/No acertado`.
- Ocultar ubicación, captura visual, resumen diario/semanal y personalización fina
  hasta disponer de métricas.
- Eliminar `DayTimelineScreen` y `CalendarImportedEventCard` si siguen sin uso.
- Dividir `CalendarScreen`, `SettingsScreen` y `KeywordListenerService` por estado y
  responsabilidad sin cambiar la UX visible.

### P2 — validación de venta

- Tests Compose: primera captura, confirmar sugerencia, posponer/completar, navegar
  días/mes, abrir Agenda con cero elementos, grabar, backup y denegar permisos.
- Sesiones moderadas en móvil pequeño y grande.
- Medir tiempo a primera captura, aceptación/descarte por fuente, uso de Agenda,
  errores de navegación, coste de batería y precisión ambiental etiquetada.
- Solo después decidir si Chat, ubicación, captura visual y Wear entran en el
  producto base o en extensiones.

No se fijan porcentajes comerciales arbitrarios: primero debe recogerse una línea
base real. La decisión de retirada o promoción de una función se documentará con
esas métricas, no con impresiones.
