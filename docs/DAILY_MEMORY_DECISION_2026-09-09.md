## Decisión: memoria diaria interna

El antiguo bloque «Página diaria» no ayudaba a decidir ni a actuar: repetía datos ya visibles, ocupaba el final de cada día y mostraba un estado vacío durante la jornada. Se retira de Inicio y de los ajustes.

`DailyPage` se conserva como representación derivada privada. Reduce contexto al responder preguntas históricas, guarda señales estructuradas y puede reconstruirse desde entradas, visitas, calendario y grabaciones. No es la fuente de verdad.

La generación queda desacoplada de las preferencias de interfaz: WorkManager procesa el día anterior a las 03:00, con batería no baja, y no publica notificaciones. Inicio consulta siempre los hechos y acciones originales. El aviso semanal continúa siendo una función independiente y opcional.

Consecuencias:

- desactivar un aviso no impide alimentar la memoria del chat;
- una generación fallida no oculta ni altera el diario original;
- Google Calendar permanece configurable independientemente de cualquier resumen;
- el prompt interno de memoria deja de aparecer entre los controles editables.
