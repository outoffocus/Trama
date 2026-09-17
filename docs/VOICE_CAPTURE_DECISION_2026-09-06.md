# Trama: segunda evaluación de producto y decisión sobre escucha continua

Fecha: 6 de septiembre de 2026. Estado: recomendación, sin cambios de implementación.

> Plan posterior consolidado: [producto e implementación](CONSOLIDATED_PRODUCT_AND_IMPLEMENTATION_PLAN_2026-09-06.md). Incorpora las necesidades ampliadas de lugares, experiencias, recomendaciones, Google y anticipación de guardias/citas. Este informe conserva la evaluación técnica de voz; el nuevo plan determina las prioridades del conjunto.

## Recomendación

**Construir el producto sobre captura deliberada muy rápida y resultados fiables. Mantener una frase de activación distintiva como experimento opcional. Retirar de la promesa principal la creación automática de tareas a partir de conversaciones ambientales durante todo el día.**

Mantener el micrófono activo durante muchas horas es técnicamente posible en Android bajo condiciones. Conseguir que una app ordinaria detecte intenciones espontáneas de forma fiable, con poco consumo y sin crear trabajo de revisión es un problema distinto, mucho más difícil. El historial de pruebas del usuario es evidencia relevante para dejar de invertir indefinidamente en ajustar esa premisa.

Esta revisión combina tres análisis independientes —pipeline y diagnósticos, plataforma, producto— con revisión directa del código y fuentes oficiales. No se ha ejecutado la app, medido batería ni probado nuevos modelos. Se distinguen hallazgos actuales, registros históricos e hipótesis a validar. La capacidad de análisis de esta evaluación no modifica los modelos locales ni el hardware de la app.

## Qué rectifico del primer informe

El [primer informe](USABILITY_AND_UTILITY_REVIEW_2026-09-06.md) detectó problemas reales de integridad y UX, pero su recomendación debe cambiar en cinco puntos:

1. Conservar la escucha por estar aprobada no demuestra su viabilidad. El usuario ahora pide reconsiderar esa decisión a partir de resultados insatisfactorios.
2. Confirmar todas las acciones es excesivo cuando la persona ya ha dado una orden local, sencilla y explícita. La autorización expresada por el usuario y la confianza del modelo son cosas diferentes.
3. Una bandeja global no arregla un sistema que genera ruido: puede convertirlo en una tarea diaria de limpieza.
4. El cierre reflexivo diario es una hipótesis secundaria. Los ejemplos actuales piden no olvidar compras y compromisos; primero hay que resolverlos de principio a fin.
5. El plan de 6–8 semanas era orientativo y demasiado amplio para decidir esta hipótesis. Primero propongo un experimento acotado y tres recorridos completos.

Son cambios propuestos respecto a `PRODUCT_SPEC_2026-08-12.md`, especialmente en escucha como núcleo y confirmación universal. No se han aplicado silenciosamente a la especificación ni a la app.

## 1. Qué significa «escuchar todo el día»

Hay tres problemas distintos:

| Nivel | Qué hay que resolver | Evaluación |
|---|---|---|
| Audio disponible | Micrófono accesible, proceso operativo y estado correcto con pantalla bloqueada y otras apps. | Viable durante periodos largos, sin garantía universal de continuidad. |
| Detectar una expresión | Separar voz/ruido y reconocer una frase en distintas voces, posiciones y entornos. | Una activación distintiva y pequeña es un objetivo acotado; muchas expresiones naturales amplían las confusiones. |
| Decidir qué hacer | Atribuir hablante, intención, vigencia, destinatario, fecha y resultado deseado. | El contenido ambiental a menudo no contiene suficiente información para decidir con seguridad. |

«Faltan plátanos» puede describir una necesidad propia, la de otra casa, una frase citada o una situación ya resuelta después. «Tengo que hacer la compra» es más explícita sobre una obligación, pero tampoco demuestra siempre que el hablante quiera registrarla en Trama. Si el usuario configura previamente una regla para que esas frases se registren, aporta autorización; todavía hay que reconocer el contexto, las negaciones y quién habla.

No todas las menciones son ambiguas. Un sistema puede acertar muchas. El problema de producto es que el usuario necesita saber cuándo puede confiar en él sin revisar constantemente.

Un detector de voz distingue voz de silencio; no distingue al propietario de la televisión. Identificar al propietario tampoco distingue una orden de una cita pronunciada por él. Subir el umbral reduce algunas falsas capturas y aumenta las omisiones. Un LLM más capaz puede interpretar mejor el contexto que recibe, pero no reconstruir una orden ausente o audio que nunca llegó a procesarse.

Los asistentes comerciales también usan varias etapas, identificación del hablante y detección de habla dirigida al dispositivo. Apple documenta esa separación y procesamiento especializado; no es evidencia de que una app independiente pueda reproducir sus resultados con un cambio de prompt. [Arquitectura de activación de Siri](https://machinelearning.apple.com/research/voice-trigger).

### La exposición importa más que una demo

Ejemplo hipotético: tres tareas correctas y dos falsas en un día producen una precisión del 60 %, aunque el sistema haya ignorado correctamente miles de sonidos. No es una medición de Trama.

Además, los errores se acumulan entre disponibilidad, activación, transcripción y extracción. Cuatro etapas con un 90 % de éxito condicional cada una darían aproximadamente un 66 % de éxito completo. Este cálculo ilustra por qué evaluar cada componente por separado no garantiza que el usuario pueda fiarse del resultado final.

## 2. Restricciones reales de Android

- Un servicio en primer plano de tipo `microphone`, con permisos y notificación, puede continuar una captura iniciada correctamente al pasar la app a segundo plano. Su inicio desde background y desde el arranque del dispositivo tiene restricciones y excepciones; un watchdog no concede permisos adicionales. [Tipos de servicio](https://developer.android.com/develop/background-work/services/fgs/service-types), [restricciones de inicio](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- Bloquear la pantalla no prohíbe por sí solo esa captura. Sin embargo, entre dos apps ordinarias que capturan audio solo una recibe señal; la otra puede recibir silencio. Llamadas, cámara, mensajes de voz y cambios de ruta Bluetooth requieren estados e interrupciones bien resueltos. [Compartir entrada de audio](https://developer.android.com/media/platform/sharing-audio-input).
- El usuario puede detener una app con servicio en primer plano desde los controles del sistema. La aplicación debe respetarlo y comunicar el estado, no aparentar disponibilidad permanente. [Detención por el usuario](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).
- Una app ordinaria no debe asumir acceso al DSP de muy bajo consumo utilizado por asistentes del sistema. `AlwaysOnHotwordDetector` pasó a API de sistema desde Android 12, y SoundTrigger tiene dependencias del fabricante. Ser el asistente elegido no equivale a ser una app privilegiada del sistema. [Android 12](https://source.android.com/docs/whatsnew/android-12-release), [SoundTrigger](https://source.android.com/docs/core/audio/sound-trigger).
- `VoiceInteractionService` es una alternativa de integración que implica asumir el rol de asistente del usuario. No la recomiendo como dependencia inicial de un diario ni como atajo para conseguir DSP. [API oficial](https://developer.android.com/reference/android/service/voice/VoiceInteractionService).

No almacenar audio en disco no significa que el micrófono esté apagado: la espera requiere capturar y procesar señal. Un detector especializado puede reducir el cómputo frente a ASR por ventanas, pero el coste en batería del dispositivo hay que medirlo. Tampoco se arregla el consumo trasladando sin más la escucha permanente al reloj.

La API de Android `SpeechRecognizer` advierte que no está pensada para reconocimiento continuo. Trama usa ASR local propio, así que esa advertencia no prohíbe su arquitectura; tampoco conviene sustituirla por reinicios continuos de esa API. [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).

## 3. Qué explica la implementación y qué dicen las pruebas anteriores

### Código actual

| Hallazgo | Consecuencia |
|---|---|
| `ContextualAudioCaptureEngine` evalúa ventanas de voz; `VoskGateAsr` transcribe esas ventanas para detectar triggers. | Es ASR por ventanas como filtro inicial; no detección de activación sobre DSP. Hay trabajo recurrente y errores antes de Whisper. |
| Whisper solo recibe ventanas seleccionadas. `UncertainGateFallbackPolicy` rescata principalmente gate vacío o de hasta dos palabras, con cooldown y restricciones de batería. | Si Vosk produce más de dos palabras equivocadas sin trigger, el ASR final no necesariamente tendrá ocasión de corregirlo. Mejorar solo Whisper no arregla ese agujero. |
| Las reglas y patrones predeterminados inspeccionados cubren acciones verbales, pero no la construcción general «faltan [producto]». | El ejemplo del usuario puede fallar por cobertura incluso con transcripción correcta. Añadir la expresión es posible; no resuelve su ambigüedad ambiental. |
| `KeywordListenerService` pausa al detectar reproducción multimedia en el propio móvil. | Protege frente a parte del audio reproducido, pero pierde oportunidades durante esa pausa y no identifica por sí solo una TV externa. |
| La verificación de hablante inspecciona la cola del audio; ausencia de perfil puede tratarse neutral y `ownerVerified` se deriva de no requerir revisión. | No se debe presentar «sin evidencia de rechazo» como «identidad verificada». En varios hablantes, la cola puede pertenecer a otra persona. |
| Algunas acciones adicionales se crean como `PENDING`, mientras la degradación posterior a sugerencia se aplica al registro principal. | Toda acción derivada necesita heredar correctamente el origen de autorización y las incertidumbres, no solo la primera. |
| El aprendizaje local compara decisiones y textos similares con reglas y umbrales. | No es entrenamiento acústico personalizado de Vosk/Whisper ni resuelve por sí solo voz, ruido o frases perdidas. |

Referencias de implementación: `shared/.../audio/VoskGateAsr.kt`; `app/.../audio/ContextualAudioCaptureEngine.kt`; `app/.../service/UncertainGateFallbackPolicy.kt`; `KeywordListenerService.kt:960`, `:1015`, `:1318`; `CaptureSaver.kt:115`; `ActionItemProcessor.kt:336`, `:1126`; `UserLearningDecisionEngine.kt`.

### Diagnósticos históricos

Se inspeccionaron `diagnostics.json` y `trama-diagnostics-20260507-2345.json`. Los campos `exportedAt` corresponden al 1 y 7 de mayo de 2026. Son anteriores a cambios relevantes de mayo y agosto; el campo `version=2` describe el formato del exportado, no identifica la versión de la app.

En el exportado del **7 de mayo**, ventana declarada de 72 horas:

- 528 eventos `ASR_FINAL/OK`, de los cuales 448 proceden de `uncertain_fallback` (84,8 %).
- 78 eventos `USER_DELETE`: 53 con motivo `noise` y 12 con `bad_asr`.

Son conteos de eventos, no una muestra de intenciones reales etiquetadas. No permiten calcular la precisión global, la cobertura de frases perdidas ni el rendimiento de la versión actual. Sí corroboran que rescatar audio incierto y limpiar resultados consumía una parte relevante del flujo histórico. No se han copiado conversaciones personales al informe.

La afirmación de un documento anterior de que cero falsas tareas demuestra «precisión conservadora» es insuficiente si tampoco se capturan tareas útiles. Igualmente, muchos tests de texto sintético pueden validar reglas sin medir el comportamiento con micrófono, bolsillo, TV o interrupciones reales.

## 4. Alternativas comparadas

Las valoraciones siguientes son juicios de ingeniería y producto, no resultados de una prueba comparativa ya realizada.

| Alternativa | Beneficio | Coste o límite | Decisión propuesta |
|---|---|---|---|
| Expresiones cotidianas durante todo el día → tareas | Máxima pasividad si acierta. | Ambigüedad, exposición prolongada a errores, consumo y necesidad de comprobar. | Retirar como promesa principal; referencia experimental sin modificar tareas. |
| Frase distintiva, p. ej. «Oye Trama, añade…» | Manos libres y señal de intención mucho más clara. | Sigue escuchando; hay que recordar invocarla; detector y consumo por validar en español. | Experimento optativo, con un botón siempre disponible. |
| Un toque en móvil/reloj → hablar | Acota el audio, expresa intención de capturar y evita detector permanente. | Requiere gesto y proximidad; no recoge espontáneamente todo lo que se dice. | Base del producto. |
| Sesión breve «Preparar compra» | Permite decir varios productos mientras se revisa la cocina. | Debe abrirse explícitamente y terminar de forma clara; otras voces siguen siendo un riesgo. | Alternativa especialmente útil para el ejemplo de plátanos. |
| Grabación deliberada de reunión | Contexto suficiente para proponer compromisos con evidencia. | Procesamiento largo y revisión posterior. | Complemento separado, con propuestas agrupadas. |
| Micrófono fijo o hardware dedicado | Puede mejorar distancia, alimentación y captura en una habitación. | Nuevo hardware, cobertura limitada y misma ambigüedad de intención. | No justifica cambiar ahora el producto. |

**Compromiso explícito:** el botón y la invocación no cumplen el requisito estricto de «no acordarme nunca de activar nada». Si eso es imprescindible, hay que aceptar un experimento ambiental limitado, con omisiones y revisión, o replantear alcance/hardware. No prometo las ventajas de captura deliberada conservando mágicamente toda la pasividad.

### Opción manos libres realista

Una frase predeterminada validada, no una lista ilimitada de expresiones naturales. «Oye Trama» es un ejemplo de UX, no una selección acústica ya validada.

Detector pequeño → acuse breve de activación → captura de la orden → ASR local → guardado/clarificación → acuse del resultado. Conservar pre-roll suficiente en memoria para no cortar el comienzo y distinguir «te he oído» de «ya está guardado». ASR y LLM pesado se usan después de la activación, no para interpretar todo el ambiente.

Porcupine documenta Android, español y activaciones personalizadas, por lo que es un candidato de prototipo. Requiere AccessKey: comprobar licencia, condiciones de funcionamiento desconectado y compatibilidad con la promesa local antes de seleccionarlo. No trasladar sus cifras publicitarias a Trama. [FAQ](https://picovoice.ai/docs/faq/porcupine/), [Android](https://picovoice.ai/docs/quick-start/porcupine-android/).

El catálogo oficial KWS consultado de sherpa-onnx enumera modelos chinos, ingleses y bilingües; no se encontró un KWS español listo para adoptar. Que ya se use sherpa para ASR no significa que baste activar una opción. Entrenar uno sería un proyecto distinto. [Catálogo KWS](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html).

## 5. Producto mínimo que realmente resolvería el problema

### Tres recorridos completos

| Lo que hace o dice la persona | Resultado útil |
|---|---|
| Abre «Compra» y dicta «Faltan plátanos y leche». | Dos artículos en una lista persistente; mensaje «Añadidos: plátanos y leche» y Deshacer. Sin inventar cantidades ni vencimiento. |
| Da una orden explícita: «Recuérdame llamar mañana a las seis de la tarde». | Recordatorio con fecha/hora visible y aviso comprobablemente programado. Si solo dice «a las seis», aclarar la ambigüedad. |
| Guarda «Ana me recomendó Casa Sol». | Recuerdo en su día, texto preservado y localizable. No un pendiente que haya que completar. |

«Tengo que hacer la compra» puede convertirse en tarea si se dicta en ese contexto. «Plátanos» es un artículo de una lista. Una lista debe permitir añadir varios artículos, tachar, conservar cantidades expresadas, evitar duplicados abiertos y volver a añadir algo ya comprado. No hace falta crear un gestor complejo de inventario.

El acceso principal puede seguir siendo único, para hablar o escribir; los atajos de Compra y Tarea son opcionales y aportan contexto. No exigir una clasificación extensa antes de cada nota.

### Confirmar según intención

- Orden explícita para una modificación local simple: guardar, mostrar resultado y Deshacer. No pedir una segunda autorización mecánica.
- Dictado genérico: expresa intención de guardar, no permiso para ejecutar cualquier acción extraída. Conservar nota y ofrecer conversión.
- Reunión o captura ambiental experimental: propuestas; ninguna acción fiable por la sola confianza del modelo.
- Fecha, destinatario o contenido ambiguos: aclaración inmediata o propuesta editable.
- Envíos, calendario externo y otros efectos fuera del registro local: revisión explícita del resultado antes de ejecutar.

Persistir por separado «orden explícita», «inferencia pendiente» y «confirmación posterior», sin convertir la confianza del modelo en prueba de decisión humana. Un número de confianza de Gemma no es una probabilidad de acierto calibrada.

### Problemas que hoy impiden esos resultados

1. **Compra sin lista:** `EntryActionBridge.kt:70` deriva BUY a tarea/recordatorio/evento; `ActionExecutor.kt:186` abre calendario para TODO. No completa el caso de llegar al supermercado con artículos tachables.
2. **Fecha de tarea no equivale a aviso:** no se encontró un planificador propio de notificaciones ligado a cada `dueDate` en las rutas inspeccionadas. `ActionExecutor.kt:167` abre `ACTION_SET_ALARM` y entrega solo hora/minuto, sin conservar la fecha solicitada. Hay que verificar «el próximo martes» de extremo a extremo y decidir fecha, aviso y zona horaria explícitamente.
3. **Dictado principal por resolver:** `OfflineDictationCapture` existe, pero sus usos encontrados están en lugares y ajustes. Home sigue teniendo como botón principal el control de escucha. Reutilizar piezas con un flujo de dictado acotado, sin exigir que la frase pase los filtros ambientales de activación.
4. **Cronología:** `CalendarScreen.kt:309` cambia `createdAt` de ciertas entradas antiguas del reloj. Resolver presentación de pendientes mediante consultas, no reescribiendo cuándo se capturaron.
5. **Backup:** `BackupManager.BackupEntry` omite evidencia de confirmación; conservarla junto a relaciones y fechas. El JSON actual no incluye audio binario: no prometer recuperación de ese audio sin ampliar el formato.
6. **Notas:** distinguir recuerdo/tarea y preservar el texto deliberado aunque la IA no encuentre acciones. Guardar no debe depender de descargar Gemma.

### Home y diario

Conservar calendario y lenguaje visual aprobado. Priorizar captura, tareas/compra relevantes y timeline legible; estado técnico solo cuando necesita intervención. La bandeja de revisión se alimenta de incertidumbre útil, no de todo el audio que consigue superar un filtro.

Poner búsqueda fiable, edición, deshacer y copia/restauración antes de más funciones generativas. Mantener origen y distinguir lo previsto de lo ocurrido. La página del día puede ser un borrador visible, pero la reflexión nocturna, las rachas y nuevos resúmenes no deben convertirse en trabajo obligatorio.

La utilidad del diario se demuestra también sin escribir una reflexión: encontrar una recomendación, recordar una decisión o saber qué quedó pendiente. Mejorar Chat después de comprobar que recupera información mejor que búsqueda y calendario, con fuentes internas accesibles.

## 6. Experimento antes de otra ronda de ajustes

Propuesta: 7–10 días de trabajo acotado para obtener una decisión técnica inicial, no para acreditar escucha perfecta todo el día. La validación de campo continúa el tiempo necesario para acumular exposición representativa. No repetir semanas de cambios de umbral sin una regla de salida.

### Comparación

- A: botón o acceso rápido → orden breve → resultado.
- B: activación distintiva → la misma orden y el mismo procesamiento posterior.
- C: expresiones ambientales con pipeline anterior, solo como referencia sin crear tareas reales; usar corpus consentido/histórico cuando sea suficiente para no reabrir una línea descartada.
- D, si la compra es frecuente: sesión explícita breve para añadir varios productos.

Comparar A/B en el mismo móvil, alternar orden de sesiones y reutilizar el procesamiento posterior para aislar el efecto de activación. Medir Wear por separado. Incluir un teléfono objetivo y al menos otro representativo antes de generalizar.

Usar al menos 200 órdenes deliberadas variadas, varias voces, acentos, negaciones, repeticiones y cantidades. Incluir ambiente con conversación, TV, música, bolsillo, mesa, cocina y calle. Separar sesiones de calibración y evaluación; reservar voces/sesiones que no se usen para ajustar. No limitarse a reproducir veinte frases conocidas.

Registrar la intención esperada mediante guion y marcas del participante, incluidas las oportunidades que no produjeron ninguna entrada. No calcular cobertura a partir de lo que ya está guardado. No hace falta grabar permanentemente conversaciones privadas para medir resultados: usar sesiones consentidas y anotaciones de eventos.

### Métricas y umbrales iniciales

Son objetivos propuestos para decidir, no prestaciones actuales ni estándares universales.

| Medida | Criterio inicial |
|---|---|
| Resultado final correcto | ≥95 % de las órdenes deliberadas en escenarios soportados terminan con contenido y destino correctos, sin edición. Es un umbral de prototipo, no justifica publicar el 5 % restante como errores silenciosos: distinguir fallo visible, aclaración y guardado incorrecto. Informar tamaño de muestra, campos erróneos y desglose por dispositivo/entorno. |
| Errores difíciles de detectar | Cero fechas/avisos silenciosamente erróneos en la batería de aceptación; ante duda pedir aclaración. No generalizar cero observado a riesgo cero. |
| Latencia | Medir desde fin de habla hasta resultado durable, p50 y p95, separando arranque frío y modelo ya cargado. Objetivo inicial p95 ≤5 s para órdenes cortas en dispositivo objetivo; si no se cumple, mostrar estado real y reducir procesamiento. El acuse de recepción y el del resultado final son medidas distintas. |
| Esfuerzo | Medir activar + hablar + esperar + repetir + corregir + revisar + comprobar. B solo se mantiene si aporta ventaja real sobre A en situaciones de manos ocupadas. |
| Activaciones falsas de B | Objetivo inicial ≤0,05 por hora de exposición representativa, registrando aparte si hubo interrupción y si llegó a guardarse contenido. No usar «accuracy» global sobre silencio. |
| Consumo | Comparar escucha apagada/A frente a B en jornadas equivalentes de 10 h. Propuesta de presupuesto de B: ≤5 puntos porcentuales adicionales atribuibles a escucha en el teléfono objetivo, sin deterioro térmico sostenido. Es un criterio por validar; no se suman ni se confunden aquí ubicación y otras funciones. |
| Disponibilidad | Medir minutos con audio real frente a minutos anunciados como activos; registrar llamadas y otras pausas. Nunca presentar silencio impuesto por Android como escucha funcional. |
| Integridad | Sin pérdidas en interrupción de proceso, reinicio, falta de modelo, disco lleno o doble entrega del reloj; error visible si no se pudo guardar. |

La tolerancia a activaciones falsas y gasto se debe contrastar con el usuario del piloto. Un ajuste que cumple precisión sacrificando tantas órdenes que obliga a repetir no supera la prueba.

Una semana sin falsas activaciones no demuestra una tasa muy baja sin conocer las horas y los entornos. Como orientación matemática, con cero eventos en T horas, el límite superior aproximado al 95 % es 3/T bajo un modelo Poisson estacionario. Para un límite de 0,05/h hacen falta aproximadamente 60 horas negativas sin eventos, y aun así no se acredita rendimiento en ambientes no representados. Para 0,01/h serían unas 300 horas. Este cálculo no sustituye diversidad ni validación por dispositivo.

### Regla de salida

- A funciona y B no aporta utilidad neta o supera consumo/errores: entregar A y sesión de compra; no bloquear el producto por B.
- B supera los criterios: ofrecerlo como opción en entornos/dispositivos validados, con estado y botón de respaldo.
- C sigue exigiendo mucha revisión o pierde frases al endurecer filtros: cerrar esa promesa de producto. No añadir simplemente más frases ni pasar todo a un LLM mayor.
- Ninguna modalidad de voz supera A por texto: mantener texto útil y resolver el componente acústico aislado; no esconder errores detrás de una pantalla más atractiva.

## 7. Orden de trabajo recomendado

1. **Integridad y contrato:** fechas, backup, tipos de contenido y evidencia de orden/confirmación. Acordar qué significa tarea, artículo, fecha límite y aviso.
2. **Primer recorrido completo:** lista de compra con entrada deliberada, acuse, edición, deshacer y tachado. Acceso visible desde Home; después atajo en reloj/widget cuando reduzca pasos reales.
3. **Otros dos recorridos:** recordatorio con fecha/aviso verificados y recuerdo localizable. Manejar fallo de modelo o permisos sin perder la captura deliberada.
4. **Experimento manos libres separado:** una activación distintiva, presupuesto acotado y decisión escrita. No usarlo como dependencia de los anteriores.
5. **Piloto de una o dos semanas:** episodios de utilidad, esfuerzo y confianza. Solo después añadir revisión diaria editable, más automatización contextual o mejorar Chat.

La promesa que propondría es: **«Dilo o escríbelo una vez; Trama lo guarda donde sirve y te lo devuelve cuando lo necesitas».** Para sostenerla, importa tanto recibir el aviso o encontrar la compra como reconocer la frase.
