# Trama: evaluación de utilidad y plan de mejora

Fecha: 6 de septiembre de 2026. Estado: propuesta para discutir, sin cambios de implementación.

> Alcance consolidado posterior: [plan de producto e implementación](CONSOLIDATED_PRODUCT_AND_IMPLEMENTATION_PLAN_2026-09-06.md), que incorpora todas las necesidades ampliadas y reordena las prioridades.

> Revisión posterior: [decisión sobre voz y prioridades de producto](VOICE_CAPTURE_DECISION_2026-09-06.md). Reconsidera la escucha ambiental como núcleo, limita la confirmación a inferencias/ambigüedades y prioriza compra, avisos y recuperación antes de una rutina de reflexión diaria. Las recomendaciones iniciales de este documento deben leerse con esa revisión.

## Dictamen

Trama tiene una base amplia para capturar contexto, pero su experiencia todavía gira demasiado alrededor de tareas y del funcionamiento del micrófono. Para convertirse en un diario útil debe cerrar un ciclo completo: guardar algo sin esfuerzo, reconocer lo que ocurrió, corregirlo cuando haga falta y recuperarlo después.

La prioridad es una memoria fiable y fácil de alimentar. La escucha por frases configurables se conserva como dirección aprobada; su calidad se evalúa por separado y la experiencia básica debe funcionar también con texto, sin modelo instalado y sin permisos opcionales.

Esta evaluación se basa en lectura del código, navegación declarada, modelos, persistencia, pruebas y documentación. No se ha ejecutado la app ni se han realizado sesiones con usuarios o mediciones físicas. Los comportamientos descritos son hallazgos de implementación; las dificultades de uso y las metas de rendimiento son hipótesis o criterios por validar. No se afirma que la compilación o los tests pasen actualmente.

Se toma como referencia vigente `PRODUCT_SPEC_2026-08-12.md`. Los documentos Lite están archivados y el plan MVP anterior contiene decisiones sustituidas. Esta propuesta no sustituye automáticamente decisiones aprobadas. En particular, distinguir recuerdos y tareas y añadir revisión diaria editable amplía el modelo actual de producto.

## Qué conviene conservar

- El calendario diario como entrada y eje de navegación.
- El procesamiento local del contenido personal y la independencia de calendario y ubicación respecto de la escucha.
- La combinación de entradas, grabaciones, calendarios y estancias.
- La infraestructura existente de Room, workers de recuperación, backups y sincronización con Wear.
- La distinción persistida entre confianza automática y confirmación humana, aunque falta aplicarla de extremo a extremo.
- La recuperación determinista ya presente en Chat y la búsqueda sin LLM.
- Las pruebas unitarias, esquemas versionados y CI con migraciones en emulador. Ampliarlas con recorridos de producto; no reconstruir la base desde cero.

## Hallazgos y consecuencias

| Prioridad | Evidencia en el código | Consecuencia y mejora |
|---|---|---|
| P0 | `CalendarScreen.kt:310` cambia `createdAt` a la hora actual para entradas pendientes antiguas del reloj sin vencimiento. | Abrir Home puede alterar la cronología almacenada. Mostrar pendientes anteriores mediante consultas, conservando la fecha original. Separar fecha de registro, fecha del recuerdo y vencimiento cuando el caso lo exija. |
| P0 | `CaptureSaver.kt` inserta con el estado predeterminado `PENDING`; `ActionItemProcessor.kt` mantiene rutas automáticas aceptadas como pendientes. | La confirmación humana de la especificación no es una regla universal. Persistir acciones extraídas como sugerencias desde el primer momento y permitir confirmación solo por una acción del usuario. |
| P0 | `BackupManager.BackupEntry` y sus conversiones omiten `userConfirmedAt` y `verificationSource`. | Un ciclo de exportación/restauración pierde la evidencia de confirmación. Versionar y completar el formato, conservando compatibilidad con backups antiguos. |
| P1 | El menú dice «Añadir nota», pero abre «Nueva tarea». La entrada insertada usa `PENDING`; `DiaryEntry` no tiene una distinción estructural entre recuerdo y tarea. | Una observación como «Comí con Ana» queda sometida al ciclo de pendientes. Introducir un tipo explícito de entrada: recuerdo/nota frente a tarea; categoría y tipo no son equivalentes. |
| P1 | `handleMicClick` inicia/detiene escucha; la grabación se descubre mediante `onLongClick` en `FloatingMicButton`. | Un micrófono no explica si dictará, escuchará o grabará. Ofrecer acciones visibles y etiquetadas; separar control del modo de escucha de creación de contenido. |
| P1 | Home usa secciones temporales y queries que incluyen conjuntamente `PENDING` y `SUGGESTED`. No aparece la bandeja global «Por revisar» aprobada. | Revisar exige interpretar estados repartidos por días. Bandeja global con tres propuestas antiguas, fuente, fecha interpretada y Confirmar/Editar/Descartar. |
| P1 | `CalendarScreen.kt:1051` pasa `null` a la página del día actual y muestra que aparecerá al cerrar el día; no hay edición de esa página en el flujo inspeccionado. | El beneficio de diario se demora y la persona no puede completar fácilmente su versión del día. Mostrar un borrador factual durante el día y permitir una revisión breve y editable. |
| P1 | `SearchScreen` exige dos caracteres; el DAO busca texto bruto y limpio de entradas. | No coincide con el contrato aprobado de búsqueda desde el primer carácter sobre la descripción. Cumplir primero ese contrato; ampliar a grabaciones y lugares solo como evolución explícita. |
| P1 | `Type.kt` conserva fuentes Google DM Sans/DM Mono y etiquetas de 10–11 sp. | La tipografía del sistema aprobada no está aplicada. Mejorar lectura y comprobar escalado, contraste, TalkBack y áreas táctiles. |
| P1 | `MainActivity.requestPermissions` agrupa micrófono, ubicación y calendario cuando se activa su ruta de petición. | Solicitar los permisos según la función que la persona va a usar. No describirlo como una petición universal al primer arranque: está condicionado por el estado de escucha. |
| P2 | `DiaryContextBuilder` construye historia extensa y `DiaryAssistant` usa una vista compacta para el modelo local. | La recuperación histórica necesita evaluación con recuerdos antiguos y ausentes. Mejorar selección de evidencia antes de aumentar contexto o cambiar el modelo. |

## Experiencia propuesta

La frase de trabajo sería: **«Guarda lo que importa de tu día y vuelve a encontrarlo cuando lo necesites».** Complementa la captura por voz y expresa el valor posterior.

Home conserva fecha, navegación temporal y dirección visual Editorial serena. Su orden propuesto:

1. Fecha y estado breve, con texto comprensible si la captura requiere atención.
2. Acción visible «Añadir»: escribir o dictar una nota, crear una tarea o grabar. Abrir la modalidad habitual en un toque cuando ya se conoce la preferencia.
3. «Por revisar», compacto y global; desaparece si está vacío.
4. Timeline del día con procedencia visible: anotado por ti, previsto en calendario, estancia detectada o propuesta pendiente de confirmar.
5. «Mi día», con borrador factual y una pregunta opcional: «¿Qué merece la pena recordar?». No exige terminar todas las tareas para cerrar el día.

Ejemplo: «He comido con Ana y me ha recomendado Casa Sol» se guarda como recuerdo. «Llamar mañana para reservar» puede originar una propuesta de tarea aparte. El recuerdo permanece aunque se descarte la tarea.

Un evento importado significa que estaba previsto. Una estancia significa que se detectó presencia. Ninguna de esas fuentes demuestra por sí sola una reunión realizada, una compra o una emoción.

### Captura y primer uso

- Poder guardar una primera nota sin descargar Gemma ni configurar ubicación, calendario o reloj.
- Guardar primero el contenido deliberado; añadir procesamiento después con estado visible y recuperable.
- Para órdenes por voz, respetar el contrato de no conservar audio. No introducir retención silenciosa como mecanismo de recuperación.
- Para reuniones, distinguir grabando, audio guardado, transcribiendo, listo y error recuperable; mantener controles visibles al navegar.
- Fecha contextual explícita al añadir a otro día. No inventar una hora factual cuando solo se conoce la fecha.
- Borrador protegido al navegar y recuperación ante cierre del proceso para capturas deliberadas compatibles con la política de audio.
- Widget o acceso rápido después de estabilizar el flujo interno de captura.

### Revisión y retorno

- Revisar el día en 30–60 segundos: completar una frase, corregir una estancia o aceptar una propuesta. Todo opcional.
- Recordatorio elegido por el usuario, silenciable y sin rachas que castiguen días vacíos.
- Resumen generado y texto personal separados: regenerar el resumen nunca sobrescribe lo escrito por la persona.
- Vista semanal breve apoyada en recuerdos y decisiones, accesible una vez que el flujo diario demuestre uso.
- Recuperación con enlaces a fecha, entrada o grabación; «no encuentro evidencia» cuando corresponda.
- Aplazar correlaciones de hábitos, ánimo o productividad hasta tener datos suficientes y una demanda observada.

## Plan de ejecución propuesto

Los tiempos son una orientación de planificación, no una estimación cerrada. Ventana inicial: unas 6–8 semanas de calendario con capacidad Android y apoyo de diseño/QA; ajustar después del primer bloque. Las fases avanzan por criterios de salida, no solo por fecha.

| Bloque | Entrega | Criterio de salida |
|---|---|---|
| 0. Línea base, 2–3 días | Recorrido de instalación, captura, revisión, recuperación y restauración; corpus propio de frases; mapa de estados; una especificación vigente. | Backlog reproducible y métricas iniciales en dispositivos de referencia. |
| 1. Integridad, semana 1 | Eliminar mutación temporal en Home; confirmar acciones humanas; conservar confirmaciones en backup; revisar fallos de captura y transferencia. | Pruebas de cronología, estados y exportar/importar sin pérdida de información; ninguna acción extraída llega a fiable sin confirmación. |
| 2. Captura y claridad, semanas 2–3 | Separar nota/tarea con migración compatible; acciones visibles; permisos progresivos; bandeja global; tipografía y estados cotidianos. | Una persona nueva guarda una nota y revisa una sugerencia sin explicación. Funciona sin Gemma y con permisos opcionales rechazados. |
| 3. Diario que devuelve valor, semana 4 | Borrador del día visible, texto personal editable, revisión breve y búsqueda acorde al contrato. | Cerrar el día en menos de un minuto y recuperar un recuerdo conocido en menos de 30 segundos en la prueba de uso. |
| 4. Uso real, semanas 5–6 | Piloto de 10–15 personas durante 14 días; batería, reloj, reinicios, interrupciones y recuperación histórica. | Decisión documentada sobre qué mantener, corregir o aplazar según utilidad y coste observados. |
| Reserva, hasta semana 8 | Resolver problemas de dispositivos, accesibilidad y migración que aparezcan en el piloto. | Sin defectos abiertos de pérdida de datos o confirmación indebida; experiencia esencial estable. |

Responsabilidades: producto mantiene escenarios y criterios; diseño prueba captura/revisión; Android implementa persistencia y flujos; QA verifica estados reales y dispositivos. Es una distribución propuesta de trabajo, no una afirmación de que hayan participado varios agentes en esta evaluación.

### Primer lote concreto

1. Prueba de regresión: abrir Home con una entrada antigua de Wear no modifica su fecha.
2. Prueba de ciclo completo: confirmar, exportar, restaurar y conservar la verificación humana.
3. Contrato único de sugerencia/confirmación en móvil, reloj, grabaciones y contenido compartido; adaptación de pruebas que hoy premien aceptación automática.
4. Croquis de Home y captura aplicando las decisiones visuales aprobadas; validar la nueva distinción nota/tarea.
5. Implementar el primer recorrido completo: escribir una nota, verla en su día y volver a encontrarla.

## Cómo medir si sirve

Metas iniciales propuestas, no resultados actuales ni referencias universales de mercado:

| Dimensión | Medida y objetivo inicial |
|---|---|
| Primer valor | Al menos 4 de 5 personas en prueba moderada guardan y encuentran una primera nota en menos de 2 minutos, sin ayuda. |
| Fricción | Acceso a captura habitual en un toque desde Home; nota breve guardada en menos de 10 segundos excluyendo el tiempo de redactar. Medir mediana y p95 por dispositivo. |
| Revisión | Mediana inferior a 60 segundos para el cierre diario de los participantes que lo utilizan. Registrar también abandonos y motivos. |
| Recuperación | Al menos 80 % de éxito en preguntas con respuesta conocida, encontrando el registro en menos de 30 segundos. Incluir días antiguos y nombres parecidos. |
| Utilidad | Recuerdos recuperados o revisiones que la persona declara útiles por semana. En el piloto, al menos 7 de 10 participantes con datos completos describen dos episodios concretos de utilidad durante la segunda semana. Resultado orientativo, no validación estadística. |
| Calidad de IA | Precisión y cobertura separadas sobre capturas intencionales anotadas. Objetivos iniciales: ≥90 % de propuestas útiles y ≥85 % de acciones explícitas recuperadas, informando tamaño de muestra y correcciones necesarias. |
| Confianza | Cero tareas inferidas confirmadas automáticamente y cero pérdidas en la matriz de fallos. Conservar fuente y fechas tras backup/restauración. |
| Consumo | Con escucha apagada, cero decodificaciones automáticas de audio; contrastar el objetivo previo de ≤5 puntos porcentuales adicionales en 10 h para calendario+ubicación en recorridos comparables. Medir escucha y Wear por separado antes de fijar su presupuesto. |

Contar aperturas o transcripciones no demuestra utilidad. Registrar métricas agregadas locales; cualquier recopilación de contenido del piloto debe ser voluntaria y explícita.

## Verificación técnica necesaria

- Tests de integración para nota/tarea, fechas de captura y vencimiento, medianoche, cambio horario, edición posterior y fuentes provenientes del reloj.
- Pruebas Compose de captura, confirmación, navegación a otro día, búsqueda y vuelta al día elegido. Los tests actuales de listas de rutas no sustituyen la ejecución de esos recorridos.
- Grabación interrumpida, modelo ausente, poco almacenamiento, cierre del proceso, doble entrega Wear y desconexión durante transferencia. Revisar durabilidad e idempotencia de extremo a extremo.
- Restauración en instalación limpia: relaciones, verificaciones, texto personal y cobertura real de audio. El backup JSON inspeccionado guarda datos de grabaciones, pero no incorpora el audio binario; hacerlo explícito o ampliar el formato si se promete restaurarlo.
- Borrado de entrada/grabación y derivados: resumen diario, markdown, aprendizaje, cachés y copias bajo control de la app. Definir cómo se tratan copias externas ya exportadas.
- Diario privado: bloqueo opcional y decisión de cifrado/retención con migración y recuperación de claves verificables, antes de distribución amplia.
- Texto ampliado, TalkBack, contraste y objetivos táctiles de al menos 48 dp, comprobando que no se solapen. Referencia: [accesibilidad de Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- Prueba de escucha con pantalla bloqueada, llamadas, música, Bluetooth, permisos revocados y reinicio. Las restricciones de servicios en primer plano hacen necesaria una validación física; no basta con un watchdog. Referencia: [cambios en servicios en primer plano de Android](https://developer.android.com/develop/background-work/services/fgs/changes).

## Lo que aplazaría

Nuevas fuentes ambientales, diarización avanzada, más controles de modelos/prompts, gamificación, correlaciones personales y un rediseño amplio de Wear. Conservar las funciones actuales necesarias para los recorridos esenciales y evaluar su coste. No eliminar la escucha por frases aprobada por una conclusión no medida.

La primera entrega debe demostrar algo muy concreto: una persona guarda un recuerdo real, confía en que permanece intacto y lo recupera cuando le resulta útil.
