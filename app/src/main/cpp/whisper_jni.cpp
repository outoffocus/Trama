#include <jni.h>
#include <string>
#include <cstring>
#include <cmath>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static struct whisper_context *g_context = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_trama_app_speech_WhisperEngine_nativeLoadModel(
        JNIEnv *env, jobject /* this */, jstring modelPath) {

    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
    }

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    g_context = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_context) {
        LOGE("Failed to load whisper model");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_trama_app_speech_WhisperEngine_nativeTranscribe(
        JNIEnv *env, jobject /* this */, jfloatArray audioData, jstring language) {

    if (!g_context) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);
    jsize audioLen = env->GetArrayLength(audioData);

    const char *lang = env->GetStringUTFChars(language, nullptr);

    // Validate audio data — check for silence or corrupt samples
    bool hasSignal = false;
    for (jsize i = 0; i < audioLen; i++) {
        if (std::isnan(audio[i]) || std::isinf(audio[i])) {
            audio[i] = 0.0f; // Sanitize bad samples
        }
        if (!hasSignal && (audio[i] > 0.001f || audio[i] < -0.001f)) {
            hasSignal = true;
        }
    }
    if (!hasSignal) {
        LOGI("Audio is silent, skipping transcription");
        env->ReleaseFloatArrayElements(audioData, audio, 0);
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("");
    }

    // Force "es" if "auto" — auto-detect causes NaN in f16 dot product on some ARM64 devices
    const char *effectiveLang = lang;
    if (strcmp(lang, "auto") == 0) {
        effectiveLang = "es";
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = effectiveLang;  // Fixed language — auto-detect crashes on ARM64
    params.translate = false;         // Don't translate, keep original language
    params.print_progress = false;
    params.print_timestamps = false;
    params.no_timestamps = true;
    params.single_segment = false;    // Allow multiple segments for longer audio
    params.n_threads = 2;             // Conservative thread count for Android

    LOGI("Transcribing %d samples (~%.1f seconds), lang=%s (effective=%s)",
         audioLen, (float) audioLen / 16000.0f, lang, effectiveLang);

    int result = whisper_full(g_context, params, audio, audioLen);

    env->ReleaseFloatArrayElements(audioData, audio, 0);
    env->ReleaseStringUTFChars(language, lang);

    if (result != 0) {
        LOGE("Transcription failed with code %d", result);
        return env->NewStringUTF("");
    }

    int n_segments = whisper_full_n_segments(g_context);
    std::string text;
    for (int i = 0; i < n_segments; i++) {
        const char *segment_text = whisper_full_get_segment_text(g_context, i);
        if (segment_text) {
            if (!text.empty()) text += " ";
            text += segment_text;
        }
    }

    LOGI("Transcription result: '%s'", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_trama_app_speech_WhisperEngine_nativeFree(
        JNIEnv * /* env */, jobject /* this */) {
    if (g_context) {
        whisper_free(g_context);
        g_context = nullptr;
        LOGI("Model freed");
    }
}

} // extern "C"
