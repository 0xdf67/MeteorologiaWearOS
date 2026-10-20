package com.thewizrd.weather_api.ipma

import android.util.Log
import com.thewizrd.shared_resources.exceptions.ErrorStatus
import com.thewizrd.shared_resources.exceptions.WeatherException
import com.thewizrd.shared_resources.json.listType
import com.thewizrd.shared_resources.locationdata.LocationQuery
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.await
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.getStream
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.ConversionMethods
import com.thewizrd.shared_resources.utils.Coordinate
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.weatherdata.WeatherAPI
import com.thewizrd.weather_api.locationdata.WeatherLocationProviderImpl
import com.thewizrd.weather_api.utils.APIRequestUtils.checkForErrors
import com.thewizrd.weather_api.utils.APIRequestUtils.checkRateLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class IpmaLocationProvider : WeatherLocationProviderImpl() {
    companion object {
        private const val LOCATIONS_URL = "https://api.ipma.pt/open-data/distrits-islands.json"
    }

    override fun getLocationAPI(): String {
        return WeatherAPI.IPMA
    }

    override fun isKeyRequired(): Boolean {
        return false
    }

    override fun supportsLocale(): Boolean {
        return false
    }

    override fun needsLocationFromName(): Boolean {
        return false
    }

    @Throws(WeatherException::class)
    override suspend fun getLocations(
        ac_query: String?, weatherAPI: String?
    ): Collection<LocationQuery> = withContext(Dispatchers.IO) {
        val locations = HashSet<LocationQuery>()
        val client = sharedDeps.httpClient
        var response: Response? = null
        var wEx: WeatherException? = null

        try {
            checkRateLimit()
            val request = Request.Builder()
                .cacheControl(CacheControl.Builder().maxAge(14, TimeUnit.DAYS).build())
                .get()
                .url(LOCATIONS_URL)
                .build()

            response = client.newCall(request).await()
            checkForErrors(response)

            val stream = response.getStream()
            val root = JSONParser.deserializer<List<IpmaDistrictItem>>(stream, listType<IpmaDistrictItem>())
            stream.closeQuietly()

            if (root != null) {
                val query = ac_query?.lowercase() ?: ""
                for (item in root) {
                    val name = item.local ?: ""
                    if (query.isBlank() || name.lowercase().contains(query)) {
                        locations.add(
                            LocationQuery().apply {
                                locationSource = weatherAPI ?: WeatherAPI.IPMA
                                locationQuery = item.globalIdLocal?.toString() ?: ""
                                locationName = name
                                locationCountry = "Portugal"
                                locationRegion = item.idAreaAviso ?: ""
                                locationLat = item.latitude?.toDoubleOrNull() ?: 0.0
                                locationLong = item.longitude?.toDoubleOrNull() ?: 0.0
                            }
                        )
                    }
                }
            }
        } catch (ex: Exception) {
            if (ex is IOException) {
                wEx = WeatherException(ErrorStatus.NETWORKERROR, ex)
            } else if (ex is WeatherException) {
                wEx = ex
            }
            Logger.writeLine(Log.ERROR, ex, "IpmaLocationProvider: error getting locations")
        } finally {
            response?.closeQuietly()
        }

        if (wEx != null) throw wEx
        if (locations.isEmpty()) {
            locations.add(LocationQuery())
        }
        return@withContext locations
    }

    @Throws(WeatherException::class)
    override suspend fun getLocationFromID(model: LocationQuery): LocationQuery? {
        return null
    }

    @Throws(WeatherException::class)
    override suspend fun getLocation(
        coordinate: Coordinate, weatherAPI: String?
    ): LocationQuery = withContext(Dispatchers.IO) {
        var closestLocation: LocationQuery? = null
        var minDistance = Double.MAX_VALUE
        val client = sharedDeps.httpClient
        var response: Response? = null

        try {
            checkRateLimit()
            val request = Request.Builder()
                .cacheControl(CacheControl.Builder().maxAge(14, TimeUnit.DAYS).build())
                .get()
                .url(LOCATIONS_URL)
                .build()

            response = client.newCall(request).await()
            checkForErrors(response)

            val stream = response.getStream()
            val root = JSONParser.deserializer<List<IpmaDistrictItem>>(stream, listType<IpmaDistrictItem>())
            stream.closeQuietly()

            if (root != null) {
                for (item in root) {
                    val lat = item.latitude?.toDoubleOrNull() ?: 0.0
                    val lon = item.longitude?.toDoubleOrNull() ?: 0.0
                    val dist = abs(ConversionMethods.calculateHaversine(coordinate.latitude, coordinate.longitude, lat, lon))
                    if (dist < minDistance) {
                        minDistance = dist
                        closestLocation = LocationQuery().apply {
                            locationSource = weatherAPI ?: WeatherAPI.IPMA
                            locationQuery = item.globalIdLocal?.toString() ?: ""
                            locationName = item.local ?: ""
                            locationCountry = "Portugal"
                            locationRegion = item.idAreaAviso ?: ""
                            locationLat = lat
                            locationLong = lon
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IpmaLocationProvider: error getting location by coordinate")
        } finally {
            response?.closeQuietly()
        }

        return@withContext closestLocation ?: LocationQuery()
    }

    override suspend fun isKeyValid(key: String?): Boolean = false
    override fun getAPIKey(): String? = null
}
