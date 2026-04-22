package com.trama.app.summary

import android.content.Context

object PromptTemplateStore {

    private const val PREFS = "prompt_templates"

    const val ACTION_ITEM = "action_item"
    const val RECORDING_ANALYSIS = "recording_analysis"
    const val RECORDING_TITLE = "recording_title"
    const val RECORDING_SUMMARY = "recording_summary"
    const val RECORDING_ACTIONS = "recording_actions"
    const val DAILY_SUMMARY = "daily_summary"
    const val ENTRY_VALIDATION = "entry_validation"
    const val PLACE_OPINION_SUMMARY = "place_opinion_summary"

    data class PromptDefinition(
        val id: String,
        val title: String,
        val subtitle: String,
        val defaultTemplate: String
    )

    val definitions = listOf(
        PromptDefinition(
            id = ACTION_ITEM,
            title = "Procesamiento de entrada",
            subtitle = "Limpia una nota y extrae tipo, fecha y prioridad",
            defaultTemplate = """
{{recentContext}}
Analiza esta nota de voz capturada y devuelve SOLO un objeto JSON valido.
- No añadas explicaciones, markdown, backticks ni texto extra.
- Si dudas entre varias interpretaciones, elige la mas conservadora y mas literal.
- Preserva la informacion importante: nombres propios, lugares, telefonos, numeros, cantidades y fechas.
- No resumas en exceso. Es mejor mantener contexto util que perder precision.
- Usa la transcripcion original como fuente principal de verdad si difiere del texto normalizado.

Formato exacto:
{
  "isActionable": true|false,
  "cleanText": "texto accionable minimo util, sin el trigger inicial",
  "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
  "dueDate": "YYYY-MM-DD o null",
  "priority": "LOW|NORMAL|HIGH|URGENT",
  "confidence": 0.0-1.0,
  "originalText": "texto original preservado",
  "normalizedText": "texto corregido y normalizado, preservando nombres y detalles",
  "dateMentions": ["menciones literales de fecha u hora"],
  "people": ["personas mencionadas"],
  "places": ["lugares mencionados"],
  "phones": ["telefonos mencionados"],
  "numbers": ["cantidades, importes o numeros importantes"],
  "extraActions": [
    {
      "cleanText": "accion adicional si existe",
      "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
      "dueDate": "YYYY-MM-DD o null",
      "priority": "LOW|NORMAL|HIGH|URGENT"
    }
  ]
}

NO EXTRAER (isActionable=false, confidence<=0.3):
- solo expresiones temporales o de frecuencia: "mañana", "hoy", "esta tarde", "todos los días", "a veces"
- frases sin verbo de accion claro + objeto/persona/destino: "hay que ver", "sería bueno"
- reflexiones, auto-charla o preguntas retóricas: "no sé qué hacer", "¿y si lo dejo?"
- fragmentos incompletos por mala transcripcion: "por la", "y luego el"
- meros comentarios sobre el pasado sin accion pendiente: "ayer fui al medico"
- transcripciones con palabras mayormente sin sentido o repeticiones de ruido
- referencias a una tarea que ya aparece en el CONTEXTO del inicio del prompt (ej: "eso que dije de Pedro", "la reunion del lunes"). En ese caso copia el texto literal de la nota en cleanText pero marca isActionable=false con confidence<=0.3 — no dupliques tareas existentes.

Cuando isActionable=false, igualmente rellena cleanText con el texto original trimmed para referencia.

Ejemplos:

Input: "recuerdame que mañana tengo que llamar a Pedro"
Output: {"isActionable": true, "cleanText": "Llamar a Pedro", "actionType": "CALL", "dueDate": "{{tomorrow}}", "priority": "NORMAL", "confidence": 0.9, ...}

Input: "mañana por la noche"
Output: {"isActionable": false, "cleanText": "mañana por la noche", "actionType": "GENERIC", "dueDate": null, "priority": "NORMAL", "confidence": 0.2, ...}

Input: "tengo que comprar leche y pan"
Output: {"isActionable": true, "cleanText": "Comprar leche y pan", "actionType": "BUY", "dueDate": null, "priority": "NORMAL", "confidence": 0.9, ...}

Input: "hay que"
Output: {"isActionable": false, "cleanText": "hay que", "actionType": "GENERIC", "dueDate": null, "priority": "NORMAL", "confidence": 0.1, ...}

Reglas:
- Tienes dos entradas:
  - originalText = transcripcion original de Whisper
  - normalizedInput = version corregida o normalizada previa
  - si hay conflicto entre ambas, conserva lo que preserve mejor nombres, lugares, telefonos, numeros y fechas
- originalText:
  - conserva el contenido tal como se entiende en la nota
  - no elimines nombres propios, lugares, telefonos, numeros, relaciones o fechas
- normalizedText:
  - corrige errores claros de transcripcion
  - preserva nombres, lugares, telefonos, numeros y fechas literales
  - manten el idioma original
- cleanText:
  - elimina solo el trigger inicial si existe: "recordar", "tengo que", "hay que", "deberia", "acordarme de", "acordarnos de", "me olvide", "se me fue la olla"
  - conserva el minimo contexto util para ejecutar la accion sin perder precision
  - NO elimines nombres propios, lugares, telefonos, numeros o fechas si ayudan a entender la accion
  - no la conviertas en una frase demasiado bonita ni demasiado general
  - es mejor una accion algo larga y fiel que una corta y ambigua
  - cleanText NUNCA puede ser solo una expresion temporal, frecuencia o habito. Si al quitar el trigger ("tengo que", "hay que", "recuerdame que", etc.) solo queda tiempo ("Mañana", "Cada manana", "Todos los dias"), falta el destino/persona/objeto: inclúyelo. La expresion temporal va en dueDate, no en cleanText. Ej: "mañana tengo que ir a CTAG" → cleanText "Ir a CTAG", dueDate={{tomorrow}}. NUNCA cleanText="Mañana".
- actionType: CALL=llamar, BUY=comprar, SEND=enviar, EVENT=cita/reunión, REVIEW=revisar, TALK_TO=hablar con, GENERIC=otro
- dueDate:
  - usa YYYY-MM-DD
  - conviertela solo si la nota menciona una fecha o momento temporal claro y resoluble
  - hoy = {{today}}
  - mañana = {{tomorrow}}
  - si no hay fecha clara o no se puede resolver con seguridad, devuelve null
  - la mencion literal debe ir en dateMentions aunque dueDate sea null
  - no inventes fechas
- priority:
  - URGENT si aparece urgencia explicita como "urgente", "ya", "ahora", "cuanto antes"
  - HIGH si indica alta importancia
  - LOW si transmite baja prioridad o "cuando pueda"
  - NORMAL en el resto
- confidence:
  - entre 0.0 y 1.0
  - alto (>=0.8) si el contenido es claro, especifico y accionable (verbo + objeto/persona/destino)
  - bajo (<=0.4) si la nota es ambigua, esta mal transcrita, o cleanText carece de objeto/destino
  - 0.3 o menos si isActionable=false, o si cleanText contiene un infinitivo español como destino, lugar o nombre propio (ej: "ir a aceptar", "en completar") — señal de error ASR donde un nombre fue sustituido por un verbo
- isActionable:
  - true SOLO si cleanText describe una accion concreta (verbo + complemento) que el usuario puede marcar como hecha
  - false si cleanText es solo temporal, un fragmento, un comentario pasado o ruido
  - ante la duda, false — es mejor perder una tarea que crear recordatorios inutiles
- people, places, phones, numbers:
  - incluye solo valores mencionados o claramente presentes en la nota
  - no inventes ninguno
- extraActions:
  - usa [] si la nota solo contiene una accion principal
  - añade acciones extra SOLO si son claramente independientes y accionables
  - conserva el contexto compartido dentro del texto de cada accion cuando haga falta para no perder informacion

Transcripcion original Whisper: "{{originalText}}"
Texto normalizado previo: "{{normalizedInput}}"
            """.trimIndent()
        ),
        PromptDefinition(
            id = RECORDING_ANALYSIS,
            title = "Análisis de grabación",
            subtitle = "Genera título, resumen, puntos clave y acciones",
            defaultTemplate = """
Analiza esta transcripcion de una grabacion de voz y devuelve SOLO un objeto JSON valido.
- No añadas explicaciones, markdown, backticks ni texto fuera del JSON.
- No inventes hechos, fechas ni tareas.
- Preserva nombres propios, lugares, telefonos, numeros, cantidades y fechas mencionadas.
- Prioriza fidelidad y utilidad sobre estilo.

Formato exacto:
{
  "title": "Título breve y descriptivo (max 8 palabras)",
  "summary": "Resumen de 2-3 párrafos del contenido principal",
  "keyPoints": ["Punto clave 1", "Punto clave 2"],
  "entities": {
    "people": ["personas mencionadas"],
    "places": ["lugares mencionados"],
    "phones": ["telefonos mencionados"],
    "numbers": ["cantidades o numeros importantes"],
    "dates": ["menciones literales de fecha u hora"]
  },
  "uncertainDetails": ["detalles ambiguos que conviene revisar"],
  "actionItems": [
    {
      "text": "Descripción de la tarea, con el minimo contexto util",
      "actionType": "CALL|BUY|SEND|EVENT|REVIEW|TALK_TO|GENERIC",
      "priority": "LOW|NORMAL|HIGH|URGENT",
      "dueDate": "YYYY-MM-DD o null",
      "originalText": "fragmento original relevante",
      "normalizedText": "texto corregido preservando nombres y detalles",
      "people": ["personas mencionadas en esta accion"],
      "places": ["lugares mencionados en esta accion"],
      "phones": ["telefonos mencionados en esta accion"],
      "numbers": ["cantidades o numeros importantes"],
      "dateMentions": ["menciones literales de fecha u hora"]
    }
  ]
}

Reglas:
- title:
  - resume el tema principal en pocas palabras
  - maximo 8 palabras
  - no uses comillas
- summary:
  - resume el contenido principal en 2 o 3 parrafos cortos
  - tono neutro, literal y fiel al contenido
  - incluye nombres, lugares y decisiones si aparecen de verdad
  - no limpies tanto que se pierdan entidades importantes
- keyPoints:
  - maximo 7 puntos
  - frases cortas con informacion realmente importante
  - si hay poco contenido, usa menos puntos
- entities:
  - recoge solo lo explicitamente mencionado o claramente presente
  - no inventes valores
- uncertainDetails:
  - usa [] si todo esta claro
  - si hay ambigüedad, prefierela aqui antes que inventar en actionItems
- actionItems:
  - incluye solo tareas, compromisos o cosas por hacer mencionadas claramente
  - si no hay tareas, usa []
  - text: accion minima util, pero sin perder nombres, lugares, numeros ni fechas relevantes
  - originalText: conserva el fragmento original o casi original que origina la accion
  - normalizedText: corrige transcripcion pero preserva entidades
  - actionType: CALL=llamar, BUY=comprar, SEND=enviar, EVENT=cita/reunión, REVIEW=revisar, TALK_TO=hablar con, GENERIC=otro
  - dueDate: SOLO si mencionan una fecha o momento temporal claro y explicito (hoy, mañana, lunes, 5 de abril, etc.) y se puede resolver con seguridad. Hoy={{today}}, mañana={{tomorrow}}. Si no se puede resolver con certeza, devuelve null y conserva la mención literal en dateMentions
  - priority: URGENT si urgente/ya/ahora/cuanto antes. HIGH si importante. LOW si cuando pueda. NORMAL en el resto
  - no crees dos tareas si en realidad es la misma accion expresada dos veces
  - mejor menos acciones y fiables que muchas dudosas

Transcripción:
\"\"\"
{{transcription}}
\"\"\"
            """.trimIndent()
        ),
        PromptDefinition(
            id = RECORDING_TITLE,
            title = "Título simple de grabación",
            subtitle = "Fallback local para extraer solo el título",
            defaultTemplate = """
Escribe un titulo breve para esta nota.
- Maximo 8 palabras
- Debe describir el tema principal
- Si hay nombres propios o lugares claramente centrales, conservalos
- Responde SOLO con el titulo, sin comillas ni texto extra

{{transcription}}
            """.trimIndent()
        ),
        PromptDefinition(
            id = RECORDING_SUMMARY,
            title = "Resumen simple de grabación",
            subtitle = "Fallback local para extraer solo el resumen",
            defaultTemplate = """
Resume esta nota en 2 o 3 frases.
- Se fiel al contenido
- No inventes informacion
- Conserva nombres propios, lugares, telefonos, numeros y fechas si aparecen
- Mejor literal que excesivamente elegante
- Responde SOLO con el resumen, sin encabezados ni viñetas

{{transcription}}
            """.trimIndent()
        ),
        PromptDefinition(
            id = RECORDING_ACTIONS,
            title = "Acciones simples de grabación",
            subtitle = "Fallback local para extraer acciones",
            defaultTemplate = """
Extrae las tareas o cosas por hacer de este texto.
Responde SOLO con un array JSON valido y nada mas.
Ejemplo: [{"text":"Llamar a Inés por el parque","type":"CALL","dateMentions":["mañana por la tarde"],"people":["Inés"],"places":["parque"]}]
Si no hay tareas, responde [].
Tipos validos: CALL, BUY, SEND, EVENT, REVIEW, TALK_TO, GENERIC
Reglas:
- text debe ser minimo util, claro y accionable, pero sin perder nombres, lugares, telefonos, numeros ni fechas relevantes
- corrige errores obvios de transcripcion
- no inventes tareas
- no incluyas contexto innecesario
- si una fecha aparece de forma implicita o poco clara, no la resuelvas, pero mantenla en dateMentions
- incluye people, places, phones y numbers cuando existan
- mejor menos tareas y fiables que muchas dudosas
Hoy es {{today}}.

Texto: "{{transcription}}"
            """.trimIndent()
        ),
        PromptDefinition(
            id = DAILY_SUMMARY,
            title = "Resumen diario",
            subtitle = "Genera narrativa, grupos y acciones del día",
            defaultTemplate = """
Eres un asistente personal. Analiza las notas de voz del usuario capturadas hoy ({{dateStr}}).

Responde UNICAMENTE con un JSON valido, sin texto antes ni despues, con esta estructura:
- "narrative": string con resumen de 2-3 frases del dia en español
- "groups": array de objetos con "label" (string), "emoji" (string), "items" (array de strings)
- "actions": array de objetos con "type" (CALENDAR_EVENT|REMINDER|TODO|MESSAGE|CALL|NOTE), "title" (string), y opcionalmente "description", "datetime" (ISO 8601), "contact"

Separacion funcional entre "groups" y "actions":
- "groups" describe lo que PASO, se hablo o se penso hoy — resumen retrospectivo de las notas (reflexiones, observaciones, lo que ya hiciste, contexto). Tono pasado.
- "actions" son tareas PENDIENTES deducidas de las notas que todavia no estan hechas — cosas por hacer, llamadas por hacer, recordatorios futuros. Tono futuro.
- Una misma nota NUNCA aparece en "groups" y "actions" a la vez. Decide el lado segun el tono: si es algo por hacer → solo "actions". Si es algo sobre lo que ya ocurrio o un pensamiento sin accion pendiente → solo "groups".

Reglas generales:
- No inventes hechos, fechas, personas ni acciones
- Si algo es ambiguo, elige la interpretacion mas conservadora
- Todo debe salir de las notas o del contexto de calendario proporcionado

Reglas para "groups":
- Maximo 5-6 categorias
- Los items deben ser frases cortas que resuman cada nota descriptiva (pasado, contexto, reflexion)
- NUNCA repitas una misma nota en dos categorias
- NUNCA incluyas en "groups" notas que ya estan en "actions"
- Usa etiquetas utiles y naturales en español

Reglas para "actions":
- Solo incluye acciones que se deduzcan claramente de las notas y que sigan pendientes
- Detecta fechas relativas: "mañana" = dia siguiente a {{dateStr}}
- Si mencionan una persona, usa "contact"
- "title" debe ser breve y accionable
- "description" solo si aporta contexto real, y preserva nombres y lugares si son relevantes
- Si no puedes inferir una fecha con claridad, omite "datetime"
- NO inventes acciones que no esten en las notas
- No dupliques acciones equivalentes
{{calendarContext}}
Notas del dia:
{{entriesText}}
            """.trimIndent()
        ),
        PromptDefinition(
            id = ENTRY_VALIDATION,
            title = "Validación de entrada",
            subtitle = "Decide si una transcripción parece una nota personal",
            defaultTemplate = """
Analiza esta transcripción de voz y responde SOLO con JSON:
{"es_nota_personal":true,"correccion":null,"confianza":0.9}

es_nota_personal=true si parece nota/tarea/recordatorio personal. false si es radio/TV/ruido.
correccion: corrige SOLO errores claros de transcripcion ASR (palabras mal reconocidas, ortografia). NO resumas, NO simplifiques, NO extraigas la idea principal. Si el texto es correcto o simplemente informal, devuelve null. La correccion debe ser el texto COMPLETO con minimas correcciones, no una version mas corta.
Sé permisivo: en duda, true.
No rechaces una nota solo porque contenga nombres poco comunes, lugares, telefonos, numeros o frases fragmentarias.
Si corriges el texto, preserva TODA la informacion: nombres propios, lugares, telefonos, numeros, fechas, motivos y contexto.
Mejor aceptar una nota ambigua que perder una nota personal real.
Error ASR frecuente — infinitivo como sustantivo: si un infinitivo español común (aceptar, completar, cancelar, pagar, enviar, comprar, borrar, guardar, llamar, gestionar, revisar, firmar, entregar, recoger, actualizar, confirmar, etc.) aparece como destino de "ir a", nombre de lugar, nombre de persona o sigla (ej: "ir a aceptar", "en aceptar", "llegar a cancelar"), es casi seguro un error ASR donde el modelo sustituyó un nombre propio o acrónimo. En ese caso, sustituye esa palabra por "[inaudible]" en la correccion y baja confianza a 0.4.

Transcripción: "{{text}}"
            """.trimIndent()
        ),
        PromptDefinition(
            id = PLACE_OPINION_SUMMARY,
            title = "Resumen de opinión de lugar",
            subtitle = "Resume lo que te pareció un sitio",
            defaultTemplate = """
Resume esta opinión personal sobre un lugar en 2 o 3 frases.
- Sé fiel al contenido
- Conserva detalles importantes como comida, servicio, ambiente, precio o contexto
- Si hay valoración en estrellas, intégrala de forma natural
- No inventes nada
- Responde SOLO con el resumen, sin título ni viñetas

Lugar: {{placeName}}
Valoración: {{ratingText}}
Opinión:
{{opinionText}}
            """.trimIndent()
        )
    )

    fun get(context: Context, id: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val default = definitions.firstOrNull { it.id == id }?.defaultTemplate.orEmpty()
        return prefs.getString(id, default) ?: default
    }

    fun set(context: Context, id: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(id, value.trim())
            .apply()
    }

    fun reset(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(id)
            .apply()
    }

    fun render(context: Context, id: String, replacements: Map<String, String>): String {
        var template = get(context, id)
        replacements.forEach { (key, value) ->
            template = template.replace("{{${key}}}", value)
        }
        return template
    }
}
