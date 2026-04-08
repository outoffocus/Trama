Bundled Moonshine assets for the lightweight semantic gate live here.

Trama uses this bundle as the first-pass ASR:
VAD -> Moonshine gate -> IntentDetector -> Whisper small

Preferred official bundle format (Moonshine v2):
- encoder_model.ort
- decoder_model_merged.ort
- tokens.txt

Legacy format also supported:
- preprocessor.onnx
- encoder*.onnx
- uncached_decoder*.onnx
- cached_decoder*.onnx
- merged_decoder*.onnx
- tokens.txt

Official Spanish download:
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2

These files may be large and should normally stay out of Git. Place them here
locally to activate the Moonshine gate on mobile.
