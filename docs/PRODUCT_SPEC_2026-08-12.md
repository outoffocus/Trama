# Especificación de producto v0.1

Fecha: `2026-08-12`  
Estado: base de trabajo para aprobar el rediseño.  
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

- `CalendarScreen` sigue siendo Home y el tiempo continúa siendo el eje del producto.
- Se mantiene la navegación inferior por días, mes y vuelta a hoy.
- Agenda sincronizada y traza de ubicación siguen siendo funciones principales.
- La app ofrece un modo persistente que espera frases configurables sin exigir
  pulsar un botón para cada captura.
- El usuario ve siempre un estado comprensible de la captura.
- Todo el reconocimiento y análisis de contenido personal se realiza localmente.
- El producto no incluye modelos cloud ni claves API.
- Home debe ser más simple, cálido, agradable y legible.
- La pantalla de detalle se rediseñará alrededor de la acción útil, no del diagnóstico.
- Cambios de navegación, tipografía, jerarquía o posición de botones requieren
  aprobación previa de una propuesta concreta.

## Experiencia esencial

### Captura sin manos

1. El usuario activa el modo una vez de forma explícita y concede el permiso de
   micrófono.
2. Trama mantiene una escucha local ligera para un conjunto pequeño de frases de
   activación configurables.
3. Antes de una activación, el audio solo ocupa una ventana efímera en memoria.
4. Al reconocer una frase, Trama da feedback inmediato y conserva la intervención
   necesaria para transcribirla.
5. La transcripción se transforma localmente en nota, tarea, cita o sugerencia,
   según el contrato que se apruebe.
6. El usuario puede corregir, confirmar o descartar el resultado.

Las frases largas y específicas reducen activaciones accidentales. Las palabras
aisladas y las listas masivas aumentan colisiones y coste. El MVP no debe volver a
ofrecer cientos de frases equivalentes: proponemos entre 3 y 7 frases personales,
más una gramática compacta de acciones. La cifra final queda pendiente de aprobación.

### Estados visibles

La interfaz y la notificación deben usar el mismo modelo de estados:

| Estado | Significado visible | ¿Se guarda audio? |
| --- | --- | --- |
| `Inactivo` | Micrófono apagado | No |
| `En espera` | Micrófono activo; esperando una frase | No, solo ventana efímera en RAM |
| `Capturando` | Frase detectada; guardando la intervención | Sí |
| `Procesando` | Transcribiendo o preparando el resultado localmente | Solo según la política de retención |
| `Necesita atención` | Falta permiso, modelo o el servicio falló | No hasta resolverlo |

`Escuchando` no puede presentarse como si el micrófono estuviera apagado. Android
muestra además su indicador de privacidad y exige un servicio en primer plano para
continuar una captura iniciada por el usuario. El estado debe explicar la diferencia
entre usar el micrófono y guardar una captura.

### Home — propuesta del equipo, no aprobada todavía

Orden visual recomendado:

1. fecha seleccionada y dos utilidades como máximo;
2. tarjeta de estado de ancho completo, con modo, explicación de una línea y acción
   `Pausar` o `Activar`;
3. timeline único del día: calendario, lugares, capturas y tareas;
4. una acción manual de respaldo claramente etiquetada;
5. navegación temporal inferior actual, conservando su funcionamiento.

Se eliminarían del primer nivel los estados técnicos, contadores de diagnóstico y
acciones ambiguas u ocultas tras pulsaciones largas. Agenda debe tener una entrada
permanente aunque esté vacía, para evitar que su navegación aparezca y desaparezca.

### Detalle de una entrada — propuesta del equipo, no aprobada todavía

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

### Tipografía y tono visual — propuesta del equipo, no aprobada todavía

La base actual, DM Sans, es adecuada para una interfaz cálida. El problema principal
no es la familia sino su uso: etiquetas de 10–11 sp en DM Mono hacen que gran parte
de la app parezca técnica y reducen la legibilidad. Proponemos:

- reservar la monoespaciada para diagnóstico y datos estrictamente técnicos;
- usar la sans en navegación, estados y etiquetas habituales;
- elevar el cuerpo habitual a 14–16 sp con interlineado cómodo;
- empaquetar la fuente o garantizar un fallback consistente sin depender de una
  descarga al primer uso;
- mantener la paleta cálida, reduciendo el número de acentos simultáneos.

## Alcance del primer producto vendible

### Debe incluir

- captura por frases configurables y captura manual de respaldo;
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

Antes de modificar Home, detalle, navegación, botones o tipografía se cerrarán las
preguntas de producto planteadas al usuario. Después se preparará un único concepto
de navegación y dos variantes visuales comparables para aprobación. Solo la variante
aprobada pasará a código.
