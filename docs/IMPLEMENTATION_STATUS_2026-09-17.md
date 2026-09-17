# Estado de implementación — 17 de septiembre de 2026

Este documento describe el estado que corresponde al código publicado en `main`. Sustituye las afirmaciones de estado de documentos históricos cuando exista contradicción, sin borrar sus decisiones ni resultados anteriores.

## Producto implementado

- Navegación estable entre **Hoy**, **Acciones** y **Recuerdos**, conservando el desplazamiento por días en Hoy.
- FAB flotantes verticales para **Escuchar**, **Reunión** y **Reloj**, con estado compartido entre pantallas y widget.
- Widget del timeline actual que muestra todas las acciones y los estados Escuchando, trigger reconocido, reunión con temporizador y dispositivo activo.
- Captura continua con estados visibles de escucha y palabra clave, rearme entre órdenes y protección frente a ventanas de audio repetidas o tardías.
- Separación persistente entre memoria original y acción derivada. Una revisión de la misma captura sustituye propuestas automáticas anteriores sin duplicar tareas activas.
- Bandeja de sugerencias diferenciada. Las acciones extraídas de reuniones permanecen dentro de la reunión hasta aprobación.
- Edición manual y por voz de notas y tareas. El audio breve vive en memoria; la transcripción puede revisarse antes de pedir al modelo local que reconstruya la acción.
- Configuración del modelo Gemma local restaurada en Ajustes.
- Extracción común de la cláusula accionable: nombres, lugares y fechas se conservan, mientras la conversación anterior y posterior no se muestra como título de tarea.
- Acciones externas coherentes: Calendar para eventos, recordatorios y llamadas programadas; Gmail para correos; Keep o selector de notas para notas.
- Copia diaria configurable a una hora elegida. Android puede ejecutarla alrededor de esa hora. El paquete excluye audio y modelos descargables.
- Reuniones con audio recuperable, transcripción, resumen, puntos clave, búsqueda, acciones aprobables y eliminación confirmada.
- Procesamiento de reuniones largas por bloques. Los bloques fallidos no borran los válidos; un resultado parcial es visible y reintentable.
- Transferencia móvil/reloj con recibos e identificadores estables para reducir pérdidas y duplicados.

## Contrato del modelo local

El contenido personal se procesa en el dispositivo. El modelo produce una estructura; una capa determinista valida y normaliza el texto antes de mostrarlo. Esta regla se aplica a capturas, correcciones, sugerencias, reuniones, capturas de pantalla y resúmenes.

Una reanalítica explícita de una tarea existente no cambia su estado ni reemplaza sus campos visibles hasta disponer de un resultado accionable. Si el modelo no está instalado, falla o devuelve una interpretación ambigua, la edición permanece abierta.

## Diarización

La diarización real no está implementada.

El APK incluye VAD y un modelo de *speaker embedding* para verificar opcionalmente si una captura breve se parece a la voz entrenada del propietario. Esa comprobación no detecta turnos ni separa participantes en una reunión.

La dependencia Sherpa expone clases nativas de diarización, pero falta incorporar un modelo compatible de segmentación, ejecutar segmentación y agrupación sobre el PCM, transcribir intervalos con tiempos, persistir turnos, permitir renombrar hablantes y atribuir acciones con evidencia. Hasta completar y medir ese circuito, la interfaz no debe mostrar etiquetas de hablante.

## Validación automatizada

Validación ejecutada antes de publicar:

```bash
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Resultado: correcto. Incluye pruebas de persistencia, migración, deduplicación, extracción con contexto conversacional, reuniones, copias, estados de captura, reloj y proyecciones de timeline.

## Validación física pendiente

- Reunión de 60 minutos con pantalla bloqueada y marcas conocidas al principio, centro y final.
- Tiempo total de transcripción/análisis, recuperación tras interrupción y comportamiento con almacenamiento bajo.
- Escucha durante una jornada: batería, temperatura, triggers omitidos, activaciones falsas y rearme consecutivo.
- Transferencia del Galaxy Watch 4 con teléfono desconectado, reintento y recibo durable.
- Ejecución y restauración de una copia diaria con datos sintéticos.
- Escritura, modificación y cancelación reales en el proveedor de Calendar seleccionado.
- Ensayo acústico y de rendimiento previo a elegir un modelo de diarización.

Estas comprobaciones requieren los dispositivos y no quedan sustituidas por compilación o tests unitarios.
