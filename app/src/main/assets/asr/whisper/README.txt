Coloca aqui un bundle Whisper multilingue compatible con sherpa-onnx.

La app detecta automaticamente uno de estos conjuntos, por orden de preferencia:

1. small
- small-encoder.int8.onnx
- small-decoder.int8.onnx
- small-tokens.txt

2. medium
- medium-encoder.int8.onnx
- medium-decoder.int8.onnx
- medium-tokens.txt

3. tiny
- tiny-encoder.int8.onnx
- tiny-decoder.int8.onnx
- tiny-tokens.txt

Ruta esperada:
- app/src/main/assets/asr/whisper/

Notas:
- La app no guarda audio del usuario.
- Los modelos se copian solo a almacenamiento privado de la app para poder abrirlos por ruta.
- Mientras estos ficheros no existan, la captura continua del movil quedara en estado degradado (`ASR local no disponible`).
- No se usa SpeechRecognizer como fallback porque no garantiza una ruta offline/local.
