# IA local y confirmacion de sugerencias

## Contrato de privacidad

Trama no ofrece Gemini Cloud ni permite configurar una clave de API para IA.
La aplicacion elimina en el siguiente arranque cualquier valor antiguo
`gemini_api_key` que pudiera quedar de versiones anteriores.

El procesamiento personal sigue esta ruta:

1. Vosk, Silero y Whisper/sherpa procesan la captura de voz localmente.
2. Gemma estructura acciones, grabaciones, resumenes y respuestas si el modelo
   esta descargado.
3. Si Gemma no esta disponible, las reglas deterministas mantienen operativas
   las capturas claras y mandan los casos dudosos a revision.
4. No existe fallback a un modelo remoto.

La clave de Google Places es independiente y solo se utiliza para resolver
lugares cuando esa funcion esta configurada.

## Confianza automatica y confirmacion humana

Son evidencias distintas y no se deben mezclar:

- `confidence` y `llmConfidence` describen la decision automatica.
- `userConfirmedAt` indica que una persona confirmo la sugerencia.
- `verificationSource` guarda si se confirmo desde Home/Calendario, Agenda,
  una grabacion o el detalle.

Confirmar no cambia artificialmente la confianza del modelo a `1.0`. La entrada
pasa de `SUGGESTED` a `PENDING` y queda catalogada como `CONFIRMADA`.

## Como confirmar

- En Home/Calendario, pulsa `Confirmar` en la tarjeta sugerida. El gesto de
  deslizar a la derecha se conserva como atajo.
- En Agenda, deslizar a la derecha una sugerencia la confirma; no la marca como
  completada.
- En el detalle de una grabacion, usa `Anadir` o `Anadir todas`.

Si `Aprender de mis decisiones` esta activo, la confirmacion se conserva como
ejemplo positivo local. Los descartes por `ruido` o `no era para mi` actuan como
ejemplos negativos. Este aprendizaje protege casos parecidos, pero nunca evita
un rechazo real de la verificacion de voz ni convierte dialogo impersonal en una
tarea fiable.

## Perfil de voz opcional

No tener perfil de voz es un estado neutral. Una frase personal explicita puede
ser fiable por su estructura y confianza sin obligar al usuario a registrar su
voz.

Cuando existe un perfil habilitado:

- una coincidencia aporta evidencia positiva;
- una comprobacion inconclusa envia la captura a sugerencias;
- una falta de coincidencia real bloquea la captura.

## Diagnostico

La tarjeta `Cobertura local` separa:

- horas de cobertura estimada;
- transcripciones disparadas y ambientales;
- textos sin intencion;
- capturas guardadas y sugeridas;
- segmentos continuos de 30 segundos;
- pausas causadas por audio reproducido en este dispositivo.

Una television externa no activa la deteccion multimedia de Android. Su audio
puede aparecer como transcripcion ambiental y debe terminar en `NO_INTENT`, no
como tarea.

Los diagnosticos serializan decimales con punto y aceptan exportaciones antiguas
con coma. El calculo de bateria separa sesiones de carga y descarga, ignora
lecturas iniciales sin estabilizar y exige al menos 30 minutos para calcular una
tasa.

## Persistencia

Room version 16 anade a `diary_entries`:

- `userConfirmedAt INTEGER NULL`
- `verificationSource TEXT NULL`

La migracion `15 -> 16` conserva todas las entradas existentes.
