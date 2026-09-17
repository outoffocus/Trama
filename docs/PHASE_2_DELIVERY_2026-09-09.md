# Fase 2 — integridad y captura escrita

## Implementación

- `CalendarViewModel` posee consultas del Inicio y borrador manual. Los flujos se conservan fuera de la recomposición y se observan con el ciclo de vida de la pantalla.
- `CalendarEntryActions` concentra escrituras del Inicio; `CalendarActionsViewModel` mantiene las operaciones enviadas mientras la pantalla deja de esperar. El borrado mixto de entradas, reuniones y eventos es transaccional. La señal de borrado se registra tras confirmar la transacción.
- Guardar manualmente no necesita ASR ni LLM. Solo admite una escritura simultánea, conserva el borrador si falla, y muestra «Guardado / Ver» después de recibir el ID de Room.
- El borrador y su fecha usan SavedStateHandle: se restauran en recreaciones compatibles con saved state de Android. Un reintento de un borrador restaurado reutiliza la captura ya comprometida con igual fecha/texto y procedencia manual.
- `EntryEditorViewModel` bloquea guardados vacíos o simultáneos y conserva la edición ante fallo. El modo edición se cierra tras confirmar la escritura.
- Editar actualiza cleanText/correctedText y conserva text como captura original, fecha, procedencia, vencimiento y confirmaciones. Compartir usa la versión visible.
- Inicio consulta únicamente las grabaciones del día seleccionado, con límites inclusivos obtenidos de DayRange.

## Compatibilidad

No se altera el esquema 16 ni se crean tablas o un almacén paralelo. Se reutilizan los campos de texto original/editado y procedencia. Las copias existentes mantienen su formato; la restauración de una captura editada está cubierta por una prueba de codificación/decodificación real del BackupManager. El camino de navegación y el repositorio existente permanecen; no hay doble escritura ni migración destructiva.

SavedStateHandle cubre la recreación gestionada por Android; no se promete recuperar texto todavía sin guardar tras borrar datos, forzar detención o fallos antes de que Android conserve el estado. La captura confirmada sí queda en Room.

## Verificación

- Tests unitarios de móvil, compartido y reloj: correctos.
- Nuevas pruebas: doble pulsación; error y reintento; borrador restaurado tras commit; edición vacía/error/reintento; backup con texto original y editado.
- En Samsung S25+ (Android 16): tres pruebas instrumentadas correctas. Migraciones desde versiones 1–15 a 16; guardar/cerrar/reabrir/editar/buscar conservando metadatos; consulta de grabaciones en los límites del día.
- Las pruebas físicas usan bases temporales aisladas; no tocan la base personal.
- Corregida una carencia del proyecto de pruebas: faltaba la dependencia AndroidJUnitRunner en shared.

## Validación personal

Añadir una nota, abrirla desde «Ver», editarla, salir y volver a buscar el texto editado. Probar también recreación de pantalla con un borrador abierto y captura en un día histórico. La claridad y comodidad de la interfaz quedan pendientes de validación del usuario.
