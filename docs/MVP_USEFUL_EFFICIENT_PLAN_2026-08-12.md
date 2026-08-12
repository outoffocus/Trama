# Plan de MVP útil, preciso y eficiente

Fecha de decisión: `2026-08-12`.

Este documento sustituye como dirección de producto a las propuestas que situaban
la escucha ambiental en el centro. Parte del diagnóstico real exportado el
`2026-08-12`: durante la nueva jornada se evaluaron 2.596 segmentos, se obtuvieron
13 transcripciones finales, se guardaron 2 entradas y el usuario no recuperó
ningún recuerdo útil que no hubiese provocado. La escucha funcionó técnicamente,
pero no justificó su coste ni su complejidad.

## Decisión de producto

La promesa del MVP será:

> Trama reúne lo que tenías previsto, dónde estuviste y lo que decidiste guardar
> o hacer, en un calendario diario privado.

La utilidad no depende de un micrófono siempre activo. El día se reconstruye con
cuatro fuentes comprensibles:

1. agenda y calendarios seleccionados;
2. estancias y lugares detectados mediante ubicación;
3. notas y tareas que el usuario escribe o dicta deliberadamente;
4. grabaciones voluntarias de reuniones o conversaciones.

La escucha continua pasa a ser una opción explícita, fuera de la promesa
comercial. Las instalaciones nuevas ya parten con la intención persistida de
escucha desactivada; el contexto ambiental depende de ella y permanece
experimental.

## Contrato que no se debe romper

- `CalendarScreen` continúa siendo Home.
- Se mantiene la navegación existente por día, mes y vuelta a hoy.
- Los eventos de los calendarios seleccionados siguen apareciendo en el timeline.
- La traza de ubicación basada en estancias (*dwells*) y lugares sigue funcionando
  aunque la escucha continua esté desactivada.
- Agenda, grabaciones, entrada manual, búsqueda, copia y procesamiento local no
  dependen de la escucha continua.
- Desactivar la escucha detiene el servicio de micrófono, su watchdog, sus avisos
  de recuperación y la escucha equivalente del reloj. No detiene una grabación
  manual que ya esté en curso en el reloj.
- No se introduce ninguna API ni modelo remoto.

## Alcance del MVP

### Núcleo visible

1. **Home temporal**: calendario diario, selector mensual y timeline conjunto.
2. **Agenda sincronizada**: selección de calendarios, importación de eventos y
   actualización al abrir la app o cambiar una fuente.
3. **Traza de lugares**: estancias relevantes, duración y acceso al detalle del
   lugar; no un mapa de coordenadas permanente.
4. **Captura rápida deliberada**: escribir, dictar una tarea o iniciar una
   grabación mediante acciones visibles, sin gestos ocultos.
5. **Grabaciones útiles**: transcripción local, resumen y propuestas de acción
   que siempre se pueden revisar antes de aceptar.
6. **Bandeja de revisión**: confirmar, editar o descartar sugerencias con su fuente
   visible.
7. **Agenda accionable**: vencidas, próximas y sin fecha, siempre accesible.
8. **Búsqueda, exportación e importación**: recuperación y control de los datos.

### Opcional avanzado

- escucha continua de frases activadoras;
- contexto ambiental;
- control técnico de ASR, pre/post-roll y diagnóstico;
- personalización exhaustiva de frases y prompts;
- escucha continua en Wear OS.

### Fuera del MVP comercial

- promesa de grabar o recordar todo;
- clasificación de TV, música o conversación como valor principal;
- creación automática de tareas desde conversaciones ambientales;
- análisis continuo de pantalla o notificaciones;
- modelos cloud, API keys o sincronización de contenido personal con servidores;
- inferencias de emoción, productividad o identidad de interlocutores.

## Experiencia principal

### Home

Home mantiene el calendario actual. Cada día combina, en orden temporal:

- eventos previstos del calendario;
- estancias detectadas;
- notas y tareas;
- grabaciones y acciones confirmadas.

El control principal debe ser `Capturar`, con tres acciones etiquetadas:

1. `Decir una tarea`;
2. `Grabar reunión`;
3. `Escribir`.

Activar o desactivar la escucha continua pertenece a Ajustes y al indicador de
estado, no a la acción principal de captura. La grabadora no debe depender de una
pulsación larga.

### Revisión

Una sugerencia no confirmada nunca se mezcla visualmente con una tarea fiable.
Cada sugerencia muestra:

- contenido mínimo y editable;
- procedencia: dictado, grabación o reloj;
- fecha interpretada, si existe;
- acciones `Confirmar`, `Editar` y `Descartar`.

Confirmar es la única operación que la cataloga como fiable. La confianza del
modelo no sustituye esa decisión; se conserva por separado para diagnóstico y
calibración local.

### Calendario y ubicación

El calendario representa **lo previsto** y la ubicación representa **dónde
transcurrió el día**. Trama no debe afirmar que un evento ocurrió solo porque
estuviese en el calendario, ni que una estancia explica una actividad.

La primera unión útil será visual y factual:

```text
09:00  Reunión de proyecto      Calendario
09:48  Oficina · 3 h 12 min     Ubicación
13:15  Llamar a Marta           Tarea confirmada
```

No se generarán conclusiones semánticas automáticas entre esas fuentes en el MVP.

## Presupuesto de recursos

### Estado inactivo

Con escucha continua desactivada:

- no debe existir servicio de micrófono en primer plano;
- no debe programarse el watchdog de audio;
- no debe haber ventanas Vosk/Whisper ni fallbacks ambientales;
- Gemma y Whisper solo se cargan por una captura o grabación explícita y se
  liberan después según la política de memoria existente;
- calendario y ubicación continúan con ciclos independientes.

### Agenda

La sincronización usa `CalendarContract`, sin red propia de Trama. Se conserva la
reconciliación actual al abrir la app y al modificar las fuentes. Antes de añadir
trabajo periódico se medirá si existe un problema real de frescura. Si hace falta,
se usará trabajo diferible y restringido, nunca un proceso residente.

### Ubicación

Se conserva la traza de estancias. La implementación actual solicita GPS y red
cada 3 minutos, por lo que la optimización prioritaria será:

1. aumentar el intervalo normal y exigir desplazamiento mínimo;
2. preferir proveedor de bajo consumo cuando la precisión sea suficiente;
3. usar GPS solo para confirmar una estancia ambigua o un cambio relevante;
4. no conservar una ruta de coordenadas si una estancia agregada cumple el caso
   de uso;
5. mantener umbrales configurables en el nivel avanzado.

No se reducirá silenciosamente la calidad: la versión optimizada debe compararse
con la detección actual usando jornadas reales y lugares conocidos.

## Criterios de aceptación

Son puertas de publicación, no resultados ya demostrados.

### Funcionales

- Con escucha desactivada, reiniciar, abrir Home y terminar una grabación no
  reactivan el micrófono continuo.
- Los calendarios seleccionados siguen sincronizando y mostrándose en Home.
- La ubicación sigue creando y cerrando estancias.
- Entrada manual, dictado directo, grabación, Agenda, búsqueda y backup siguen
  disponibles.
- Una sugerencia solo pasa a fiable mediante confirmación explícita.

### Precisión y utilidad

- Cero tareas ambientales confirmadas automáticamente.
- Toda acción muestra una fuente rastreable.
- Se evalúa por separado precisión de tareas y cobertura; no se celebra una tasa
  baja de falsos positivos si no se recupera nada útil.
- Una prueba moderada debe demostrar que una persona puede añadir una tarea,
  confirmar una sugerencia y localizar un evento o lugar sin explicación externa.
- Las grabaciones deben producir acciones útiles solo cuando existe evidencia en
  la transcripción; en caso ambiguo se ofrece revisión o no se propone nada.

### Consumo

- Con escucha desactivada, el diagnóstico debe registrar cero decodificaciones
  automáticas de audio durante una jornada sin grabaciones.
- No debe aparecer una notificación persistente de micrófono.
- El presupuesto provisional de calendario más ubicación es un máximo de 5 % de
  batería adicional durante una jornada de 10 horas. Debe medirse en el mismo
  dispositivo y recorrido con ubicación activada/desactivada; si no se cumple, la
  traza de ubicación no está lista para el MVP.
- No se aceptan episodios térmicos atribuibles al funcionamiento de fondo del MVP.

## Plan de ejecución

### Fase 0 — control de consumo

Estado: implementada en código, pendiente de validación física.

- Mostrar `Escucha continua` en la raíz de Ajustes.
- Al desactivarla, detener teléfono, watchdog, notificación de recuperación y
  escucha del reloj.
- Conservar calendario, ubicación y grabaciones manuales.
- Deshabilitar visualmente el contexto ambiental cuando no hay escucha.
- Cubrir el contrato independiente con tests.

### Fase 1 — día fiable

- Hacer visible el estado de sincronización del calendario: última actualización,
  fuentes y error accionable.
- Añadir actualización manual sin duplicados y testear altas, cambios y borrados.
- Mostrar claramente eventos de calendario y estancias en el timeline.
- Garantizar que Agenda sea accesible aunque tenga cero elementos.

### Fase 2 — captura deliberada

- Sustituir el FAB de encendido/apagado por `Capturar`.
- Mostrar `Decir una tarea`, `Grabar reunión` y `Escribir` sin pulsación larga.
- Pedir micrófono solo al usar dictado o grabación.
- Mantener Wear como extensión opcional para captura directa y grabación.

### Fase 3 — confianza y precisión

- Unificar sugerencias en una bandeja con fuente y evidencia.
- Separar de forma permanente confianza automática y confirmación humana.
- Priorizar reglas deterministas para fechas, duplicados y acciones simples.
- Ejecutar el modelo local solo cuando mejora una captura explícita; si no está
  instalado, mantener edición y confirmación manual funcionales.
- Construir un corpus anonimizado a partir de confirmaciones y descartes locales
  exportados voluntariamente para evaluar precisión y cobertura.

### Fase 4 — batería y recursos

- Medir una línea base con Battery Historian/`batterystats`, CPU, temperatura y
  número de despertares.
- Aplicar ubicación adaptativa y repetir el mismo recorrido de prueba.
- Cargar ASR/LLM bajo demanda y evitar reprocesados sin cambios.
- Reducir el diagnóstico normal a métricas agregadas y evitar conservar durante
  72 horas fragmentos conversacionales completos salvo modo diagnóstico temporal
  y consentimiento claro.

### Fase 5 — simplificación y onboarding

- Onboarding breve: calendario, ubicación opcional, captura y privacidad.
- Separar permisos por función; nunca pedir audio, ubicación y calendario juntos.
- Dejar en Ajustes básicos solo escucha continua, calendarios, ubicación,
  privacidad/copia y apariencia.
- Mover frases, motores, prompts, radios y diagnóstico al nivel avanzado.
- Retirar pantallas o componentes sin entrada real después de verificar sus usos.

### Fase 6 — validación y decisión

- Pruebas Compose de los recorridos principales.
- Prueba física en móvil pequeño y grande, batería real y reinicio.
- Cinco jornadas de uso con escucha desactivada, calendario y ubicación activados.
- Comparar utilidad percibida, tareas confirmadas, errores de calendario,
  estancias perdidas y consumo.
- Si la escucha continua no demuestra valor incremental medible, eliminarla del
  producto distribuido y conservarla solo en una rama experimental.

## Orden de prioridad

1. Independencia y consumo de la escucha continua.
2. Fiabilidad del calendario y la traza de ubicación.
3. Captura deliberada rápida.
4. Confirmación y precisión.
5. Simplificación visual.
6. Funciones experimentales.

No se añadirá otra fuente automática hasta que el núcleo demuestre utilidad y
coste aceptable en uso real.
