# Aplicación de la auditoría Trama

**Fecha:** 12 de septiembre de 2026. **Base:** árbol de trabajo existente, conservando sus cambios previos.

## Resultado aplicado

- Las capturas manuales, compartidas, del teléfono y del reloj se guardan primero como `MEMORY/SAVED`. El clasificador crea una fila `ACTION` derivada y ya no puede ocultar el original.
- Cada captura y cada acción derivada tiene identidad de origen y revisión. Los cambios automáticos solo afectan acciones sin decisión humana; confirmar, completar, editar o descartar incrementa la revisión y bloquea resultados tardíos.
- Room sube a esquema 18 mediante una migración aditiva. La copia JSON y la sincronización del reloj conservan los nuevos campos. Las notas manuales históricas descartadas se recuperan como recuerdos.
- Las acciones futuras `SEND` y `TALK_TO` se programan como recordatorios. Día, Acciones, Detalle y Reunión usan el mismo diálogo de Calendar. Programar conserva la acción pendiente y guarda el `eventId`; abrir un editor externo se comunica como resultado no verificado.
- La deduplicación de Calendar exige que descripción, final, ubicación y aviso coincidan. Un evento ajeno con el mismo título e inicio ya no se considera automáticamente el mismo recordatorio.
- El aprendizaje está apagado por defecto, y al apagarlo el procesador deja de leer el historial. Borrar el historial continúa disponible con el interruptor apagado.
- Las reuniones se borran por un solo caso de uso: cancela ambos workers, mueve temporalmente el PCM, confirma en una transacción Room también las memorias y eventos seleccionados y después elimina el archivo; si falla la transacción restaura el audio.
- `TRANSCRIPT_ONLY` distingue una transcripción conservada sin análisis y ofrece reprocesarla. Las fechas relativas usan la fecha de grabación. La recuperación omite la grabación que sigue activa.
- Las reuniones largas guardan un checkpoint por fragmento, continúan como trabajo prolongado y reanudan tras una interrupción de Android. El análisis local divide transcripciones extensas en bloques acotados.
- La identificación de lugares por internet es opt-in y explica que envía la ubicación. Después de una consulta lenta se vuelve a leer Room para no pisar un nombre u opinión editados.
- La navegación principal expone Día, Acciones y Recuerdos. Añadir es la acción primaria; escucha, reloj y reunión pasan al menú. Recuerdos transfiere la consulta al asistente y lo presenta como síntesis de resultados.
- Los diagnósticos salen del menú normal y se abren con siete toques en «Trama». Los logs no guardan texto ni metadatos sensibles salvo durante una sesión de diagnóstico explícita.
- Los textos de copia, audio, reconocimiento de voz y modelo local describen su alcance real. La copia declara expresamente que no incluye PCM ni configuración.
- Editar o eliminar una entrada invalida la memoria diaria derivada. Se retiraron pantallas y un constructor de contexto sin consumidores.

## Cobertura por ficha

| Ficha | Estado | Alcance actual |
|---|---|---|
| C01–C04 | Aplicado | Original protegido, decisiones monotónicas, origen estable y Calendar común |
| C05–C08 | Aplicado en el flujo actual | Reconciliación Calendar más estricta, trigger persistido, aprendizaje efectivo y borrado de reuniones único |
| C09–C15 | Aplicado en su núcleo | Estados de reunión, privacidad de lugares, IA principal, búsqueda primero y Ajustes simplificados |
| C16 | Cubierto por los adaptadores existentes | PCM durable converge en el transcriptor común; falta medir formatos reales externos |
| C17 | Parcial | UUID/origen y deduplicación del reloj aplicados; quedan pruebas de replay con hardware y recibos para texto |
| C18 | Parcial y condicionado | Fecha de captura, estados, checkpoint, recuperación y segmentación de análisis corregidos; diarización y cola global requieren corpus y pruebas de consumo antes de activarse |
| C19 | Parcial | Se retiraron cinco archivos sin consumidores; los composables privados restantes se mantienen hasta verificar referencias visuales/instrumentadas |
| C20 | Pospuesto por diseño | Recomendaciones tipadas y nuevos experimentos solo deben añadirse después de medir recuperación y uso |

## Verificación

Comando:

```text
./gradlew --offline :shared:testDebugUnitTest --rerun :app:testDebugUnitTest --rerun :wear:testDebugUnitTest --rerun
```

Resultado: **635 pruebas, 0 fallos** (`app` 390, `shared` 183, `wear` 62).

También pasan `:app:lintDebug`, `:shared:lintDebug`, `:wear:lintDebug` con **0 errores**, y compila el conjunto de tests instrumentados de migración e integridad de decisiones. No se instaló ni desplegó la aplicación. Siguen pendientes las diez pruebas físicas J, la ejecución de migraciones en emulador/dispositivo y las mediciones de batería, ASR, Calendar y reloj.
