# Fase 5 — Reuniones y memoria recuperable

## Implementado

- El audio se conserva durante captura, transcripción y recuperación; la reunión sigue siendo la fuente de las acciones extraídas.
- Las acciones de una reunión nacen como sugerencias y una aceptación repetida no crea otra entrada.
- El detalle agrupa resumen y puntos clave como «Notas de la reunión». Se pueden editar título, resumen y puntos clave sin modificar la transcripción original.
- Los puntos clave usan un único formato JSON. Las reuniones creadas por versiones que los guardaban separados por líneas siguen siendo legibles y se normalizan al editarlas.
- El detalle de una acción extraída permite volver a su reunión de origen.
- Los lugares conservan ciudad/localidad y dirección durante su resolución, copia y restauración. Buscar admite varias palabras y categorías habituales, de modo que una consulta como «restaurantes en Portonovo» exige coincidencia de tipo y localidad en el mismo lugar.
- La ficha de reunión separa Resumen, Acciones y Transcripción; la transcripción permite buscar y contar coincidencias. El borrado individual confirma que elimina reunión, sugerencias y audio.
- Día, Acciones y Recuerdos tienen navegación estable. Acciones separa «Por revisar» de las tareas confirmadas y Recuerdos filtra Notas, Lugares y Reuniones desde la primera letra.
- La ficha de lugar abre en modo lectura y concentra los cambios en un modo Editar con una única acción Guardar. Las opiniones breves se conservan tal cual, sin forzar un resumen local.
- El estado de escucha, trigger, reunión, procesado, reloj y transferencia se resuelve en una única máquina observable que comparten Home y el widget.
- Los controles flotantes de Escuchar, Reunión y Reloj permanecen disponibles en los tres destinos principales: Hoy, Acciones y Recuerdos.
- Las reuniones largas se dividen en bloques que terminan preferentemente en límites de frase. Cada bloque tiene un segundo intento simplificado y el fallo de uno no descarta los resultados de los demás.
- Un resultado incompleto se guarda como `LOCAL_PARTIAL`, se identifica como análisis parcial y permite reintentar sin eliminar acciones ya revisadas.
- Las acciones visibles pasan por el mismo normalizador que las capturas breves: la conversación anterior y posterior sirve como contexto, pero no se copia en el título de la tarea.
- Las notas y tareas existentes admiten corrección escrita o explicación por voz. El modelo local actualiza la misma tarea únicamente cuando obtiene un resultado accionable; un fallo mantiene intacto el registro anterior.

## Diarización

El proyecto incluye las clases nativas de `sherpa-onnx` y un modelo de *speaker embedding* utilizado para «Solo mi voz». No incluye el modelo de segmentación Pyannote requerido para detectar turnos de hablante. Por tanto, la diarización aún no está implementada y la interfaz no afirma lo contrario.

Para completarla hacen falta: incorporar y licenciar un modelo compatible, ejecutar segmentación sobre el PCM conservado, transcribir por intervalos, guardar segmentos con tiempos y hablantes anónimos, permitir renombrarlos manualmente y medir errores con reuniones reales de 2–4 personas. La identificación de la voz entrenada no sustituye esta segmentación.

No existe actualmente una tabla de turnos ni etiquetas `Hablante 1/2` en la interfaz. El procesamiento por bloques descrito arriba protege contexto y recuperación, pero no atribuye frases o acciones a una persona.

## Pendiente

- **Prueba física de reunión de 60 minutos — aplazada por el usuario el 17-09-2026.**
  Grabar con pantalla bloqueada e introducir marcas en los minutos 1, 20, 40 y 58.
  Verificar duración, conservación del audio, reanudación por bloques, ausencia de texto
  repetido, presencia de las marcas inicial y final, extracción única de acciones,
  aislamiento de sugerencias hasta su aprobación y persistencia al reabrir. Registrar
  también el tiempo desde «Transcribiendo» hasta «Lista».
- Relacionar cada acción con el intervalo exacto de audio que la sustenta.
- Añadir segmentos diarizados y asignación manual de nombres cuando exista un modelo validado.
- Incorporar y validar un modelo de segmentación de hablantes con licencia compatible antes de mostrar diarización en producto.
- Medir solapamientos, atribuciones erróneas, tiempo de proceso y consumo en el dispositivo objetivo.
- Validar edición de notas, recuperación del audio y navegación de origen en uso real; el usuario realizará el despliegue cuando lo considere.
