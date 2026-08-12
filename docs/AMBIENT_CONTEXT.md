# Contexto ambiental local

## Objetivo y alcance

Esta entrega cierra la fase 6 del plan especifico de mejora de captura de audio:
separar el *contexto ambiental* de las tareas. La escucha intencional sigue siendo
la ruta para crear tareas; el contexto ambiental solo añade bloques descriptivos
al día.

La función está desactivada por defecto y se activa en
`Ajustes > Captura y contexto > Contexto ambiental`.
También requiere que `Ajustes > Escucha automática > Escucha continua` esté
activada. Al desactivar la escucha, el contexto ambiental queda inactivo, mientras
calendarios, ubicación y grabaciones manuales continúan funcionando.

## Qué guarda

Solo existen cuatro categorías:

- `MUSIC`: música.
- `TELEVISION`: televisión o radio.
- `CONVERSATION`: conversación.
- `MEETING`: reunión o conversación de trabajo.

Cada detección termina como un `TimelineEvent` de tipo `AMBIENT_CONTEXT`. El
evento contiene categoría, inicio, fin aproximado, número de señales agrupadas y,
si está disponible, el lugar activo. No es una `DiaryEntry`, no aparece como
pendiente y no puede convertirse automáticamente en tarea.

## Qué no guarda

- No guarda el audio de la escucha contextual.
- No guarda la transcripción que permitió clasificar el ambiente.
- No guarda interlocutores, temas, emociones ni inferencias sensibles.
- No usa Gemma ni ningún servicio remoto.

El evento de diagnóstico `ASR_FINAL` omite el texto cuando la captura se enruta a
contexto ambiental. `AMBIENT_CONTEXT` registra únicamente la categoría, la
confianza, la evidencia técnica y el motivo de exclusión o agrupación.

## Protección contra ruido

- Solo se evalúan ventanas `uncertain_fallback`; una captura disparada por una
  frase de intención conserva la ruta normal de tareas.
- Las expresiones personales como `recuérdame`, `anota`, `tengo que` o `necesito`
  nunca se clasifican como ambiente.
- Se exige una señal textual conservadora y una confianza mínima de `0,82`.
- Señales de la misma categoría se agrupan durante 45 minutos.
- Un cambio de categoría tiene 15 minutos de enfriamiento.
- Se crean como máximo 12 bloques nuevos por día.

Estas reglas evitan sustituir el antiguo problema de cientos de frases por cientos
de eventos. El clasificador no contiene un catálogo de expresiones del usuario:
usa cuatro categorías y un conjunto corto de indicios ambientales verificables.

## Exclusiones

- Horario configurable; si inicio y fin coinciden, se considera todo el día.
- Exclusión opcional de Casa y Trabajo cuando existe una estancia de ubicación
  activa y el lugar está marcado con esa función.
- El audio reproducido por aplicaciones del propio teléfono se excluye siempre:
  Android solo informa que hay reproducción multimedia, no una identidad de app
  fiable que justifique pedir acceso invasivo adicional.

Una TV o radio externa no activa la señal multimedia de Android. Puede detectarse
por indicios claros de emisión y producir un bloque `Televisión o radio`. Si el
audio no contiene evidencia suficiente, se descarta: la app no inventa contexto.

## Persistencia y compatibilidad

No cambia el esquema Room. `TimelineEvent.type` y `source` ya son campos de texto,
por lo que `AMBIENT_CONTEXT` y `AMBIENT_LOCAL` son compatibles con la base v16 sin
migración. Los eventos se incluyen en el backup v3 de la cronología.

Los ajustes se guardan en DataStore y mantienen estos valores iniciales:

- desactivado;
- horario `07:00–23:00`;
- Casa y Trabajo no excluidos hasta que el usuario lo decida.

## Diagnóstico esperado

En `Ajustes > Audio y diagnóstico`:

- `Bloques ambientales guardados/agrupados` cuenta inserciones y fusiones;
- `Contexto ambiental excluido/limitado` cuenta horario, lugar, cooldown, límite
  diario o indisponibilidad de persistencia;
- el export de diagnóstico conserva motivos técnicos, nunca la transcripción de
  un bloque ambiental.

## Limitaciones verificadas

- No hay clasificador acústico general: Silero solo decide habla/no habla. Música
  sin una etiqueta textual clara puede no producir un bloque.
- La atribución `Televisión o radio` exige indicios de emisión; un diálogo externo
  sin esos indicios queda como `Conversación` o se descarta.
- Los intervalos son aproximados porque se construyen a partir de muestreos
  limitados por batería y cooldown, no de vigilancia semántica continua.
- La calibración final requiere jornadas reales con TV, reuniones, música y
  conversación etiquetadas por el usuario. No se afirma una tasa de acierto sin
  ese corpus.
