Coloca aqui un bundle pequeno de ASR streaming compatible con sherpa-onnx
para usarlo como gate local en Wear antes de caer a Vosk.

Formatos soportados por SherpaGateAsr:

0) Bookbot Spanish phoneme transducer (preferente en Wear):
- asr/sherpa-gate/bookbot-phoneme-es/encoder.int8.onnx
- asr/sherpa-gate/bookbot-phoneme-es/decoder.int8.onnx
- asr/sherpa-gate/bookbot-phoneme-es/joiner.int8.onnx
- asr/sherpa-gate/bookbot-phoneme-es/tokens.txt

Fuente:
- https://huggingface.co/bookbot/sherpa-onnx-zipformer-streaming-robust-es-v0

Nota: emite fonemas, no texto normal. En Wear se usa solo como gate de
captura; el movil sigue haciendo la transcripcion final.

1) Zipformer2 CTC:
- asr/sherpa-gate/zipformer2-ctc/model.onnx
- asr/sherpa-gate/zipformer2-ctc/tokens.txt

2) Transducer:
- asr/sherpa-gate/transducer/encoder.onnx
- asr/sherpa-gate/transducer/decoder.onnx
- asr/sherpa-gate/transducer/joiner.onnx
- asr/sherpa-gate/transducer/tokens.txt

Si no hay bundle real o los ficheros son placeholders vacios, Wear usa Vosk.
