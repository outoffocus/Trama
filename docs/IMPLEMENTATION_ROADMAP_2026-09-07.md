# Trama — renovación de experiencia y arquitectura

Fecha: 7 de septiembre de 2026.
Estado actualizado el 17 de septiembre de 2026: fases 0–5 cuentan con implementación funcional incremental. La validación automatizada de móvil y datos está correcta; siguen pendientes las pruebas físicas largas y la diarización real. El estado consolidado está en [IMPLEMENTATION_STATUS_2026-09-17.md](IMPLEMENTATION_STATUS_2026-09-17.md).

Actualización de ejecución: fase 0 iniciada y primer lote de correcciones implementado; [resultado técnico](PHASE_0_FIXES_2026-09-07.md). La primera iteración de la fase 1 está implementada y compilada; [entrega y validación](PHASE_1_UX_DELIVERY_2026-09-07.md). Siguen pendientes las validaciones físicas indicadas y la aceptación de experiencia en dispositivo.

Validación acordada: el usuario realizará las pruebas de experiencia y uso cotidiano. No se requiere reclutar participantes externos para avanzar o publicar. La facilidad de aprendizaje para personas nuevas queda como hipótesis pendiente, sin equiparar la validación del usuario con evidencia de comprensión por parte del público general.

Este documento actualiza el orden de ejecución del plan consolidado del 6 de septiembre. Prevalece para esta renovación cuando discrepen prioridades, navegación o alcance de voz. Conserva sus casos U01–U16 y añade explícitamente reuniones y escucha durante todo el día. Los croquis anteriores son exploraciones descartadas como dirección de interfaz por exceso de controles.

Entrega F2: [captura escrita e integridad](PHASE_2_DELIVERY_2026-09-09.md). Se conserva el esquema y el almacenamiento existente.

Entrega F3: [Inicio útil y anticipación](PHASE_3_DELIVERY_2026-09-09.md). Próximos compromisos, revisión agrupada, conciliación de calendarios y corrección de visitas.

Decisión posterior de producto: [la memoria diaria pasa a ser infraestructura privada](DAILY_MEMORY_DECISION_2026-09-09.md); se retira el resumen narrativo de Inicio y se conserva como índice derivado para búsqueda y chat.

F4 implementada, pendiente de validación física: [captura común y destinos accionables](PHASE_4_PROGRESS_2026-09-09.md). Texto y dictado comparten borrador; la corrección reprocesa la misma entrada, el modelo local vuelve a estar configurable y cada tipo de tarea ofrece un destino externo coherente.

F5 implementada salvo diarización y validación física: [reuniones y memoria recuperable](PHASE_5_PROGRESS_2026-09-10.md). Notas editables, búsqueda en transcripción, acciones aprobables, recuperación por bloques y navegación a la reunión de origen. La diarización sigue pendiente de un modelo de segmentación real; la verificación de la voz del propietario no se presenta como sustituto.

## 1. Resultado que queremos entregar

Una app Android que permita entender el día y lo próximo de un vistazo, capturar sin interrumpirse y recuperar recuerdos para actuar. Público general; los ejemplos personales no crean funciones específicas para un perfil.

Decisiones de partida:

- Renovación gradual, manteniendo datos y sustituyendo recorridos completos.
- Inicio breve que combina próximos compromisos y diario de lugares/registros.
- Captura preferida por palabra clave sin tocar el teléfono. Escucha opcional durante todo el día, con estado real, pausa y alternativa inmediata de texto/dictado.
- Una entrada visible «Añadir», con escritura/dictado y acceso explícito a «Grabar reunión». Configuración de voz fuera del flujo de captura.
- Reuniones con notas, diarización y acciones revisables: alcance requerido, no una función eliminable para simplificar Home.
- Búsqueda como acceso común para recuperación literal y preguntas; la respuesta conversacional conserva fuentes.
- Procesamiento personal local como restricción vigente. Cualquier cambio a servicios cloud necesita una decisión explícita de producto.
- Elegancia: jerarquía, legibilidad y comportamiento consistente; evitar un botón permanente por tipo de contenido.

## 2. Recorridos y contrato de confianza

| Recorrido | Resultado verificable |
|---|---|
| Abrir Trama | Reconocer estancias principales y siguiente compromiso relevante; distinguir previsto de ocurrido. |
| Capturar con voz | Saber si espera activación, captura, procesa o ha fallado; conservar una captura antes de intentar clasificarla. |
| Añadir bajo demanda | Escribir o dictar sin clasificar primero. Revisar únicamente los campos necesarios. |
| Prepararse y actuar | Anticipación elegida, avisos correctos y acciones para completar/posponer. |
| Recuperar memoria | Encontrar una experiencia o recomendación por contexto, fecha, lugar o persona; abrir su fuente. |
| Grabar reunión | Audio recuperable durante el proceso; notas editables, voces separadas, acciones con fragmento de origen. |

No confundir captura recibida, guardado local, propuesta extraída, acción confirmada y envío externo confirmado. Una animación o vibración no prueba la escritura en Calendar.

Una orden local explícita y reversible puede ejecutarse con Deshacer. Una inferencia, especialmente de conversación o reunión, queda como propuesta. Fechas ambiguas se aclaran sin perder el original. Una frase de activación no autoriza automáticamente todas las inferencias posteriores.

## 3. Arquitectura objetivo, sin modularización excesiva

Conservar inicialmente los módulos `app`, `shared` y `wear`. Separar responsabilidades dentro de ellos antes de añadir módulos Gradle.

1. **Presentación:** pantallas Compose pequeñas, ViewModels y estados de UI. Sin escrituras directas a proveedores de calendario ni decisiones de negocio dentro de composables.
2. **Casos de uso:** capturar, revisar propuesta, confirmar acción, completar/reprogramar, consultar día y procesar reunión. Compartidos por todas las entradas cuando corresponda.
3. **Datos:** repositorios y transacciones. Fuente de verdad local, migraciones y consultas acotadas al día o búsqueda; evitar cargar todo el historial para Home.
4. **Adaptadores:** audio/ASR, extracción, ubicación, calendario, avisos y reloj. Contratos sustituibles y fallos visibles.

Conceptos mínimos: captura original, propuesta con procedencia, acción confirmada, destino/estado de envío, visita, experiencia/recomendación y sesión de reunión con segmentos. Reutilizar entidades existentes cuando su significado sea compatible; no crear tablas nuevas por simetría.

Las capturas de texto, dictado y palabra clave entran en el mismo circuito. Una reunión conserva su sesión larga y publica propuestas en el mismo circuito de revisión. La diarización y la verificación de una voz son capacidades distintas.

Operaciones externas con identificador estable, conciliación y reintento que no duplique eventos. La cola de procesamiento no garantiza por sí misma alarmas puntuales. Coordinación explícita de propietario del micrófono móvil/reloj y transición a grabación de reunión.

## 4. Fases y entregables

### F0. Línea base y pruebas de viabilidad

- Ejecutar la versión actual en dispositivo y documentar seis recorridos, permisos, arranque sin modelos y fallos relevantes. La revisión previa fue de código, no una auditoría visual en dispositivo.
- Inventariar esquema, migraciones, copias y datos que deben preservarse. Medir inicio/Home con historial representativo.
- Clasificar componentes en conservar, envolver o reemplazar según pruebas, no por antigüedad.
- Probar temprano escucha prolongada, ASR y diarización local en dispositivos objetivo, incluyendo móvil/reloj e interrupciones. No posponer el riesgo principal hasta el final.
- Verificar capacidades reales de Calendar, avisos y Keep para cuentas personales. Keep automático no se promete; alternativa inicial: lista local y envío asistido.

**Salida:** inventario, dispositivos mínimos propuestos, resultados reproducibles y decisiones técnicas. Si una capacidad no cumple, documentar limitación y siguiente ensayo; no declararla resuelta por un prototipo.

### F1. Experiencia y sistema visual

- Prototipo de Inicio, Añadir, resultado/revisión, Buscar y reunión. Próximos días y detalle de lugar como navegación contextual.
- Fecha y navegación temporal conservadas. Un acceso a búsqueda y una acción de captura dominante. Escucha como estado accionable; grabación en curso con detener accesible.
- Tipografía del sistema, espaciado, colores y componentes reutilizables. Probar tema claro/oscuro, texto ampliado, lector de pantalla y objetivos táctiles.
- Diseñar estados vacíos, permisos denegados, modelos no disponibles, sin conexión, procesando y fallo. Pedir permisos en el contexto de la función elegida.
- Unificar vocabulario: dejar de abrir «Nueva tarea» desde «Añadir nota». No exigir categorías ni escoger aplicación de destino en cada entrada.
- Validación del prototipo por el usuario mediante los seis recorridos, con objetivos y datos concretos. Intentar primero cada escenario sin indicaciones paso a paso; registrar dudas, retrocesos, errores y pasos innecesarios. Corregir los bloqueos y repetir los escenarios afectados. El agente prepara los escenarios y revisa consistencia, accesibilidad y estados; el usuario evalúa claridad y comodidad reales.

**Salida:** propuesta visual concreta validada, mapa de navegación y componentes. La implementación de pantallas comienza sobre esta propuesta, no sobre los croquis saturados.

### F2. Integridad y primer recorrido completo

- Extraer de `CalendarScreen` estado y operaciones hacia ViewModel/casos de uso. Dividir por responsabilidades; no refactorizar todas las pantallas simultáneamente.
- Implementar captura de texto → persistencia → diario → detalle → edición. Funciona sin ASR/LLM.
- Añadir procedencia y estados necesarios mediante migraciones aditivas. Preservar fecha de origen, confirmaciones y vínculos.
- Mantener la ruta anterior accesible en builds internas mientras se valida la nueva. Retirada posterior; evitar escritura simultánea divergente entre dos modelos.
- Copia y restauración verificadas con datos representativos. No asumir que una APK anterior puede abrir un esquema nuevo.

**Salida:** recorrido fiable sobre datos existentes, reinicio sin pérdida y migración/restauración comprobadas.

### F3. Inicio útil y anticipación

- Inicio mixto: uno o dos próximos compromisos y recorrido del día. Pendientes de revisión agrupados en una entrada breve, sin desplazar siempre el diario.
- Estancias legibles; corregir identidad, intervalo o visita omitida. No fusionar establecimientos vecinos solo por proximidad.
- Calendarios seleccionados y eventos relevantes, propios o compartidos. Horizonte próximo y preferencias de anticipación simples.
- Avisos: fecha, hora, zona, cambios, cancelaciones, reinicio y permiso. Diferenciar reprogramar de posponer un aviso.

**Salida:** primera versión interna útil a diario, incluso sin voz; se entiende qué ocurrió y qué está previsto.

### F4. Captura de voz y acciones

- Dictado breve al circuito de captura existente. Guardar antes de extraer; corregir transcripción sin duplicar acciones.
- Activación por una lista pequeña de frases elegidas. En F1 probar si la gente comprende frases explícitas frente a expresiones naturales como «faltan…». Evaluar ambas técnicamente; no tratarlas como detectores equivalentes.
- Escucha elegida para todo el día: activación inicial, feedback, pausa/reanudación y recuperación tras interrupción. Estado coherente entre app y notificación; un fallo no aparece como escucha activa.
- Propuestas de tarea, lista y evento: previsualización contextual, fecha completa cuando corresponde, destino habitual editable y Deshacer para acciones locales.
- Calendar con escritura comprobada y conciliación ante respuesta incierta. Listas locales utilizables; Keep asistido mientras no se valide otra vía soportada.

**Salida:** captura manual, dictada y por activación comparten estados y resultados. Escucha prolongada supera las pruebas de F0 ampliadas; si no, permanece experimental y se informa de que el alcance manos libres aún no está terminado.

### F5. Reuniones y memoria recuperable

- Preservar grabación existente durante toda la renovación. Añadir controles visibles, estados de transferencia/procesado y recuperación ante interrupción.
- Notas editables y decisiones; transcripción temporal diarizada con voces sin nombre y asignación manual. No atribuir fragmentos inciertos ni inventar responsables.
- Acciones agrupadas por reunión, con enlace al fragmento y edición antes de aceptar. Una aceptación repetida no crea dos tareas.
- Opiniones por visita y recomendaciones con procedencia opcional. Estados pendiente/realizado elegibles sin formulario inicial extenso.
- Búsqueda por texto, ciudad, tipo, fecha y procedencia; preguntas con fuentes. Separar visita, recomendación y opinión. Si no hay evidencia, indicarlo.
- Intereses con fecha de decisión independiente del evento; convertirlos en compromiso solo al elegirlo.

**Salida:** guardar y recuperar experiencias y reuniones completas; la diarización se valida con audio real, no solo con transcripciones simuladas.

### F6. Piloto y publicación

- Piloto personal con el usuario; duración orientativa de 2–3 semanas, ajustable según cobertura de escenarios. No requiere participantes externos. Usar sus dispositivos disponibles y documentar la cobertura pendiente de otros equipos.
- Registrar fricciones durante el uso real y comprobar recuperación de recuerdos días después. Facilitar un registro breve: qué intentaba hacer, qué ocurrió y qué esperaba. Recoger diagnósticos con consentimiento, evitando audio/transcripciones personales por defecto.
- Pruebas de batería, segundo plano, llamadas, permisos revocados, falta de espacio, ausencia de modelos y grandes historiales. Reintentos no deben perder audio ni duplicar acciones.
- Revisar borrado/exportación, explicación de permisos y retención de audio, accesibilidad y requisitos de distribución vigentes.
- Corregir bloqueos, publicar gradualmente y retirar rutas antiguas solo cuando su sustitución esté validada. Preparar correcciones compatibles con el nuevo esquema.

**Salida:** candidato público con limitaciones explícitas y ninguna función simulada presentada como implementada.

## 5. Dependencias y orden

F0 → F1 → F2 → F3 → F4 → F5 → F6. Los ensayos técnicos de voz/diarización empiezan en F0 para informar el diseño. La grabación actual se mantiene operativa mientras llega F5. No se considera completa la visión solicitada entregando únicamente F3.

Orden inicial de trabajo: establecer línea base, validar prototipo, probar migración, construir captura textual completa, sustituir Home y continuar con voz y reuniones. Las estimaciones de esfuerzo se hacen al cerrar F0; dar ahora una fecha de publicación ocultaría incertidumbres de audio e integración.

## 6. Criterios de aceptación y medidas

Objetivos iniciales de producto, no resultados ya obtenidos:

- El usuario completa los seis recorridos con resultados correctos y sin bloqueos; registrar ayuda necesaria, dudas, errores y pasos evitables. Su aceptación determina el cierre de UX de cada entrega. No atribuir a esta prueba conclusiones sobre facilidad de aprendizaje de personas nuevas.
- Identificar el siguiente compromiso y las estancias principales en unos 10 segundos. Crear una nota simple sin clasificarla primero.
- Ninguna pérdida o duplicación en la batería definida de migración, reinicio, reintento y restauración. Esto no equivale a garantizar ausencia universal de fallos.
- Medir activaciones falsas por hora, activaciones omitidas por intento, acciones incorrectas por propuesta y latencia hasta confirmación. Separar silencio, televisión, calle, conversación y voz de otras personas.
- Medir consumo incremental de batería frente al mismo dispositivo sin escucha, en sesiones de jornada; fijar presupuesto y umbrales de voz al cerrar F0, antes del ensayo final.
- Diarización: medir error de segmentación/asignación con referencia revisada, solapamientos y 2–4 hablantes; medir además correcciones y atribuciones erróneas de acciones. Fijar umbrales y dispositivos soportados en F0.
- Avisos y envíos: verificar destino, fecha/hora, cancelación y cambios; no presentar éxito sin comprobación.
- Medir recuperación de un recuerdo días después y carga semanal de revisión. No optimizar cantidad de registros o tiempo dentro de la app como sinónimos de utilidad.

## 7. Fuera de esta renovación inicial

No incluir iOS, paridad completa con reloj, un editor general de automatizaciones, clasificación obligatoria extensa, avisos por cada lugar/recomendación ni migración a cloud implícita. La expansión de alcance se decide tras el piloto.

## 8. Documentos relacionados

- `CONSOLIDATED_PRODUCT_AND_IMPLEMENTATION_PLAN_2026-09-06.md`: casos de uso y contratos detallados.
- `USABILITY_AND_UTILITY_REVIEW_2026-09-06.md`: revisión inicial.
- `VOICE_CAPTURE_DECISION_2026-09-06.md`: riesgos y evidencia de voz.
- `PRODUCT_SPEC_2026-08-12.md`: dirección previa; sus decisiones incompatibles de navegación se sustituyen por este plan para la nueva versión.
