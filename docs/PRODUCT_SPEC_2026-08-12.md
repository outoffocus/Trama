# Especificación de producto v0.2

Fecha: `2026-08-12`  
Estado: dirección de producto aprobada; concepto visual pendiente de elección.
Precedencia: este documento sustituye la dirección de producto de los planes MVP
anteriores cuando exista una contradicción.

## Definición breve

> Trama es una memoria diaria privada que espera en el dispositivo las frases de
> captura elegidas por el usuario y reúne lo que este decide guardar con su agenda
> y los lugares donde ha estado.

Trama no pretende grabarlo todo ni interpretar de forma indiscriminada el sonido
ambiental. Su valor es capturar una intención sin tocar el teléfono y permitir
recuperarla después dentro del contexto real del día.

## Decisiones ya tomadas

- Usuario principal: persona ocupada que no quiere olvidar decisiones, ideas y
  compromisos.
- Promesa: `Dilo y aparecerá organizado en tu día`.
- `CalendarScreen` sigue siendo Home y el tiempo continúa siendo el eje del producto.
- Se mantiene la navegación inferior por días, mes y vuelta a hoy.
- Agenda sincronizada y traza de ubicación siguen siendo funciones principales.
- La app ofrece un modo persistente que espera **una frase de activación
  configurable**, inicialmente `Trama`, sin grabar de forma continua.
- Al reconocer esa frase comienza una captura breve de la orden y termina al detectar
  silencio. Esto pertenece al modo continuo y no es una `grabación bajo demanda`.
- El reconocimiento de la activación será estricto; la captura posterior priorizará
  no perder el contenido dictado.
- La detección se confirma con un sonido o vibración breve.
- El usuario ve siempre un estado comprensible de la captura.
- Todo el reconocimiento y análisis de contenido personal se realiza localmente.
- El producto no incluye modelos cloud ni claves API.
- Home debe ser más simple, cálido, agradable y legible.
- La pantalla de detalle se rediseñará alrededor de la acción útil, no del diagnóstico.
- Las notas simples pueden guardarse directamente; tareas, citas y recordatorios
  necesitan confirmación humana.
- Chat es una herramienta secundaria de búsqueda y recuperación.
- Wear OS es una extensión opcional para capturar y grabar, sin exigir paridad total.
- La interfaz utilizará la tipografía del sistema para mejorar legibilidad,
  disponibilidad offline y coherencia con el tamaño configurado por el usuario.
- Cambios de navegación, tipografía, jerarquía o posición de botones requieren
  aprobación previa de una propuesta concreta.

## Experiencia esencial

### Los cuatro casos de uso de audio

`Escucha continua` y `grabación bajo demanda` son productos distintos. La primera
espera una activación y no conserva una reunión. La segunda empieza al pulsar
`Grabar`, conserva una sesión completa durante el procesado y produce un documento
de reunión.

| Caso | Inicio y final | Resultado | Retención del audio |
| --- | --- | --- | --- |
| Escucha continua · móvil | Se activa una vez; espera `Trama` en el micrófono del teléfono y captura la orden posterior hasta silencio | Nota directa o tarea/cita pendiente de confirmar | La espera nunca se persiste; la orden se descarta después de procesarla |
| Escucha continua · reloj | Se activa una vez; el reloj espera `Trama`, captura la orden y la envía al teléfono | El teléfono transcribe y crea el mismo resultado que el móvil | La espera nunca se persiste; el reloj borra la captura cuando la transferencia queda confirmada |
| Grabación bajo demanda · móvil | El usuario pulsa `Grabar reunión` y la detiene explícitamente, con límite de seguridad | Grabación, transcripción diarizada visible, resumen y acciones extraíbles | Se elimina tras completar el procesado salvo que el usuario elija `Conservar grabación` |
| Grabación bajo demanda · reloj | El usuario inicia y detiene la reunión desde el reloj; el audio se transfiere al teléfono | El teléfono genera la misma ficha de reunión, transcripción diarizada, resumen y acciones | El reloj borra su copia tras confirmar la transferencia; el teléfono aplica la preferencia `Conservar grabación` |

Solo un dispositivo será propietario de la escucha continua cada vez. La propuesta
recomendada es transferencia explícita `Móvil ↔ Reloj`, porque evita duplicados,
estados contradictorios y doble consumo. La grabación bajo demanda pausa temporalmente
la escucha continua del dispositivo que use el micrófono y esta se recupera al terminar.

En móvil, conectar auriculares no cambia por defecto el origen de captura: tanto la
escucha continua como una reunión usarán el micrófono integrado del teléfono. En el
reloj se usa su propio micrófono.

### Captura activada por voz

1. El usuario activa el modo una vez de forma explícita y concede el permiso de
   micrófono.
2. Trama mantiene una escucha local ligera para una única frase de activación
   configurable, inicialmente `Trama`.
3. Durante la espera no existe una grabación continua. El audio solo atraviesa una
   ventana efímera en memoria para detectar la activación y nunca se persiste.
4. Al reconocer la frase, Trama da feedback inmediato y comienza una captura breve
   de la orden hasta detectar silencio.
5. La transcripción se transforma localmente en nota, tarea, cita o sugerencia,
   según el contrato que se apruebe.
6. El usuario puede corregir, confirmar o descartar el resultado.

Una frase de activación corta pero distintiva reduce esfuerzo sin convertir cientos
de expresiones normales en disparadores. El MVP no volverá a ofrecer listas masivas:
mantendrá una sola activación personal y una gramática compacta para interpretar lo
dicho después.

### Grabación bajo demanda y diarización

La reunión es una captura deliberada, potencialmente larga. Su pantalla mostrará:

1. título, fecha, duración, dispositivo de origen y estado de procesado;
2. reproducción solo si se conserva el audio;
3. resumen y puntos clave;
4. acciones sugeridas, siempre revisables antes de confirmar;
5. transcripción completa con segmentos temporales y hablantes;
6. opción de corregir o renombrar hablantes.

La diarización separa voces; no identifica automáticamente personas. Las etiquetas
iniciales serán `Tú`, únicamente cuando la huella local permita afirmarlo con el
umbral aprobado, y `Persona 2`, `Persona 3` o `No identificado` para el resto. Una
atribución incierta permanece sin identificar. Nunca se deducirá un nombre por el
contenido de la conversación.

Estado técnico actual: móvil y reloj ya graban, transfieren, transcriben y extraen
acciones, pero la transcripción todavía es un bloque de texto. La diarización no está
integrada en el pipeline y debe validarse on-device en batería, memoria, tiempo y
precisión antes de considerarla terminada. La verificación actual de `Solo mi voz` no
sustituye la segmentación de múltiples hablantes.

### Estados visibles

La interfaz y la notificación deben usar el mismo modelo de estados:

| Estado | Significado visible | ¿Se guarda audio? |
| --- | --- | --- |
| `Inactivo` | Micrófono apagado | No |
| `En espera · móvil/reloj` | Ese dispositivo espera `Trama` | No, solo ventana efímera en RAM |
| `Capturando orden` | Activación detectada; capturando hasta silencio | Sí, solo en memoria durante el procesado |
| `Grabando reunión · móvil/reloj` | Grabación bajo demanda iniciada por el usuario | Sí, en almacenamiento privado temporal |
| `Procesando` | Transcribiendo, diarizando o preparando el resultado localmente | Solo hasta completar el procesado, salvo conservación explícita |
| `Necesita atención` | Falta permiso, modelo o el servicio falló | No hasta resolverlo |

`Escuchando` no puede presentarse como si el micrófono estuviera apagado. Android
muestra además su indicador de privacidad y exige un servicio en primer plano para
continuar una captura iniciada por el usuario. El estado debe explicar la diferencia
entre usar el micrófono y guardar una captura.

### Home — arquitectura aprobada, ejecución visual pendiente

Orden visual recomendado:

1. fecha seleccionada y dos utilidades como máximo;
2. tarjeta de estado de ancho completo, con modo, explicación de una línea y acción
   `Pausar` o `Activar`;
3. timeline único del día: calendario, lugares, capturas y tareas;
4. una acción `Grabar reunión` claramente etiquetada y entrada manual de respaldo;
5. navegación temporal inferior actual, conservando su funcionamiento.

Se eliminarían del primer nivel los estados técnicos, contadores de diagnóstico y
acciones ambiguas u ocultas tras pulsaciones largas. Agenda debe tener una entrada
permanente aunque esté vacía, para evitar que su navegación aparezca y desaparezca.

### Detalle de una entrada — arquitectura aprobada, ejecución visual pendiente

La pantalla actual concede demasiado peso a iconos sin texto y datos técnicos. La
nueva jerarquía propuesta es:

1. contenido de la entrada como elemento principal y editable;
2. una acción dominante según el estado: `Confirmar`, `Completar` o `Reabrir`;
3. fecha, prioridad y tipo como propiedades editables y comprensibles;
4. origen y evidencia en `Cómo se creó`, plegado por defecto;
5. acciones secundarias con texto; compartir y eliminar dentro de `Más`;
6. diagnóstico de ASR, confianza y textos intermedios solo en modo avanzado.

Para grabaciones, el orden recomendado es reproductor, resumen, acciones sugeridas
y transcripción plegada. Para lugares, identidad y visitas son el contenido principal;
los datos de resolución geográfica quedan en segundo nivel.

### Tipografía y tono visual — dirección aprobada, ejecución pendiente

La app adoptará la tipografía del sistema. La base actual mezcla DM Sans con etiquetas
de 10–11 sp en DM Mono, haciendo que gran parte de la app parezca técnica y reduciendo
la legibilidad. La ejecución deberá:

- usar la fuente sans del sistema en toda la experiencia cotidiana;
- reservar una monoespaciada del sistema para diagnóstico estrictamente técnico;
- elevar el cuerpo habitual a 14–16 sp con interlineado cómodo;
- respetar el escalado de fuente y evitar descargas tipográficas;
- mantener la paleta cálida, reduciendo el número de acentos simultáneos.

### Auriculares y origen del micrófono

- Con auriculares conectados, el origen predeterminado seguirá siendo el micrófono
  integrado del teléfono.
- La salida puede permanecer en los auriculares sin activar su micrófono.
- Ajustes avanzados permitirá elegir `Teléfono`, `Auriculares` o `Automático`.
- La app comprobará el dispositivo realmente enrutado y lo mostrará en el estado;
  no afirmará que usa el teléfono si Android o el fabricante no respetan la preferencia.
- Esta política se aplica a la espera, a la orden activada por voz y a la grabación
  bajo demanda.

## Alcance del primer producto vendible

### Debe incluir

- escucha continua en móvil y reloj con una activación configurable;
- grabación de reuniones bajo demanda en móvil y reloj;
- estado de escucha/captura inequívoco;
- Home temporal simplificado;
- agenda y calendarios seleccionados;
- estancias y lugares;
- revisión de resultados y confirmación humana;
- grabaciones voluntarias con transcripción local;
- búsqueda y recuperación por Chat local;
- exportación, copia y borrado de datos;
- Wear OS solo si su flujo esencial se decide como parte del MVP.

### No debe incluir en el primer nivel

- contexto ambiental como promesa principal;
- cientos de frases predefinidas;
- controles de motores, prompts, umbrales o radios;
- diagnósticos de ASR mezclados con contenido personal;
- acciones críticas solo mediante gestos ocultos;
- modelos remotos o API keys.

## Contrato de calidad

- La app nunca dice que un modelo no está instalado si existe un archivo compatible.
- Instalación, activación y fallo de carga son estados diferentes y recuperables.
- Cada pantalla pública tiene una entrada estable y una vuelta predecible al día
  seleccionado.
- El usuario puede identificar el modo de micrófono y si se está guardando algo sin
  interpretar un icono aislado.
- Una captura ambigua no se convierte silenciosamente en un hecho fiable.
- El audio de la espera y de una orden activada por `Trama` nunca se conserva.
- El audio de una reunión bajo demanda se elimina solo después de obtener una
  transcripción íntegra y durable, salvo que el usuario elija `Conservar grabación`.
- Una transferencia desde el reloj no borra la única copia antes de que el teléfono
  confirme recepción e integridad.
- La diarización no atribuye nombres sin confirmación y marca los segmentos dudosos.
- Precisión, cobertura, batería y temperatura se validan con jornadas reales antes
  de prometer funcionamiento continuo.

## Restricciones de plataforma verificadas

- Android exige declarar el tipo de servicio de micrófono y mostrar un servicio en
  primer plano para continuar la captura; el inicio normal debe partir de una acción
  visible del usuario: [Foreground services y micrófono](https://developer.android.com/about/versions/14/changes/fgs-types-required).
- Android recomienda mostrar un indicador en tiempo real cuando se captura audio y
  degradar la función sin romper el resto de la app si falta permiso:
  [Privacy checklist](https://developer.android.com/privacy-and-security/about).
- La UI seguirá Material 3 y deberá adaptarse a tamaños grandes sin cambiar el modelo
  mental del calendario: [Material 3 en Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).

## Puerta de decisión

Las decisiones de producto se cerraron el `2026-08-12`. El equipo preparará un único
concepto de navegación y dos variantes visuales comparables. Solo la variante elegida
por el usuario pasará a código; cualquier desviación posterior de navegación,
jerarquía, botones o tipografía requerirá una nueva aprobación explícita.
