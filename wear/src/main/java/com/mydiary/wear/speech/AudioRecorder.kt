package com.mydiary.wear.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class AudioRecorder {

    private val TAG = "AudioRecorder"

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    val bufferSize: Int = maxOf(
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING),
        2048
    )

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }
            audioRecord?.startRecording()
            Log.i(TAG, "Recording started (buffer: $bufferSize)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            false
        }
    }

    fun read(buffer: ByteArray): Int {
        return try {
            audioRecord?.read(buffer, 0, buffer.size) ?: -1
        } catch (e: Exception) {
            Log.w(TAG, "Read error", e)
            -1
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    fun isRecording(): Boolean = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}
