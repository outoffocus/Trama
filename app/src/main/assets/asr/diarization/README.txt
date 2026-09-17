Modelo de segmentación local para diarización de reuniones.

Archivo:
- segmentation.int8.onnx

Origen:
- sherpa-onnx-pyannote-segmentation-3-0/model.int8.onnx
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-segmentation-models

Se usa junto con asr/speaker/model.onnx. El PCM se procesa localmente en
ventanas acotadas; no se envía audio ni texto a servicios remotos.
La licencia del modelo está incluida en este directorio.
