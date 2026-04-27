package com.trama.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.Source
import com.trama.shared.model.WatchAudioSyncMetadata
import com.trama.wear.NotificationConfig
import com.trama.wear.R
import com.trama.wear.sync.WatchToPhoneSyncer
import com.trama.wear.ui.WatchMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manual recorder on watch.
 *
 * The watch only captures PCM16 locally and transfers the audio to the phone.
 * Whisper and the heavier downstream processing stay on the phone side.
 */
class WatchRecordingService : LifecycleService() {

    companion object {
        private const val TAG = "WatchRecordingService"
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_WATCH_RECORDING
        private const val NOTIFICATION_ID = NotificationConfig.ID_WATCH_RECORDING
        private const val LOW_BATTERY_THRESHOLD = 10
        private const val SAMPLE_RATE_HZ = 16_000
        private const val READ_SIZE = 1024
        const val ACTION_START = "com.trama.watch.RECORD_START"
        const val ACTION_STOP = "com.trama.watch.RECORD_STOP"
    }

    private var audioRecord: AudioRecord? = null
    private val capturedChunks = mutableListOf<ShortArray>()
    private var capturedSamples = 0
    private var startTimeMs = 0L
    private var timerJob: Job? = null
    private var captureJob: Job? = null
    private var isActive = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(0))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isActive) return
        if (isBatteryLow()) {
            Log.w(TAG, "Battery too low for watch recording")
            stopSelf()
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(READ_SIZE * 2)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            stopSelf()
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            record.release()
            stopSelf()
            return
        }

        isActive = true
        capturedChunks.clear()
        capturedSamples = 0
        startTimeMs = System.currentTimeMillis()
        audioRecord = record

        RecordingController.update(true, 0, "", "")

        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000
                RecordingController.update(true, elapsed, "", "")
                updateNotification(elapsed)
                delay(1000)
            }
        }

        captureJob = lifecycleScope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(READ_SIZE)
            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(readBuffer, 0, readBuffer.size)
                    if (read > 0) {
                        synchronized(capturedChunks) {
                            capturedChunks += readBuffer.copyOf(read)
                            capturedSamples += read
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Watch audio capture failed", e)
            } finally {
                runCatching { record.stop() }
                record.release()
                if (audioRecord === record) {
                    audioRecord = null
                }
            }
        }

        Log.i(TAG, "Recording started")
    }

    private fun stopRecording() {
        if (!isActive) return
        isActive = false
        timerJob?.cancel()
        captureJob?.cancel()
        captureJob = null

        audioRecord?.let { record ->
            runCatching { record.stop() }
            record.release()
        }
        audioRecord = null

        val elapsed = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
        val pcmBytes = synchronized(capturedChunks) {
            shortsToBytes(capturedChunks)
        }

        if (pcmBytes.isNotEmpty()) {
            ioScope.launch {
                val repository = DatabaseProvider.getRepository(applicationContext)
                val syncer = WatchToPhoneSyncer(applicationContext, repository)
                val sampleCount = pcmBytes.size / 2
                val metadata = WatchAudioSyncMetadata(
                    createdAt = startTimeMs,
                    durationSeconds = elapsed,
                    sampleRateHz = SAMPLE_RATE_HZ,
                    source = Source.WATCH.name,
                    kind = "MANUAL_RECORDING",
                    pcmByteCount = pcmBytes.size,
                    pcmSampleCount = sampleCount,
                    rms = rms(pcmBytes)
                )

                val success = runCatching {
                    syncer.syncRecordingAudio(pcmBytes, metadata)
                }.isSuccess

                if (success) {
                    RecordingController.notifySaved(startTimeMs)
                    Log.i(TAG, "Recording audio transferred to phone (${elapsed}s)")
                } else {
                    Log.w(TAG, "Recording audio transfer failed")
                }

                RecordingController.reset()
                stopSelf()
            }
        } else {
            RecordingController.reset()
            stopSelf()
        }
        Log.i(TAG, "Recording stopped")
    }

    override fun onDestroy() {
        isActive = false
        timerJob?.cancel()
        captureJob?.cancel()
        audioRecord?.let { record ->
            runCatching { record.stop() }
            record.release()
        }
        audioRecord = null
        RecordingController.reset()
        super.onDestroy()
    }

    private fun isBatteryLow(): Boolean {
        val batteryManager = getSystemService(BatteryManager::class.java)
        return batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.let { it in 1 until LOW_BATTERY_THRESHOLD } == true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Grabación",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Grabación de voz activa" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(elapsedSeconds: Long): Notification {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val timeStr = "%02d:%02d".format(minutes, seconds)

        val stopIntent = Intent(this, WatchRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, WatchMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Grabando $timeStr")
            .setContentText("Audio local · se enviará al teléfono")
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(elapsedSeconds: Long) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(elapsedSeconds))
    }

    private fun shortsToBytes(chunks: List<ShortArray>): ByteArray {
        val totalSamples = chunks.sumOf { it.size }
        if (totalSamples <= 0) return byteArrayOf()
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        chunks.forEach { chunk ->
            chunk.forEach { sample -> buffer.putShort(sample) }
        }
        return buffer.array()
    }

    private fun rms(pcmBytes: ByteArray): Double {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount <= 0) return 0.0
        var sum = 0.0
        var byteIndex = 0
        repeat(sampleCount) {
            val lo = pcmBytes[byteIndex].toInt() and 0xFF
            val hi = pcmBytes[byteIndex + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            byteIndex += 2
        }
        return kotlin.math.sqrt(sum / sampleCount)
    }
}
