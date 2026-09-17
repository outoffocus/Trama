# Fase 0 — correcciones y cierre de este lote

Estado: cambios implementados en el repositorio y compilados; no instalados en los dispositivos del usuario. Esta entrega no completa las fases 1–6 ni certifica precisión de voz, diarización o autonomía.

## Cambios

| Hallazgo | Corrección / alcance |
|---|---|
| F0-01 | Home deja de cambiar createdAt de pendientes antiguos del reloj. No se inventan fechas originales de registros ya alterados anteriormente. |
| F0-02 | BackupEntry y ambas conversiones conservan userConfirmedAt/verificationSource. Copias antiguas siguen siendo legibles y no adquieren confirmaciones inexistentes. Formato aditivo, sin migración Room. |
| F0-04 | Evento y recordatorio se envían juntos con applyBatch y back-reference; no se oculta por separado un fallo del aviso. null significa sin aviso y 0 significa al inicio. Actualizados los consumidores existentes. Queda pendiente confirmar comportamiento del proveedor en dispositivo. |
| Fecha de recordatorio | ActionExecutor deja de convertir un recordatorio con fecha en una alarma horaria. Usa Calendar con aviso o abre una revisión de fecha/aviso. |
| F0-08/F0-09 | Acciones extraídas nacen SUGGESTED, sin confirmación humana. Guardado dentro de transacción y COMPLETED al final. No se borran acciones revisadas al reprocesar; las existentes con igual texto en esa reunión no se duplican. |
| F0-10 | Fallo al finalizar PCM recuperado solicita reintento limitado y después marca FAILED. |
| F0-11 | Resumen semanal comprueba notificaciones y canal antes de declarar publicación; no reintenta indefinidamente un canal desactivado. |
| F0-12 | Lectura completa de Calendar diferenciada de error/cursor nulo. No se aplica conciliación destructiva tras error. Conciliación local dentro de transacción. |
| F0-13 | Reuniones del reloj conservan PCM tras entrega al Data Layer. Móvil emite recibo tras persistir archivo/registro y comprobar tamaño/huella. Reloj borra solo archivo finalizado cuyo tamaño/SHA-256 coincide. Reenvío incompleto reutiliza registro; fallo del recibo no bloquea transcripción ya válida. |
| UX observada | Títulos históricos ya no dicen Hoy/Completado hoy. |
| Nuevo fallo encontrado | El filtro admite sustantivos como «taller» detrás de un determinante aunque terminen como un infinitivo. Regresión con «Llamar al taller» y rechazo de «Tengo que hacer». |

Compatibilidad de recibos: necesita las nuevas versiones de móvil y reloj para completar el borrado confirmado. Con móvil antiguo, el reloj conserva reuniones hasta poder confirmar; puede consumir espacio adicional. El protocolo de recibos cubre reuniones durables, no las capturas efímeras CONTEXTUAL_TRIGGER. La transferencia física y recuperación tras desconexión no se consideran probadas por los tests de huella.

## Verificación

- 595 tests unitarios: app 356, shared 173, wear 66; 0 fallos, errores u omitidos.
- Compilación de APK debug de móvil y reloj correcta.
- Compilación del test Android de migración correcta; ejecución instrumentada no realizada por dependencias UTP ausentes en modo offline y preferencia del usuario de continuar en código.
- `git diff --check` correcto.
- Pruebas añadidas: conversiones reales de copia, copia antigua sin confirmación, cursor nulo/vacío/interrumpido, propuestas de reunión, fallo al insertar acción, preservación de acción revisada, sustantivo terminado como infinitivo, recibo de audio exacto.
- Los tests de guardado con repositorio sin Room comprueban el orden y los estados; no sustituyen la prueba instrumentada de rollback de una transacción real.
- Binarios: `app/build/outputs/apk/debug/app-debug.apk` y `wear/build/outputs/apk/debug/wear-debug.apk`. No se instalaron ni se publicaron.

Comando final:

```sh
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest :wear:testDebugUnitTest :app:assembleDebug :wear:assembleDebug :shared:compileDebugAndroidTestKotlin --offline
```

## Pendientes explícitos del plan

- F0-03: la copia JSON de la app sigue sin empaquetar audio. La copia privada realizada por ADB sí incluye el audio y pasó integridad SQLite; no confundir ambos mecanismos. Diseñar paquete exportable/restaurable con archivos en la fase de datos.
- F0-05/F0-06: elegir y ensayar diarización local y medir escucha prolongada. No hay resultados nuevos de calidad acústica o batería.
- F0-07: revisión de retención/exportación de diagnósticos personales antes del piloto público.
- F0-14: identidad de ocurrencias recurrentes independiente de su hora y conservación de estados al reprogramar, en la fase de calendario/anticipación.
- Restauración real de copias, rollback Room, alarmas y recibos móvil/reloj pendientes de ejecución física.
- Rediseño, captura unificada y búsqueda de lugares/recomendaciones pertenecen a las fases siguientes; no se implementaron dentro de esta corrección técnica.

La revisión estática y este lote de correcciones quedan cerrados. La fase 0 global conserva pendientes de validación física; no debe marcarse completa solo porque el código compile.
