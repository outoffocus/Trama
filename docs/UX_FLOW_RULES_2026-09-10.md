# Reglas de flujo UX

Estas reglas convierten cada captura en un recorrido con un resultado reconocible. Deben aplicarse a Inicio, Agenda, Detalle y acciones extraídas de reuniones.

## Ciclo de una entrada

1. **Capturada:** se guarda antes de procesarse, para que un fallo no pierda la idea.
2. **Por confirmar:** solo aparece cuando la interpretación no es suficientemente segura. Sus únicas salidas son `Añadir a pendientes` o `Descartar`.
3. **Pendiente:** ya puede ejecutarse fuera de Trama, marcarse como hecha, editarse o eliminarse.
4. **Completada:** deja de competir por atención y puede reabrirse.
5. **Descartada:** sale de las vistas habituales y permite recuperarla antes de abandonar su detalle.

Una entrada por confirmar nunca debe crear efectos externos. Primero se confirma; después aparecen Calendar, Keep, llamada o mensaje.

## Resultado real de cada acción

| Acción visible | Resultado que la app puede garantizar | Estado en Trama |
|---|---|---|
| Añadir a Calendar / Crear recordatorio | Inserta el evento y confirma el resultado. Si no hay permiso, abre el editor del calendario con los datos preparados. | Se completa automáticamente solo tras una inserción confirmada. |
| Abrir en Keep | Abre un borrador en Keep o el selector de notas. Trama no puede comprobar que el usuario lo guardó. | Sigue pendiente hasta que el usuario la marque como hecha. |
| Buscar contacto para llamar | Abre contactos para que el usuario elija el número. | Sigue pendiente. |
| Preparar mensaje | Abre el selector de apps con el texto preparado. | Sigue pendiente. |
| Marcar como hecha | Cierra el trabajo dentro de Trama. | Completada y reversible mediante `Reabrir`. |

Los botones deben describir el resultado garantizado. No se debe usar `Añadido`, `Enviado` o `Llamado` cuando otra app todavía requiere una confirmación.

## Detalle

La pantalla responde, en este orden, a cuatro preguntas:

1. **¿Qué es?** Tipo, estado, fecha relevante y texto principal.
2. **¿Está bien escrito?** El texto se edita al tocarlo; guardar conserva primero la corrección y después vuelve a procesar la clasificación.
3. **¿Qué puedo hacer ahora?** Las acciones dependen exclusivamente del estado actual.
4. **¿De dónde viene?** Fecha, dispositivo y, cuando existe, enlace a la reunión de origen.

Compartir y eliminar viven en el menú secundario. Los datos de Whisper, confianza, palabra clave y backend pertenecen a Diagnóstico en Ajustes, no al flujo diario.

## Reuniones

La grabación debe terminar siempre en un objeto consultable aunque fallen transcripción o análisis. Su recorrido es:

`Grabando → Grabación guardada → Transcripción → Notas y acciones sugeridas`

Las acciones extraídas nacen `Por confirmar`. Al conservarlas pasan al mismo ciclo que cualquier captura. El detalle de cada acción enlaza de vuelta a la reunión para mantener contexto y trazabilidad.

## Visitas a lugares

Una localización candidata no se muestra todavía. Cuando permanece dentro del radio durante el umbral configurado, se convierte en una visita confirmada y aparece inmediatamente como `En curso`. La salida del radio solo fija la hora de fin y la duración definitiva.

El evento activo y el lugar se crean una sola vez. Si el servicio se reinicia, reutiliza la visita abierta; si la ubicación oscila en el borde, la histéresis evita cerrar y duplicar la estancia.

## Criterio para nuevas funciones

Antes de añadir un control hay que responder:

- qué estado inicial requiere;
- qué cambio observable produce;
- cómo informa de éxito, cancelación o error;
- si el resultado depende de otra app;
- cómo se corrige o revierte;
- en qué vista reaparece después.

Si alguna respuesta falta, el control todavía no debe exponerse en la interfaz principal.
