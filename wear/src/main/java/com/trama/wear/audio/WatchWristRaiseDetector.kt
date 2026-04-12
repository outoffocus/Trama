package com.trama.wear.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

/**
 * Detects wrist-raise gestures using accelerometer and gravity sensors.
 *
 * This is used to gate the audio capture on Wear OS to save battery.
 */
class WatchWristRaiseDetector(context: Context) {
    companion object {
        private const val TAG = "WristRaiseDetector"
        private const val RAISE_THRESHOLD = 7.0f
        private const val LOWER_THRESHOLD = 3.0f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    var onRaise: (() -> Unit)? = null
    var onLower: (() -> Unit)? = null

    private var isRaised = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val z = event.values[2] // Vertical acceleration
                if (z > RAISE_THRESHOLD && !isRaised) {
                    isRaised = true
                    onRaise?.invoke()
                } else if (z < -LOWER_THRESHOLD && isRaised) {
                    isRaised = false
                    onLower?.invoke()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.i(TAG, "Wrist raise detector started")
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        Log.i(TAG, "Wrist raise detector stopped")
    }
}
