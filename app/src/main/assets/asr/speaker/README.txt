Coloca aqui el modelo offline de speaker embedding para la funcion "Solo mi voz".

Archivo esperado:
- model.onnx

Ruta exacta:
- app/src/main/assets/asr/speaker/model.onnx

Notas:
- La app verifica al hablante despues de transcribir con Whisper.
- Si falta este archivo, la opcion aparecera como no disponible en Ajustes.
- Usa un modelo compatible con sherpa-onnx SpeakerEmbeddingExtractor.
