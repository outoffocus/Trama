# Sistema de diseño de Trama

Versión 1.0 · 18 de septiembre de 2026

Este documento define las reglas visuales y de interacción de Trama para móvil, reloj y widget. Es la referencia para diseñar pantallas nuevas y revisar las existentes.

La dirección visual toma de la referencia adjunta su estructura ligera, el timeline continuo, los marcadores circulares, la jerarquía tipográfica y el uso contenido del color. Trama conserva su identidad propia: superficies cálidas, tipografía DM Sans, estados de captura explícitos y funcionamiento coherente en tema claro y oscuro.

## 1. Principios

### El tiempo es la estructura principal

Las tareas, reuniones, recuerdos y eventos deben aparecer en una secuencia temporal fácil de recorrer. La fecha seleccionada siempre debe ser visible y cambiar de día no debe sacar al usuario de contexto.

### Primero lo que el usuario decidió

Las tareas confirmadas aparecen antes que las sugerencias. Las propuestas del modelo se agrupan en una bandeja compacta y plegada. Nunca deben competir visualmente con una tarea vencida, una grabación activa o una acción que el usuario ya confirmó.

### Estado visible, acción predecible

Cada control debe explicar qué está ocurriendo ahora y qué ocurrirá al tocarlo. Se usan juntos texto, forma e icono; el color nunca es la única señal.

Ejemplos:

- `Escuchando` → punto ámbar y control de escucha activo.
- `Palabra clave reconocida` → estado verde azulado temporal, sin animaciones intrusivas.
- `Grabando · 12:40` → rojo, temporizador y acción inequívoca para detener.
- `Escucha en el reloj · cambiar al teléfono` → azul y dispositivo explícito.

### Menos contenedores, más jerarquía

No se debe encerrar cada elemento en una tarjeta elevada. El espacio, la alineación, el timeline y los divisores suaves deben resolver la mayoría de agrupaciones. Las tarjetas se reservan para bloques interactivos independientes, advertencias, sugerencias o contenido que necesite separación real.

### Una acción principal por contexto

Cada pantalla o bloque tiene una acción dominante. Las acciones secundarias usan menor contraste. Dos botones con consecuencias diferentes no deben competir con el mismo peso.

### Densidad cómoda

Trama debe mostrar suficiente información para entender el día de un vistazo, sin reducir las áreas táctiles ni apretar el contenido. La densidad se consigue eliminando decoración redundante, no reduciendo texto o controles.

## 2. Personalidad visual

Trama debe sentirse:

- Serena: fondos limpios, pocas interrupciones y movimiento discreto.
- Precisa: horas, estados y acciones alineados de forma consistente.
- Cálida: blanco roto y grafito en lugar de blanco o negro puros.
- Humana: textos directos y cotidianos, sin vocabulario interno del sistema.
- Fiable: los estados activos, guardados y pendientes siempre son verificables.

Evitar:

- Gradientes decorativos en varias zonas de una misma pantalla.
- Sombras fuertes, bordes oscuros o tarjetas anidadas.
- Iconos sin contexto para decisiones irreversibles o poco frecuentes.
- Colores nuevos para cada categoría generada por el modelo.
- Animaciones continuas que distraigan del timeline.

## 3. Color

Los tokens existentes en `ui/theme/Color.kt` son la base. Ninguna pantalla debe declarar colores semánticos directamente.

### Neutros

| Token | Claro | Oscuro | Uso |
|---|---:|---:|---|
| `background` | `#F7F4EE` | `#08090A` | Fondo general |
| `surface` | `#FFFFFF` | `#101214` | Contenido elevado o agrupado |
| `surface2` | `#F0ECE4` | `#171A1C` | Selección suave, chips y estados secundarios |
| `surface3` | `#E5DED2` | `#202427` | Controles presionados o separación fuerte |
| `text` | `#171614` | `#F4F0E8` | Texto principal |
| `muted` | `#6D665E` | `#9A958C` | Metadatos y texto secundario |
| `dim` | `#AAA298` | `#56524B` | Elementos completados o desactivados |

### Colores semánticos

| Token | Valor | Significado |
|---|---:|---|
| Ámbar | `#D48A52` | Acción pendiente, escucha activa, selección principal |
| Verde azulado | `#79B8A6` | Completado, confirmación, asistente, trigger reconocido |
| Rojo coral | `#E06A5C` | Grabación, error, vencimiento y acción destructiva |
| Amarillo suave | `#D2B45F` | Aviso, tarea para hoy o atención moderada |
| Azul | `#6EA1FF` | Reloj, sincronización y cambio de dispositivo |

### Reglas de uso

1. Una pantalla debe tener un solo acento dominante.
2. Los fondos de estado usan el color semántico con una opacidad aproximada del 12–16 %.
3. El texto normal nunca usa un acento si no comunica estado o acción.
4. El rojo se reserva para grabación, error, vencimiento o eliminación.
5. Completado combina verde azulado, icono de confirmación, texto atenuado y, cuando proceda, tachado.
6. Los elementos desactivados conservan contraste suficiente y explican por qué no están disponibles.

### Gradientes

Los gradientes se inspiran en la referencia y se usan solo en dos componentes:

- Selector del día actual o acción temporal destacada: ámbar → rojo coral.
- Progreso diario: verde azulado con una variación moderada de luminosidad.

No usar gradientes como fondo de pantalla, en tarjetas completas, en texto ni en más de un componente protagonista a la vez. En modo oscuro deben reducirse la saturación y el brillo para evitar halos.

## 4. Tipografía

La familia principal es **DM Sans**. **DM Mono** se usa únicamente para horas, contadores, estados cortos y metadatos que se benefician de una anchura estable.

| Rol | Estilo Compose | Uso |
|---|---|---|
| Fecha protagonista | `displaySmall`, Bold | Día seleccionado en Hoy |
| Título de pantalla | `headlineMedium`, SemiBold | Hoy, Acciones, Recuerdos, Ajustes |
| Título de bloque | `titleMedium`, SemiBold | Esta semana, Por revisar |
| Título de elemento | `titleMedium`, SemiBold | Acción o reunión |
| Cuerpo | `bodyMedium` | Descripciones y contexto |
| Metadato | `labelMedium` | Hora, fuente, estado y dispositivo |
| Etiqueta mínima | `labelSmall` | Contadores y categorías breves |

Reglas:

- Máximo dos pesos tipográficos dentro de un elemento del timeline.
- Los títulos se limitan normalmente a dos líneas con elipsis.
- Las horas usan cifras tabulares o DM Mono y se alinean por la derecha.
- Las mayúsculas se reservan para etiquetas breves de 1–3 palabras; nunca para frases.
- No reducir el cuerpo por debajo de 12 sp ni el texto interactivo por debajo de 13 sp.
- Respetar el tamaño de fuente del sistema. La interfaz debe soportar al menos 1,3× sin perder acciones esenciales.

## 5. Retícula y espaciado

La unidad base es **4 dp**.

| Uso | Medida |
|---|---:|
| Margen horizontal de pantalla | 16 dp |
| Separación mínima entre bloques | 24 dp |
| Separación entre elementos relacionados | 8 dp |
| Padding de tarjeta o bloque | 12–16 dp |
| Separación entre FAB | 10–12 dp |
| Altura táctil mínima | 48 dp |
| FAB principal | 56 dp |
| Radio de tarjetas | 8–12 dp |
| Radio de chips | 20 dp o píldora completa |

Los elementos del timeline comparten tres columnas:

1. Marcador y línea: 40–48 dp.
2. Contenido flexible: ocupa el espacio restante.
3. Hora y estado: 64–80 dp, alineados a la derecha.

En pantallas estrechas, la tercera columna puede pasar debajo del título, pero debe mantener alineados hora y estado entre sí.

## 6. Anatomía de pantalla

### Barra superior

- Título corto y reconocible.
- Máximo dos acciones visibles además de navegación.
- El estado de escucha puede convivir con el título mediante una píldora compacta.
- Las acciones poco frecuentes van a un menú o a la pantalla de detalle.
- La barra no debe duplicar una acción principal ya visible en el contenido.

### Selector de fecha

- La fecha seleccionada permanece cerca de la parte superior.
- Debe ser posible avanzar y retroceder por días con un toque o gesto horizontal.
- `Hoy` es una acción directa y fácil de reconocer.
- El calendario mensual se expande bajo demanda y conserva el día seleccionado.
- El cambio de fecha actualiza el timeline sin navegar a otra pantalla.

### Resumen diario

Puede mostrar progreso cuando existe un total comprensible, por ejemplo `3 de 5 tareas`. Debe incluir el valor textual además de la barra. No se muestra un porcentaje si el denominador no tiene significado claro.

### Navegación inferior

Las tres secciones principales son `Hoy`, `Acciones` y `Recuerdos`. El elemento activo combina icono, texto y color. No se crean accesos alternativos con nombres distintos que lleven exactamente al mismo destino sin aplicar contexto.

## 7. Timeline

El timeline es el patrón principal de Hoy, el widget y las listas temporales.

### Línea temporal

- Línea vertical de 1 dp con el color `hairline`.
- Se interrumpe antes y después de encabezados de sección.
- No atraviesa tarjetas ni texto.
- Puede ser discontinua para indicar huecos o separación temporal.

### Marcadores

| Estado | Marcador |
|---|---|
| Pendiente | Círculo de contorno ámbar o neutro |
| Para hoy | Contorno amarillo suave |
| En curso | Círculo mayor con rojo coral y control de pausa/detención |
| Completada | Círculo verde azulado con check |
| Sugerida | Círculo pequeño verde azulado con contorno discontinuo o etiqueta `Sugerida` |
| Error | Círculo rojo con icono de aviso |
| Reunión | Marcador rojo durante la grabación; neutro al terminar |

El marcador normal ocupa 28–32 dp. El estado activo puede crecer hasta 44–48 dp, sin desplazar el contenido horizontal.

### Fila del timeline

Cada fila contiene:

- Acción o título en lenguaje natural.
- Contexto opcional: proyecto, persona, procedencia o fragmento breve.
- Hora o fecha relevante.
- Estado textual corto.
- Acción rápida solo si es frecuente y segura.

Las filas completadas se atenúan y pueden tacharse. No deben desaparecer inmediatamente al completarse: el usuario necesita confirmar visualmente el resultado. Los detalles secundarios de una tarea completada pueden ocultarse.

### Agrupación temporal

Usar encabezados claros: `Hoy`, `Mañana`, `Esta semana`, `Más adelante`, `Sin fecha`. Los eventos posteriores a hoy aparecen bajo su fecha o en Acciones; no se duplican en un bloque ambiguo de próximos compromisos.

## 8. Tareas, notas y sugerencias

### Vocabulario

Usar siempre:

- `Tarea` para una acción que se puede completar.
- `Nota` para información guardada sin acción.
- `Sugerencia` para una acción propuesta que aún necesita confirmación.
- `Reunión` para una grabación y su análisis.
- `Recuerdos` para la colección navegable, no para cada objeto individual.

Acciones estándar:

- `Añadir a tareas`
- `Marcar como hecha`
- `Corregir por voz`
- `Guardar cambios`
- `Crear de nuevo la tarea`
- `Conservar ambas`
- `Descartar sugerencia`

### Sugerencias

- Se muestran después de las tareas confirmadas.
- El grupo empieza plegado: `3 sugerencias por revisar`.
- Una reunión conserva sus sugerencias dentro del detalle hasta que el usuario las aprueba.
- Una misma intención no puede aparecer simultáneamente como tarea y sugerencia.
- Aceptar explica el resultado mediante `Añadir a tareas`.
- Descartar permite deshacer. Indicar el motivo es opcional.

### Edición y reanálisis

- `Guardar cambios` conserva exactamente la edición del usuario.
- `Crear de nuevo la tarea` solicita al modelo local interpretar otra vez el texto y puede cambiar acción, fecha o título.
- Ambos controles deben tener jerarquía distinta y una explicación breve cuando el resultado pueda sorprender.
- Si falla el modelo local, el texto corregido no se pierde.

## 9. Controles de captura

Se mantienen tres FAB flotantes en vertical, dentro de la zona cómoda del pulgar y sin tapar el timeline:

1. Cambiar teléfono/reloj.
2. Grabar reunión.
3. Activar o detener escucha continua.

Reglas:

- Máximo tres FAB visibles.
- Se alinean al borde inferior derecho con 16 dp de margen y respetan las barras del sistema.
- El orden no cambia entre pantallas.
- El icono cambia cuando la acción pasa de iniciar a detener.
- El estado global se muestra también en una píldora visible; no depende del tooltip del FAB.
- Las etiquetas de accesibilidad describen estado y resultado: `Escucha en el teléfono · cambiar al reloj`.
- Durante el primer uso puede aparecer una ayuda breve junto al control: `Actívalo y di “recuérdame…”`.
- La grabación muestra siempre temporizador.
- Al reconocer el trigger, solo cambia el color y el texto del estado durante unos segundos. No usar escalado, rebote ni destellos.
- Un FAB bloqueado debe explicar el motivo en el estado cercano o mediante mensaje al tocarlo.

En Acciones y Recuerdos los FAB conservan el mismo tamaño y posición. Pueden reducir su énfasis cromático cuando ninguna captura está activa, pero no deben moverse ni convertirse en una barra que ocupe el ancho.

## 10. Componentes

### Tarjetas

- Radio: 8 dp para filas densas; 12 dp para bloques destacados.
- Elevación: 0 dp por defecto.
- Borde: 0,5–1 dp con `softBorder`.
- Una franja de 3 dp puede indicar el estado semántico.
- No anidar más de una tarjeta dentro de otra.

### Chips y filtros

- Forma de píldora.
- Etiquetas breves y concretas.
- Selección mediante fondo suave, borde y cambio de texto.
- Los filtros no sustituyen el título ni esconden contenido hasta que se escribe una búsqueda.

### Botones

- Botón relleno: acción principal.
- Botón tonal o de contorno: acción secundaria.
- Botón de texto: acción terciaria o contextual.
- Acción destructiva: texto o fondo rojo, separada de la acción principal.
- En un diálogo, el orden y el texto deben describir el resultado; evitar `Aceptar` cuando puede usarse un verbo concreto.

### Campos

- Etiqueta persistente cuando el significado pueda perderse al escribir.
- Mensajes de error junto al campo, con una solución posible.
- Búsqueda con acción para limpiar y resultados recientes antes de escribir.
- El teclado no debe ocultar el botón de guardar o enviar.

### Estados vacíos

Un estado vacío incluye:

1. Qué falta.
2. Qué puede hacer el usuario.
3. Una sola acción, si es necesaria.

Ejemplo: `Todavía no hay recuerdos. Cuando guardes notas, lugares o reuniones aparecerán aquí.`

## 11. Iconografía

- Usar iconos Material con grosor y tamaño consistentes.
- Tamaño habitual: 18–24 dp.
- Un icono sin etiqueta solo se permite para acciones universales y repetidas: volver, cerrar, buscar, completar o reproducir.
- Reloj, micrófono y grabación deben acompañarse de un estado textual en la pantalla.
- No usar emojis como iconografía estructural. Pueden aparecer en contenido generado o en opciones informales si no sustituyen la etiqueta.

## 12. Movimiento y respuesta

- Duración habitual: 150–250 ms.
- Expandir o plegar: 200–300 ms.
- Cambio de color de trigger: inmediato al reconocer y retorno suave después del tiempo real de escucha.
- La pulsación debe producir respuesta visual en menos de 100 ms.
- Evitar rebotes, zoom repetido, parpadeo y movimiento decorativo.
- Respetar la preferencia de reducir movimiento del sistema.
- Las operaciones largas muestran el paso actual: `Transcribiendo`, `Analizando`, `Creando tareas`.

## 13. Accesibilidad

- Contraste mínimo WCAG AA: 4,5:1 para texto normal y 3:1 para texto grande o componentes.
- Área táctil mínima de 48 × 48 dp.
- Ningún estado se comunica solo mediante color.
- Los controles iconográficos tienen `contentDescription` orientada a la acción.
- El orden de lectura sigue fecha → estado → contenido → acción.
- Los temporizadores se anuncian sin actualizar accesibilidad cada segundo; usar intervalos razonables.
- Los textos admiten ampliación sin solaparse con horas, botones o FAB.
- El modo oscuro conserva la misma semántica, no invierte el significado de los acentos.

## 14. Reloj y widget

### Reloj

- Mostrar primero dónde está activa la escucha.
- La acción principal es contextual: `Escuchar aquí`, `Detener escucha` o `Grabar aquí`.
- Si el teléfono tiene el control, tocar `Escuchar aquí` debe intentar el cambio directamente.
- Evitar párrafos; usar una acción y un estado por pantalla.

### Widget

- Representa el timeline de hoy y muestra solo acciones.
- Comparte colores, marcadores, iconos y orden de FAB con la app.
- Muestra `Escuchando`, `Palabra clave reconocida`, `Grabando · mm:ss`, `Procesando` o el dispositivo activo.
- Prioriza tareas pendientes y vencidas. Las completadas pueden mostrarse atenuadas si queda espacio.
- Cada fila abre su detalle; los controles de captura actúan sin exigir abrir la app cuando Android lo permita.

## 15. Texto de interfaz

- Usar frases cortas, verbos concretos y español natural.
- Describir el resultado: `Añadir a tareas`, no `Aceptar`.
- Evitar términos internos como `entrada`, `backend`, `LLM`, `pipeline` o `embedding` fuera de Diagnóstico.
- Usar `alrededor de las 08:30` para procesos programados que Android puede ejecutar con margen.
- Los errores explican qué ocurrió y qué se puede hacer ahora.
- No atribuir certeza al modelo cuando está proponiendo una interpretación.

## 16. Aplicación en Jetpack Compose

- Usar `MaterialTheme.colorScheme` para fondos y texto estructural.
- Usar `LocalTramaColors.current` para acentos y metadatos semánticos.
- Reutilizar `StatusPill`, `SectionRule`, `TramaChip`, `DateHero`, `SoftCard` y `TramaCard` antes de crear variantes nuevas.
- Los tamaños, radios y colores compartidos deben convertirse en tokens; no repetir valores mágicos en pantallas.
- Cada componente nuevo debe incluir estado normal, pulsado, seleccionado, desactivado, carga y error cuando sean aplicables.
- Las previews deben cubrir tema claro, oscuro y fuente ampliada.

## 17. Lista de revisión

Antes de aprobar una pantalla:

- [ ] ¿La fecha o contexto temporal se entiende sin navegar atrás?
- [ ] ¿La tarea principal aparece antes que las sugerencias del sistema?
- [ ] ¿Existe una sola acción dominante?
- [ ] ¿Cada icono poco común tiene etiqueta o explicación visible?
- [ ] ¿Escucha, trigger, grabación y dispositivo se reconocen de un vistazo?
- [ ] ¿Las horas y estados mantienen una alineación consistente?
- [ ] ¿Se ha evitado una tarjeta cuando bastaban espacio y alineación?
- [ ] ¿Los textos describen el resultado de la acción?
- [ ] ¿La pantalla funciona en claro, oscuro y con fuente ampliada?
- [ ] ¿Los elementos táctiles miden al menos 48 dp?
- [ ] ¿El color se acompaña de texto, icono o forma?
- [ ] ¿El timeline y los FAB pueden coexistir sin taparse?
- [ ] ¿El mismo concepto usa el mismo nombre en toda la app?

