package com.trama.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trama.app.MainActivity
import com.trama.app.NotificationConfig
import com.trama.app.R
import com.trama.app.location.DwellDetector
import com.trama.app.location.DwellDetectorConfig
import com.trama.app.location.GeoSample
import com.trama.app.location.PlaceResolver
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DatabaseProvider
import com.trama.shared.model.DwellDetectionState
import com.trama.shared.model.Place
import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventSource
import com.trama.shared.model.TimelineEventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

class LocationForegroundService : LifecycleService() {

    companion object {
        private const val TAG = "LocationForegroundSvc"
        private const val CHANNEL_ID = NotificationConfig.CHANNEL_LOCATION
        private const val NOTIFICATION_ID = NotificationConfig.ID_LOCATION
    }

    private lateinit var settings: SettingsDataStore
    private lateinit var repository: com.trama.shared.data.DiaryRepository
    private lateinit var locationManager: LocationManager
    private lateinit var placeResolver: PlaceResolver
    private var detectorState: DwellDetectionState? = null
    private var currentIntervalMs: Long = 5 * 60 * 1000L
    private var currentDwellThresholdMs: Long = 15 * 60 * 1000L
    private var currentEntryRadiusMeters: Float = 80f
    private var currentExitRadiusMeters: Float = 200f

    private val listener = LocationListener { location ->
        handleLocation(location)
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsDataStore(applicationContext)
        repository = DatabaseProvider.getRepository(applicationContext)
        placeResolver = PlaceResolver(repository, settings)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
        lifecycleScope.launch(Dispatchers.IO) {
            detectorState = repository.getDwellDetectionState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Ubicación activa"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Ubicación activa"))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (!settings.locationEnabled.first()) {
                LocationDebugState.updateStatus("desactivado")
                stopSelf()
                return@launch
            }
            currentIntervalMs = settings.locationIntervalMinutes.first() * 60_000L
            currentDwellThresholdMs = settings.locationDwellMinutes.first() * 60_000L
            currentEntryRadiusMeters = settings.locationEntryRadiusMeters.first().toFloat()
            currentExitRadiusMeters = settings.locationExitRadiusMeters.first().toFloat()
            LocationDebugState.updateStatus("esperando muestras")
            requestUpdates()
        }

        ServiceController.notifyLocationRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        LocationDebugState.updateStatus("servicio detenido")
        ServiceController.notifyLocationRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun requestUpdates() {
        if (!hasFineLocationPermission()) {
            Log.w(TAG, "No location permission, stopping service")
            LocationDebugState.updateStatus("sin permiso de ubicación")
            stopSelf()
            return
        }

        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    currentIntervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    currentIntervalMs,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }.onFailure {
            Log.e(TAG, "Failed to request location updates", it)
            stopSelf()
        }
    }

    private fun handleLocation(location: Location) {
        val sample = GeoSample(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            timestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        )
        LocationDebugState.updateLastSample(
            "lat %.5f, lon %.5f · ±%dm".format(
                sample.latitude,
                sample.longitude,
                sample.accuracyMeters.toInt()
            )
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val detector = DwellDetector(
                DwellDetectorConfig(
                    entryRadiusMeters = currentEntryRadiusMeters,
                    exitRadiusMeters = currentExitRadiusMeters,
                    dwellThresholdMillis = currentDwellThresholdMs
                )
            )
            val result = detector.process(detectorState, sample)
            detectorState = result.nextState
            repository.saveDwellDetectionState(result.nextState)
            publishDebugState(result.nextState, sample.timestamp)

            result.closedDwells.forEach { dwell ->
                LocationDebugState.updateStatus("dwell cerrado · resolviendo lugar")
                val place = placeResolver.findOrCreatePlace(
                    latitude = dwell.latitude,
                    longitude = dwell.longitude,
                    visitedAt = dwell.endTimestamp
                )
                val durationMinutes = ((dwell.endTimestamp - dwell.startTimestamp) / 60_000L).coerceAtLeast(1L)
                val dataJson = JSONObject()
                    .put("lat", dwell.latitude)
                    .put("lon", dwell.longitude)
                    .put("placeName", place.name)
                    .put("placeType", place.type)
                    .toString()

                val eventId = repository.insertTimelineEvent(
                    TimelineEvent(
                        type = TimelineEventType.DWELL,
                        timestamp = dwell.startTimestamp,
                        endTimestamp = dwell.endTimestamp,
                        title = place.name,
                        subtitle = "$durationMinutes min",
                        dataJson = dataJson,
                        isHighlight = !place.isHome && !place.isWork && place.visitCount < 3,
                        placeId = place.id,
                        source = TimelineEventSource.AUTO
                    )
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    placeResolver.enrichPlace(place.id, dwell.latitude, dwell.longitude)
                    updateTimelineEventFromPlace(eventId, place.id)
                    LocationDebugState.updateStatus("dwell guardado")
                }
            }
        }
    }

    private fun publishDebugState(state: DwellDetectionState, now: Long) {
        val dwellStartedAt = state.dwellStartedAt
        if (state.active && dwellStartedAt != null) {
            val minutes = ((now - dwellStartedAt) / 60_000L).coerceAtLeast(0L)
            LocationDebugState.updateStatus("dwell activo")
            LocationDebugState.updateActiveDwell(
                "${minutes} min · ancla %.5f, %.5f".format(
                    state.anchorLat ?: 0.0,
                    state.anchorLon ?: 0.0
                )
            )
            LocationDebugState.updateCandidate("sin candidato")
            return
        }

        LocationDebugState.updateActiveDwell("sin dwell activo")
        val candidateStartedAt = state.candidateStartedAt
        val candidateLat = state.candidateLat
        val candidateLon = state.candidateLon
        if (candidateStartedAt != null && candidateLat != null && candidateLon != null) {
            val minutes = ((now - candidateStartedAt) / 60_000L).coerceAtLeast(0L)
            LocationDebugState.updateStatus("candidato en seguimiento")
            LocationDebugState.updateCandidate(
                "${minutes} min · %.5f, %.5f".format(
                    candidateLat,
                    candidateLon
                )
            )
        } else {
            LocationDebugState.updateStatus("esperando muestras")
            LocationDebugState.updateCandidate("sin candidato")
        }
    }

    private suspend fun updateTimelineEventFromPlace(eventId: Long, placeId: Long) {
        val event = repository.getTimelineEventByIdOnce(eventId) ?: return
        val place = repository.getPlaceByIdOnce(placeId) ?: return
        repository.updateTimelineEvent(
            event.copy(
                title = place.name,
                isHighlight = !place.isHome && !place.isWork && place.visitCount < 3,
                dataJson = JSONObject(event.dataJson ?: "{}")
                    .put("placeName", place.name)
                    .put("placeType", place.type)
                    .toString()
            )
        )
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ubicación",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Seguimiento pasivo de ubicación para detectar estancias"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Trama")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
