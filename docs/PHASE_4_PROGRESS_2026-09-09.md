# Fase 4 — Captura de voz y acciones

## Implementado

- El botón «Añadir» comparte un único borrador para texto y dictado local. La transcripción se muestra antes de guardarse y puede corregirse.
- La entrada original se persiste antes de clasificarla. La clasificación posterior actualiza esa misma entrada; un fallo del modelo no pierde la captura.
- Editar una transcripción vuelve a calcular tipo, fecha y prioridad sobre el mismo identificador, sin crear otra captura.
- Todo el dictado offline —captura, opinión y entrenamiento de voz— comparte coordinación de micrófono: pausa la escucha del teléfono y la recupera al terminar o fallar. La reanudación no reactiva una escucha que el usuario haya desactivado durante la captura y respeta cuando el reloj mantiene la escucha.
- El gate ligero se limita a decidir si activa Whisper. La pasada principal de Whisper conserva el contexto amplio; la ventana corta es solo un segundo intento cuando la primera pierde la intención.
- Una tarea local sin fecha deja de ofrecer una acción redundante que abría Calendar. Las compras sin fecha ofrecen un envío asistido a Google Keep; las tareas con fecha abren la previsualización editable de Calendar.
- La escritura confirmada en Calendar se ejecuta fuera del hilo de interfaz, informa del resultado y reutiliza un evento idéntico ya existente para evitar duplicados por una confirmación repetida.
- Las interpretaciones inciertas continúan entrando como sugerencias y requieren confirmación antes de cualquier destino externo.

## Límites todavía abiertos

- Google Keep no ofrece en esta arquitectura una escritura fiable sobre una lista existente. El flujo abre un borrador en Keep para que el usuario lo revise y guarde; no afirma haber añadido automáticamente un elemento.
- La escucha de jornada completa sigue necesitando medición física de consumo, falsos positivos y capturas perdidas. Hasta superar esa prueba debe tratarse como función experimental.
- El dictado bajo demanda necesita una prueba acústica final en el S25+; la compilación solo valida el circuito y sus estados.
