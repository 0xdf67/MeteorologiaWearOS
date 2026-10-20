package com.thewizrd.weather_api.ipma

import android.util.Log
import com.thewizrd.shared_resources.exceptions.ErrorStatus
import com.thewizrd.shared_resources.exceptions.WeatherException
import com.thewizrd.shared_resources.locationdata.LocationQuery
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.await
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.getStream
import com.thewizrd.shared_resources.utils.Coordinate
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.weatherdata.WeatherAPI
import com.thewizrd.weather_api.locationdata.WeatherLocationProviderImpl
import com.thewizrd.weather_api.utils.APIRequestUtils.checkForErrors
import com.thewizrd.weather_api.utils.APIRequestUtils.checkRateLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

class IPMALocationProvider : WeatherLocationProviderImpl() {
    companion object {
        private const val LOCATIONS_URL = "https://api.ipma.pt/open-data/distrits-islands.json"

        @Volatile
        private var cachedLocations: List<IPMALocation>? = null
    }

    override fun getLocationAPI(): String {
        return WeatherAPI.IPMA
    }

    override fun isKeyRequired(): Boolean {
        return false
    }

    override fun supportsLocale(): Boolean {
        return true
    }

    override suspend fun isKeyValid(key: String?): Boolean {
        return false
    }

    override fun getAPIKey(): String? {
        return null
    }

    suspend fun getIPMALocations(): List<IPMALocation> = withContext(Dispatchers.IO) {
        cachedLocations?.let { return@withContext it }

        var response: Response? = null
        try {
            checkRateLimit(getLocationAPI())

            val request = Request.Builder()
                .url(LOCATIONS_URL)
                .cacheControl(
                    okhttp3.CacheControl.Builder()
                        .maxAge(1, TimeUnit.DAYS)
                        .build()
                )
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getLocationAPI(), response)

            val stream = response.getStream()
            val result = JSONParser.deserializer<IPMALocationResponse>(stream, IPMALocationResponse::class.java)

            val locations = result?.data ?: emptyList()
            if (locations.isNotEmpty()) {
                cachedLocations = locations
            }
            return@withContext locations
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMALocationProvider: error loading locations")
            if (ex is IOException) {
                throw WeatherException(ErrorStatus.NETWORKERROR, ex)
            }
            throw WeatherException(ErrorStatus.QUERYNOTFOUND, ex)
        } finally {
            response?.closeQuietly()
        }
    }

    @Throws(WeatherException::class)
    override suspend fun getLocations(
        ac_query: String?, weatherAPI: String?
    ): Collection<LocationQuery> = withContext(Dispatchers.IO) {
        val allLocations = getIPMALocations()
        val sourceAPI = weatherAPI ?: WeatherAPI.IPMA

        val queryClean = ac_query?.trim()?.removeAccents()?.lowercase(Locale.ROOT)

        val filtered = if (queryClean.isNullOrBlank()) {
            allLocations
        } else {
            allLocations.filter { loc ->
                val nameClean = loc.local?.removeAccents()?.lowercase(Locale.ROOT) ?: ""
                val areaClean = loc.idAreaAviso?.removeAccents()?.lowercase(Locale.ROOT) ?: ""
                nameClean.contains(queryClean) || areaClean.contains(queryClean)
            }
        }

        return@withContext filtered.map { loc ->
            createLocationModel(loc, sourceAPI)
        }
    }

    @Throws(WeatherException::class)
    override suspend fun getLocation(
        coordinate: Coordinate, weatherAPI: String?
    ): LocationQuery? = withContext(Dispatchers.IO) {
        val allLocations = getIPMALocations()
        if (allLocations.isEmpty()) return@withContext null

        val sourceAPI = weatherAPI ?: WeatherAPI.IPMA

        // Find nearest location using Haversine distance
        val nearest = allLocations.minByOrNull { loc ->
            val lat = loc.latitude?.toDoubleOrNull() ?: 90.0
            val lon = loc.longitude?.toDoubleOrNull() ?: 180.0
            IPMAUtils.calculateHaversineDistance(coordinate.latitude, coordinate.longitude, lat, lon)
        }

        return@withContext nearest?.let { createLocationModel(it, sourceAPI) }
    }

    @Throws(WeatherException::class)
    override suspend fun getLocationFromID(model: LocationQuery): LocationQuery? = withContext(Dispatchers.IO) {
        val globalId = model.locationQuery?.toIntOrNull() ?: return@withContext model
        val allLocations = getIPMALocations()
        val found = allLocations.find { it.globalIdLocal == globalId }
        return@withContext found?.let { createLocationModel(it, model.weatherSource ?: WeatherAPI.IPMA) } ?: model
    }

    private fun createLocationModel(ipmaLoc: IPMALocation, weatherAPI: String): LocationQuery {
        val lat = ipmaLoc.latitude?.toDoubleOrNull() ?: 0.0
        val lon = ipmaLoc.longitude?.toDoubleOrNull() ?: 0.0

        // Determine timezone for Portugal (Lisbon / Madeira / Azores)
        val tzLong = when {
            lon < -20.0 -> "Atlantic/Azores"
            lat < 35.0 -> "Atlantic/Madeira"
            else -> "Europe/Lisbon"
        }

        return LocationQuery().apply {
            locationName = ipmaLoc.local
            locationRegion = ipmaLoc.idAreaAviso
            locationCountry = "PT"
            locationLat = lat
            locationLong = lon
            locationTZLong = tzLong
            locationSource = getLocationAPI()
            weatherSource = weatherAPI
            locationQuery = ipmaLoc.globalIdLocal?.toString() ?: String.format(Locale.ROOT, "lat=%.4f&lon=%.4f", lat, lon)
        }
    }

    private fun String.removeAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
