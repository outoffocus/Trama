package com.trama.app.location

import android.util.Log
import com.trama.app.ui.SettingsDataStore
import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.math.cos

class PlaceResolver(
    private val repository: DiaryRepository,
    private val settings: SettingsDataStore
) {

    suspend fun findOrCreatePlace(latitude: Double, longitude: Double, visitedAt: Long): Place {
        val existing = findNearbyLocalPlace(latitude, longitude)
        if (existing != null) {
            repository.incrementPlaceVisit(existing.id, visitedAt)
            return (repository.getPlaceByIdOnce(existing.id) ?: existing)
        }

        val place = Place(
            name = "Lugar sin identificar",
            latitude = latitude,
            longitude = longitude,
            lastVisitAt = visitedAt,
            visitCount = 1
        )
        val id = repository.insertPlace(place)
        return place.copy(id = id)
    }

    suspend fun enrichPlace(placeId: Long, latitude: Double, longitude: Double) {
        val current = repository.getPlaceByIdOnce(placeId) ?: return
        if (current.userRenamed || current.name != "Lugar sin identificar") return

        val resolved = resolveRemotely(latitude, longitude) ?: return
        val updated = current.copy(
            name = resolved.name,
            type = resolved.type,
            updatedAt = System.currentTimeMillis()
        )
        repository.updatePlace(updated)
        repository.updateTimelineEventTitlesForPlace(placeId, resolved.name)
    }

    private suspend fun findNearbyLocalPlace(latitude: Double, longitude: Double): Place? {
        val deltaLat = 80.0 / 111_320.0
        val deltaLon = 80.0 / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.1))
        val candidates = repository.findPlacesInBoundingBox(
            minLat = latitude - deltaLat,
            maxLat = latitude + deltaLat,
            minLon = longitude - deltaLon,
            maxLon = longitude + deltaLon
        )
        return candidates.minByOrNull { haversineMeters(latitude, longitude, it.latitude, it.longitude) }
            ?.takeIf { haversineMeters(latitude, longitude, it.latitude, it.longitude) <= 80.0 }
    }

    private suspend fun resolveRemotely(latitude: Double, longitude: Double): ResolvedPlace? {
        return overpassLookup(latitude, longitude)
            ?: googlePlacesLookup(latitude, longitude)
            ?: nominatimLookup(latitude, longitude)
    }

    private suspend fun overpassLookup(latitude: Double, longitude: Double): ResolvedPlace? = withContext(Dispatchers.IO) {
        val query = """
            [out:json][timeout:12];
            (
              nwr(around:80,$latitude,$longitude)[amenity];
              nwr(around:80,$latitude,$longitude)[shop];
              nwr(around:80,$latitude,$longitude)[leisure];
              nwr(around:80,$latitude,$longitude)[tourism];
              nwr(around:80,$latitude,$longitude)[office];
            );
            out center 1;
        """.trimIndent()
        val encoded = "data=" + URLEncoder.encode(query, "UTF-8")
        val json = postJson("https://overpass-api.de/api/interpreter", encoded) ?: return@withContext null
        val elements = json.optJSONArray("elements") ?: return@withContext null
        if (elements.length() == 0) return@withContext null
        val best = pickClosestElement(elements, latitude, longitude) ?: return@withContext null
        val tags = best.optJSONObject("tags") ?: return@withContext null
        val name = tags.optString("name").takeIf { it.isNotBlank() } ?: return@withContext null
        val type = listOf("amenity", "shop", "leisure", "tourism", "office")
            .firstNotNullOfOrNull { key -> tags.optString(key).takeIf { it.isNotBlank() } }
        ResolvedPlace(name = name, type = type)
    }

    private suspend fun googlePlacesLookup(latitude: Double, longitude: Double): ResolvedPlace? = withContext(Dispatchers.IO) {
        val apiKey = settings.googlePlacesApiKey.first().trim()
        if (apiKey.isBlank()) return@withContext null

        val url = buildString {
            append("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
            append("?location=$latitude,$longitude")
            append("&radius=80")
            append("&key=").append(URLEncoder.encode(apiKey, "UTF-8"))
        }
        val json = getJson(url) ?: return@withContext null
        val results = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val first = results.optJSONObject(0) ?: return@withContext null
        val name = first.optString("name").takeIf { it.isNotBlank() } ?: return@withContext null
        val types = first.optJSONArray("types")
        val type = types?.optString(0)?.takeIf { it.isNotBlank() }
        ResolvedPlace(name = name, type = type)
    }

    private suspend fun nominatimLookup(latitude: Double, longitude: Double): ResolvedPlace? = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://nominatim.openstreetmap.org/reverse?format=jsonv2")
            append("&lat=").append(latitude)
            append("&lon=").append(longitude)
            append("&zoom=18&addressdetails=1")
        }
        val json = getJson(url, userAgent = "Trama/1.0") ?: return@withContext null
        val name = json.optString("name").takeIf { it.isNotBlank() }
            ?: json.optString("display_name").takeIf { it.isNotBlank() }
            ?: return@withContext null
        ResolvedPlace(name = name.substringBefore(",").ifBlank { name }, type = null)
    }

    private fun pickClosestElement(elements: JSONArray, latitude: Double, longitude: Double): JSONObject? {
        var best: JSONObject? = null
        var bestDistance = Double.MAX_VALUE
        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val lat = item.optDouble("lat", item.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
            val lon = item.optDouble("lon", item.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val distance = haversineMeters(latitude, longitude, lat, lon)
            if (distance < bestDistance) {
                bestDistance = distance
                best = item
            }
        }
        return best
    }

    private fun getJson(url: String, userAgent: String = "Trama/1.0"): JSONObject? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.onFailure {
            Log.w("PlaceResolver", "GET failed for $url", it)
        }.getOrNull()
    }

    private fun postJson(url: String, body: String): JSONObject? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "Trama/1.0")
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.outputStream.bufferedWriter().use { it.write(body) }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }.onFailure {
            Log.w("PlaceResolver", "POST failed for $url", it)
        }.getOrNull()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    private data class ResolvedPlace(
        val name: String,
        val type: String?
    )
}
