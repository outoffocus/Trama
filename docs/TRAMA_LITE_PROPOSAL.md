# Trama Lite: memoria contextual util, precisa y de bajo consumo

## 1. Vision

Trama Lite no debe ser una version recortada de Trama. Debe ser una version mas util, menos invasiva y menos intensiva para bateria.

La version actual de Trama ya contiene piezas valiosas: grabaciones manuales, modo reunion, captura desde Wear OS, importacion via share intent, lectura de capturas con Gemma multimodal, calendario, ubicacion por dwell, timeline, agenda, DailyPage, chat y procesamiento local/cloud. El problema de producto no es que falten capacidades, sino que la escucha contextual continua tiene demasiado coste para el valor que esta produciendo.

La nueva direccion debe cambiar el centro de gravedad:

- De "escuchar continuamente para intentar inferir contexto" a "capturar eventos ricos cuando hay una senal clara de utilidad".
- De "audio ambiental como fuente principal" a "inputs visuales, digitales, ubicacion, calendario, notificaciones filtradas y reuniones explicitas".
- De "crear tareas por cualquier transcripcion plausible" a "extraer memoria accionable con alto umbral de precision".
- De "asistente omnipresente" a "memoria operativa local-first que aparece cuando puede ahorrar friccion real".

Principio de producto:

> Trama Lite convierte fragmentos importantes del dia digital y fisico en memoria accionable, sin vigilancia continua.

Principio tecnico:

> Audio continuo apagado por defecto. Procesamiento caro solo bajo evento explicito, contexto rico o ventana temporal declarada por el usuario.

## 2. Objetivos

1. Reducir consumo de bateria de forma estructural, no solo optimizando el pipeline actual.
2. Mantener y mejorar los casos que ya aportan valor: reuniones, grabaciones manuales, capturas, URLs, calendario, ubicacion y agenda.
3. Aumentar precision reduciendo fuentes ruidosas y aplicando gates de calidad antes de persistir o notificar.
4. Convertir Gemma local en extractor multimodal de conocimiento y acciones, no en validador permanente de transcripciones ambientales.
5. Mantener local-first: los datos sensibles deben poder procesarse y guardarse sin salir del dispositivo cuando el modelo local este disponible.
6. Hacer que la app sea util aunque Gemma no este disponible: guardar contenido, OCR/transcripcion cuando aplique, crear notas transcript-only y dejar procesado diferido.

## 3. No objetivos

- No reconstruir "conciencia ambiental total".
- No analizar audio de fondo sin activacion explicita.
- No usar notificaciones de todas las apps sin filtros.
- No inferir estados personales delicados con baja evidencia.
- No crear tareas dudosas solo porque "podrian ser utiles".
- No depender de un unico LLM para decidir todo: reglas deterministas, heuristicas y umbrales deben seguir existiendo.

## 4. Diagnostico del codigo actual

### Piezas reutilizables

- `KeywordListenerService`, `ContextualAudioCaptureEngine`, `VoskGateAsr`, `SileroVadFilter` y `SherpaWhisperAsrEngine` ya tienen guardas de bateria, VAD, gate ligero, throttling termico y pausas por audio activo.
- `RecordingService`, `RecordingProcessor` y `RecordingProcessorWorker` ya cubren grabaciones manuales y analisis posterior.
- `SharedContentActivity` y `SharedContentWorker` ya importan texto, links y audio desde Android share sheet.
- `ScreenshotShareActivity`, `ScreenshotActionWorker` e `IsolatedGemmaVisionService` ya procesan capturas con Gemma multimodal local.
- `DwellDetector`, `LocationForegroundService`, `PlaceResolver` y `Place` ya permiten memoria de lugares visitados.
- `DailyPageGenerator`, `SummaryGenerator`, `DailyInsightExtractor` y `DailyPageMarkdownStore` ya generan memoria diaria.
- `ChatContextRetriever`, `DiaryAssistant` y `DiaryContextBuilder` ya permiten consultar memoria.
- `ActionItemProcessor`, `ActionQualityGate`, `DuplicateHeuristics` y `DeletionFeedbackStore` ya contienen una filosofia de precision conservadora.

### Problema principal

El pipeline de escucha continua esta demasiado cerca del microfono como fuente central del producto. Aunque se ha optimizado, sigue implicando:

- foreground service persistente de microfono;
- wakeups periodicos;
- VAD/gate activos;
- posibles ventanas Whisper;
- verificaciones, deduplicacion, LLM o heuristicas;
- diagnostico y rearmado;
- coste cognitivo si genera ruido.

Trama Lite debe preservar ese pipeline como capacidad opcional o heredada, pero no como modo principal.

## 5. Modos de producto

### 5.1 Modo Lite por defecto

Estado inicial recomendado:

- escucha continua del telefono desactivada;
- escucha continua del reloj desactivada;
- ubicacion opcional por dwell, con frecuencia adaptativa;
- calendario opcional;
- share sheet activo;
- procesamiento de capturas activo;
- grabaciones manuales y modo reunion activos;
- notificaciones solo por allowlist;
- Gemma local preferente si existe; cloud opcional y explicito.

La home debe comunicar un estado simple:

- "Listo para capturar"
- "Procesando contenido"
- "Reunion activa"
- "Ubicacion activa"
- "Modelo local no disponible"

Evitar que la experiencia principal dependa de "Escuchando...".

### 5.2 Modo wake puntual

Mantener algunas frases activadoras, pero sin buffer largo ni escucha ambiental semantica.

Wake words recomendadas:

- "Trama, recuerda esto"
- "Trama, mira esto"
- "Trama, resume esto"
- "Trama, guarda esto"
- "Trama, que tengo pendiente"

Comportamiento:

- Capturar solo una ventana corta posterior a la wake word.
- No pasar transcripciones ambientales largas a Gemma.
- Si la intencion es "mira esto", capturar pantalla o pedir share/screenshot segun permisos disponibles.
- Si la intencion es "recuerda esto", transcribir la nota corta y procesarla con el pipeline conservador existente.
- Si hay bateria baja, degradar a input manual/share sheet.

### 5.3 Modo reunion, visita o conversacion importante

Este modo ya existe conceptualmente en Trama y debe conservarse como una capacidad central, no como novedad. Es el caso donde el audio si tiene buen retorno: el usuario declara que el momento importa.

Casos:

- medico;
- trabajo;
- banco/gestoria;
- clase;
- llamada importante;
- visita a piso;
- reparacion tecnica;
- conversacion familiar importante;
- tramite administrativo.

Requisitos:

- inicio y fin explicitos;
- tipo de reunion seleccionado o inferido desde calendario/ubicacion;
- duracion visible;
- pausa/reanudar;
- transcripcion por chunks;
- resumen jerarquico;
- extraccion de decisiones, tareas, fechas, medicacion, cantidades, dudas y follow-ups;
- borrado opcional del audio tras generar resumen;
- salida diarizada con timestamps aproximados;
- avisos de baja confianza cuando haya ruido, solapamientos o dudas.

Para reuniones de 2h, no intentar meter todo a Gemma de una vez. Procesar por bloques y fusionar.

Arquitectura recomendada:

```text
audio largo
-> VAD/silence trimming
-> chunks de 5-10 min
-> Whisper local por chunk
-> diarizacion dedicada si esta disponible
-> resumen por chunk
-> extraccion de acciones/hechos por chunk
-> fusion final
-> DailyPage/timeline/recording detail
```

Diarizacion:

- Whisper no diariza hablantes de forma nativa.
- Si se quiere diarizacion real, integrar un modulo especifico: sherpa-onnx speaker diarization si viable en Android, pyannote/WhisperX solo si hay ruta externa, o speaker embeddings simples para 2 hablantes.
- Si no hay diarizacion fiable, usar etiquetas conservadoras: "Interlocutor", "Usuario", "Persona 2", "no claro".
- Nunca inventar quien dijo algo. En salud, marcar dudas.

### 5.4 Modo captura visual

Debe ser un pilar de Trama Lite.

Entradas:

- captura de pantalla compartida;
- imagen de documento;
- foto de carta, factura, receta, ticket, cartel, etiqueta;
- captura de chat/email;
- captura de web/app;
- PDF o imagen compartida si Android entrega URI compatible.

Salida:

- resumen breve;
- texto literal relevante;
- entidades;
- tareas;
- fechas;
- importes;
- personas;
- lugares;
- nivel de sensibilidad;
- acciones sugeridas.

La ruta actual `ScreenshotActionWorker` ya extrae acciones, pero debe evolucionar de "acciones desde capturas" a "conocimiento estructurado desde contenido visual".

### 5.5 Modo web/link/documento

Cuando el usuario comparte una URL o texto:

- guardar fuente literal;
- extraer titulo, resumen, entidades, acciones y por que podria importar;
- clasificar como articulo, producto, viaje, cita, tramite, referencia tecnica, compra, receta, evento, contacto u otro;
- crear tareas solo si hay un compromiso claro;
- indexar para busqueda/chat.

No todo link debe convertirse en tarea. Muchas veces debe ser memoria recuperable.

### 5.6 Modo contexto ligero

Fuentes:

- calendario;
- ubicacion por dwell;
- lugares favoritos/casa/trabajo;
- notificaciones allowlisted;
- estado de movilidad aproximado si es barato;
- hora/dia;
- agenda pendiente.

Uso:

- enriquecer prompts;
- generar recordatorios contextuales;
- mejorar DailyPage;
- responder preguntas;
- preparar al usuario antes de una reunion o visita.

Regla: el contexto ligero no debe despertar Gemma por si solo salvo que active una regla de alto valor.

Ejemplos:

- Llegar al medico + evento de calendario + modo reunion sugerido.
- Llegar al supermercado + tareas de compra pendientes.
- Llegar al trabajo + tareas laborales abiertas.
- Salir de una reunion + generar resumen si habia grabacion.

## 6. Casos de uso prioritarios

### 6.1 "Recuerdame lo importante de lo que acabo de ver"

El usuario comparte una captura, foto, URL o texto. Trama devuelve una ficha:

- que es;
- resumen;
- por que puede ser relevante;
- datos importantes;
- acciones sugeridas;
- recordatorios posibles;
- fuente.

Utilidad: memoria de navegacion y decisiones sin tener que ordenar manualmente.

### 6.2 "Extrae tareas de una pantalla"

Desde WhatsApp, Gmail, Slack, navegador, banco, calendario o documento:

- detectar compromisos;
- extraer fecha si esta clara;
- proponer accion en `SUGGESTED`;
- deduplicar contra agenda;
- no notificar como pendiente sin confianza alta.

### 6.3 Visita medica

Entrada:

- modo reunion medico;
- foto de receta/informe;
- ubicacion en clinica/hospital;
- evento de calendario si existe.

Salida:

- motivo de visita;
- sintomas descritos;
- indicaciones;
- medicacion/dosis/frecuencia si se escucha o aparece en documento;
- pruebas solicitadas;
- proximos pasos;
- dudas para proxima consulta;
- alertas de incertidumbre.

Regla de seguridad: no interpretar clinicamente. Resumir lo dicho y extraer tareas personales.

### 6.4 Gestiones y tramites

Fotos o capturas de cartas, facturas, multas, citas, bancos, seguros:

- importe;
- vencimiento;
- referencia;
- canal de accion;
- documento asociado;
- recordatorio.

### 6.5 Compras y decisiones

Capturas de productos, comparativas, tickets, menus, alojamientos, vuelos:

- precio;
- condiciones;
- pros/contras frente a preferencias guardadas;
- fecha limite;
- tarea sugerida si hay decision pendiente.

### 6.6 Memoria diaria realmente util

DailyPage debe dejar de ser solo resumen de tareas y lugares. Debe integrar:

- reuniones/grabaciones importantes;
- capturas y documentos procesados;
- links guardados;
- sitios visitados;
- eventos;
- tareas creadas/completadas;
- decisiones tomadas;
- cabos sueltos.

El resumen diario debe priorizar "cosas que manana te alegrara recordar".

### 6.7 Chat sobre memoria

Preguntas esperadas:

- "Que me dijo el medico sobre la medicacion?"
- "Donde vi lo del curso de IA?"
- "Que documentos tengo pendientes?"
- "Que productos compare para comprar?"
- "Que tareas salieron de la reunion con Elena?"
- "Cuando estuve en CTAG y que hice alli?"

Para esto hacen falta entidades y fuentes, no solo texto libre.

## 7. Modelo de datos recomendado

La entidad `DiaryEntry` sirve para tareas, pero Trama Lite necesita distinguir memoria, fuente, accion y evento. No forzar todo a `DiaryEntry`.

Agregar o introducir gradualmente:

```kotlin
ContextItem(
    id: Long,
    type: SOURCE_IMAGE | SOURCE_URL | SOURCE_TEXT | SOURCE_NOTIFICATION | SOURCE_AUDIO | SOURCE_DOCUMENT,
    title: String?,
    summary: String?,
    rawText: String?,
    sourceUri: String?,
    sourceApp: String?,
    createdAt: Long,
    capturedAt: Long,
    placeId: Long?,
    recordingId: Long?,
    sensitivity: LOW | MEDIUM | HIGH,
    processingStatus: PENDING | COMPLETED | FAILED,
    confidence: Float,
    metadataJson: String
)
```

```kotlin
ExtractedFact(
    id: Long,
    contextItemId: Long,
    kind: PERSON | PLACE | DATE | MONEY | MEDICATION | PRODUCT | DECISION | REQUIREMENT | CONTACT | OTHER,
    value: String,
    normalizedValue: String?,
    confidence: Float,
    evidence: String?
)
```

```kotlin
SuggestedAction(
    id: Long,
    contextItemId: Long?,
    recordingId: Long?,
    diaryEntryId: Long?,
    text: String,
    actionType: CALL | BUY | SEND | EVENT | REVIEW | TALK_TO | GENERIC,
    dueDate: Long?,
    priority: LOW | NORMAL | HIGH | URGENT,
    status: SUGGESTED | ACCEPTED | DISMISSED | COMPLETED,
    confidence: Float,
    reason: String?,
    evidence: String?
)
```

Se puede mapear `SuggestedAction` a `DiaryEntry` al aceptar o cuando supere umbral alto. Evita llenar la agenda con ruido.

## 8. Contrato JSON para Gemma multimodal

Usar un contrato unico para imagenes, URLs, documentos y notificaciones. Ejemplo:

```json
{
  "contentType": "screenshot|document|url|notification|meeting_chunk|unknown",
  "title": "string|null",
  "summary": "string",
  "literalText": "string",
  "entities": [
    {
      "kind": "person|place|date|money|medication|product|organization|contact|other",
      "value": "string",
      "normalizedValue": "string|null",
      "confidence": 0.0,
      "evidence": "string"
    }
  ],
  "actions": [
    {
      "text": "accion concreta y autosuficiente",
      "type": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
      "dueDate": "YYYY-MM-DD|null",
      "priority": "LOW|NORMAL|HIGH|URGENT",
      "confidence": 0.0,
      "evidence": "string"
    }
  ],
  "decisions": [
    {
      "text": "decision tomada",
      "confidence": 0.0,
      "evidence": "string"
    }
  ],
  "openQuestions": [
    {
      "text": "duda pendiente",
      "confidence": 0.0,
      "evidence": "string"
    }
  ],
  "sensitivity": "LOW|MEDIUM|HIGH",
  "confidence": 0.0,
  "uncertainties": ["string"]
}
```

Reglas de prompt:

- devolver solo JSON;
- no inventar fechas, personas, dosis, importes ni compromisos;
- toda accion debe tener evidencia;
- si la fuente es medica/legal/financiera, aumentar sensibilidad y marcar incertidumbres;
- si no hay accion clara, `actions=[]`;
- preservar texto literal relevante, pero omitir o enmascarar DNI, tarjetas, IBAN y secretos.

## 9. Pipeline tecnico propuesto

### 9.1 Captura

Crear una abstraccion:

```text
CaptureEvent
  id
  type
  sourceUri/sourceText/sourceApp
  timestamp
  placeSnapshot?
  calendarSnapshot?
  batterySnapshot?
  userIntent?
```

Fuentes:

- share sheet texto/URL/audio;
- screenshot/image share;
- recording finished;
- meeting finished;
- geofence/dwell event;
- allowlisted notification;
- manual quick capture;
- wake puntual.

### 9.2 Normalizacion

Por tipo:

- imagen: downsample + OCR/multimodal;
- URL: resolver titulo/contenido si hay permiso/red; si no, guardar URL + texto compartido;
- audio: Whisper por chunks;
- reunion: chunking + diarizacion opcional + resumen jerarquico;
- notificacion: app, titulo, texto, timestamp, acciones visibles;
- ubicacion: placeId + tipo de lugar, no lat/lon cruda en prompts salvo que sea necesario.

### 9.3 Extraccion

Gemma local primero si:

- modelo disponible;
- bateria no baja;
- dispositivo no en presion termica;
- evento tiene alto valor;
- usuario no ha pedido cloud.

Cloud opcional solo si:

- usuario lo habilita;
- contenido no es de sensibilidad alta o el usuario acepta;
- procesamiento local falla;
- hay red y bateria razonable.

### 9.4 Quality gate

Antes de persistir acciones:

- deduplicar;
- exigir verbo + objeto/persona/destino;
- exigir evidencia;
- bajar a `SUGGESTED` si confianza < 0.75;
- descartar si no hay utilidad;
- para salud/legal/finanzas, nunca crear acciones criticas sin revision salvo recordatorios obvios.

### 9.5 Persistencia

Guardar siempre la fuente si el usuario la envio intencionalmente, aunque no haya acciones.

Persistir:

- `ContextItem`;
- `ExtractedFact`;
- `SuggestedAction`;
- `DiaryEntry` solo para acciones aceptadas o de alta confianza;
- `TimelineEvent` para eventos importantes;
- `DailyPage` con referencias.

### 9.6 Recuperacion

El chat y DailyPage deben recuperar:

- tareas;
- recordings;
- context items;
- facts;
- places;
- calendar events;
- decisions;
- open questions.

No depender solo de `DiaryEntry`.

## 10. Politica de bateria

Trama Lite debe tener presupuesto de energia por tipo de trabajo.

### Siempre barato

- guardar share intent;
- guardar screenshot/image URI privada;
- crear tarea manual;
- registrar dwell ya calculado;
- mostrar UI.

### Diferible

- Gemma vision;
- resumen de URL/documento;
- DailyPage;
- deduplicacion LLM;
- chat complejo.

Ejecutar con WorkManager cuando:

- bateria no baja;
- no hay thermal moderate+;
- preferiblemente cargando para lotes largos;
- red disponible si requiere cloud.

### Caro y explicito

- reunion larga;
- transcripcion de audio largo;
- diarizacion;
- procesamiento multimodal de varias imagenes;
- resumen diario profundo.

Estos deben requerir accion explicita o estar programados.

### Desactivar por defecto

- audio continuo telefono;
- audio continuo reloj;
- fallback incierto frecuente a Whisper;
- procesado automatico de todas las notificaciones;
- analisis continuo de pantalla.

## 11. Politica de precision

La utilidad depende mas de no molestar que de capturar todo.

Reglas:

- Por defecto, guardar como memoria; no convertir en tarea.
- Tarea solo si hay accion concreta y evidencia.
- En duda: `SUGGESTED`, no `PENDING`.
- Si el usuario descarta, aprender tipo/fuente/patron.
- Si el usuario acepta, reforzar ese patron.
- Cada sugerencia debe explicar "por que aparece".
- Toda accion debe conservar enlace a la fuente.
- No resumir en exceso documentos sensibles: preservar datos literales importantes.

Metricas:

- acciones sugeridas por dia;
- tasa de aceptacion;
- tasa de descarte;
- falsos positivos reportados;
- tiempo de procesamiento por tipo;
- mAh estimado o proxies: wakeups, decodeMs, modelLoadMs, battery delta;
- porcentaje de eventos guardados sin acciones;
- latencia p50/p95 por fuente;
- fallos por modelo no disponible.

## 12. UX recomendada

### Home

Debe mostrar:

- inbox de sugerencias;
- proximas acciones;
- ultimos contextos guardados;
- boton grande de captura;
- boton de reunion;
- estado discreto de modelo/localizacion.

No centrar la experiencia en "activar escucha".

### Inbox

Separar:

- acciones sugeridas;
- recuerdos guardados;
- documentos pendientes de revisar;
- dudas abiertas.

Acciones rapidas:

- aceptar;
- editar;
- descartar;
- recordar;
- convertir en evento;
- abrir fuente.

### Detalle de contexto

Para una captura/URL/documento:

- fuente;
- resumen;
- texto relevante;
- entidades;
- acciones;
- decisiones;
- dudas;
- historial de procesamiento.

### Reunion

Pantalla dedicada:

- grabando;
- pausa;
- marcador rapido;
- tipo de reunion;
- duracion;
- notas manuales durante la reunion;
- al finalizar: "generando resumen".

Resumen:

- diario cronologico;
- puntos clave;
- decisiones;
- tareas;
- dudas;
- citas/fechas;
- transcripcion con busqueda;
- confianza.

## 13. Plan de implementacion incremental

### Fase 1: Recentrar el producto sin grandes migraciones

1. Crear un ajuste "Modo Lite" que apague escucha continua por defecto y mantenga share, capturas, grabaciones, reuniones, calendario y ubicacion.
2. Modificar Home para priorizar captura manual, share/capturas recientes, agenda y reunion.
3. Cambiar capturas para guardar tambien una memoria aunque no haya acciones.
4. Convertir acciones de capturas a `SUGGESTED` siempre, salvo confianza muy alta y tipo no sensible.
5. Crear metricas simples de utilidad: aceptadas/descartadas por fuente.

### Fase 2: ContextItem y extraccion multimodal unificada

1. Agregar entidades `ContextItem`, `ExtractedFact` y `SuggestedAction`.
2. Migrar `ScreenshotActionWorker` a `ContextExtractionWorker`.
3. Unificar prompt JSON.
4. Extender share sheet para URLs, texto, imagenes y documentos.
5. Actualizar DailyPage para incluir contextos y hechos.
6. Actualizar Chat retrieval para consultar context items y facts.

### Fase 3: Modo reunion robusto

1. Formalizar tipos de reunion.
2. Implementar resumen jerarquico por chunks.
3. Agregar diarizacion opcional o speaker labeling conservador.
4. Extraer decisiones, dudas y acciones con evidencia.
5. Crear resumen medico/gestoria/trabajo con plantillas diferentes.
6. Permitir borrar audio tras procesar.

### Fase 4: Contexto ligero inteligente

1. Allowlist de notificaciones.
2. Reglas por ubicacion: supermercado, trabajo, casa, medico.
3. Sugerencias contextuales sin LLM cuando sea posible.
4. Procesamiento diferido por bateria/thermal.
5. Evaluacion de utilidad por fuente y auto-throttling.

## 14. Criterios de exito

Producto:

- El usuario recibe menos ruido que en la version actual.
- La app produce recuerdos utiles incluso sin escucha continua.
- Las reuniones y visitas importantes generan resumen fiable y accionable.
- Las capturas/URLs/documentos se convierten en memoria recuperable.
- La agenda no se llena de basura.

Tecnico:

- Escucha continua apagada en Lite por defecto.
- Procesos caros pasan por WorkManager y constraints.
- Toda accion tiene fuente/evidencia.
- DailyPage integra contextos no-audio.
- Chat recupera contextos y facts.
- Diagnostico reporta utilidad por fuente y coste por pipeline.
- No se pierde funcionalidad actual de reuniones/grabaciones.

## 15. Decision recomendada

Construir Trama Lite como una rama funcional o modo de producto que reutilice la base actual, pero cambie los defaults:

- audio continuo pasa a avanzado/opcional;
- modo reunion se mantiene y se mejora;
- capturas, URLs, documentos y share sheet pasan a ser la entrada principal;
- ubicacion/calendario/notificaciones se usan como contexto ligero;
- Gemma local se usa para extraer estructura desde inputs ricos;
- la agenda solo recibe acciones de alta precision o aceptadas por el usuario.

La pregunta rectora para cada feature debe ser:

> Esto le ahorra al usuario una accion mental o practica real, con menos coste que hacerlo manualmente?

Si la respuesta no es clara, no debe estar activo por defecto.
