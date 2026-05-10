Coloca aqui un bundle pequeno de ASR streaming compatible con sherpa-onnx
para usarlo como gate local en Wear antes de caer a Vosk.

Formatos soportados por SherpaGateAsr:

1) Zipformer2 CTC:
- asr/sherpa-gate/zipformer2-ctc/model.onnx
- asr/sherpa-gate/zipformer2-ctc/tokens.txt

2) Transducer:
- asr/sherpa-gate/transducer/encoder.onnx
- asr/sherpa-gate/transducer/decoder.onnx
- asr/sherpa-gate/transducer/joiner.onnx
- asr/sherpa-gate/transducer/tokens.txt

Si no hay bundle real o los ficheros son placeholders vacios, Wear usa Vosk.
