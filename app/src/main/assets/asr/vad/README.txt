Coloca aqui el modelo Silero VAD compatible con sherpa-onnx.

Fichero esperado:
- silero_vad.onnx (~2 MB)

Descarga oficial:
- https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
- (alternativa) https://github.com/snakers4/silero-vad/raw/master/files/silero_vad.onnx

Ruta esperada:
- app/src/main/assets/asr/vad/silero_vad.onnx

Comportamiento:
- Si el modelo existe, Trama lo usa como filtro pre-Whisper para
  descartar ventanas sin habla (musica, silencio, ruido ambiente)
  ANTES de pagar el coste de transcribir con Whisper. Los exports de
  diagnostico mostraban ~43% de los decodes Whisper desperdiciados en
  tokens tipo [Musica]/(Puerto)/[Silencio].
- Si el modelo no esta presente, la captura sigue funcionando igual
  que antes: todas las ventanas con voz energetica van a Whisper y el
  filtrado de tokens sin habla ocurre solo despues del decode.
