package com.thewizrd.weather_api.ipma

import android.util.Log
import com.thewizrd.shared_resources.exceptions.ErrorStatus
import com.thewizrd.shared_resources.exceptions.WeatherException
import com.thewizrd.shared_resources.icons.WeatherIcons
import com.thewizrd.shared_resources.locationdata.LocationData
import com.thewizrd.shared_resources.locationdata.LocationQuery
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.await
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.getStream
import com.thewizrd.shared_resources.utils.Coordinate
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.LocationUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.createUnsupportedLocationException
import com.thewizrd.shared_resources.weatherdata.WeatherAPI
import com.thewizrd.shared_resources.weatherdata.model.Weather
import com.thewizrd.shared_resources.weatherdata.model.WeatherAlert
import com.thewizrd.weather_api.utils.APIRequestUtils.checkForErrors
import com.thewizrd.weather_api.utils.APIRequestUtils.checkRateLimit
import com.thewizrd.weather_api.weatherdata.WeatherProviderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class IPMAWeatherProvider : WeatherProviderImpl() {
    companion object {
        private const val BASE_URL = "https://api.ipma.pt/open-data/"
        private const val DAILY_FORECAST_URL = BASE_URL + "forecast/meteorology/cities/daily/%d.json"
        private const val WEATHER_TYPES_URL = BASE_URL + "weather-type-classe.json"
        private const val WIND_SPEED_CLASSES_URL = BASE_URL + "wind-speed-daily-classe.json"
        private const val PRECIP_CLASSES_URL = BASE_URL + "precipitation-classe.json"
        private const val STATIONS_URL = BASE_URL + "observation/meteorology/stations/stations.json"
        private const val OBSERVATIONS_URL = BASE_URL + "observation/meteorology/stations/observations.json"
        private const val WARNINGS_URL = BASE_URL + "forecast/warnings/warnings_www.json"
        private const val UV_URL = BASE_URL + "forecast/meteorology/uv/uv.json"

        @Volatile
        private var cachedWeatherTypes: Map<Int, IPMAWeatherType>? = null

        @Volatile
        private var cachedWindSpeedClasses: Map<Int, IPMAWindSpeedClass>? = null

        @Volatile
        private var cachedPrecipClasses: Map<Int, IPMAPrecipClass>? = null

        @Volatile
        private var cachedStations: List<IPMAStation>? = null
    }

    init {
        mLocationProvider = IPMALocationProvider()
    }

    override fun getWeatherAPI(): String {
        return WeatherAPI.IPMA
    }

    override fun supportsWeatherLocale(): Boolean {
        return true
    }

    override fun isKeyRequired(): Boolean {
        return false
    }

    override fun supportsAlerts(): Boolean {
        return true
    }

    override fun needsExternalAlertData(): Boolean {
        return false
    }

    override fun isRegionSupported(location: LocationData): Boolean {
        return LocationUtils.isPortugal(location)
    }

    override fun isRegionSupported(location: LocationQuery): Boolean {
        return LocationUtils.isPortugal(location)
    }

    override fun getHourlyForecastInterval(): Int {
        return 1
    }

    override suspend fun isKeyValid(key: String?): Boolean {
        return false
    }

    override fun getAPIKey(): String? {
        return null
    }

    override suspend fun updateLocationQuery(weather: Weather): String {
        return weather.query ?: ""
    }

    override suspend fun updateLocationQuery(location: LocationData): String {
        val query = location.query
        val globalId = query?.toIntOrNull()
        if (globalId != null && globalId > 0) {
            return globalId.toString()
        }

        val nearest = (mLocationProvider as IPMALocationProvider).getLocation(
            Coordinate(location.latitude, location.longitude),
            getWeatherAPI()
        )
        return nearest?.locationQuery ?: "1110600" // Default to Lisboa if unknown
    }

    @Throws(WeatherException::class)
    override suspend fun getWeatherData(location: LocationData): Weather = withContext(Dispatchers.IO) {
        if (!LocationUtils.isPortugal(location)) {
            throw WeatherException(ErrorStatus.LOCATIONNOTSUPPORTED)
                .initCause(createUnsupportedLocationException(getWeatherAPI(), location))
        }

        val globalIdStr = updateLocationQuery(location)
        val globalId = globalIdStr.toIntOrNull() ?: 1110600

        // Find location info from IPMALocationProvider to get idAreaAviso
        val ipmaLocs = (mLocationProvider as IPMALocationProvider).getIPMALocations()
        val locObj = ipmaLocs.find { it.globalIdLocal == globalId }
        val idAreaAviso = locObj?.idAreaAviso ?: location.region

        // Fetch datasets
        val dailyForecast = fetchDailyForecast(globalId)
        val weatherTypes = fetchWeatherTypes()
        val windSpeedClasses = fetchWindSpeedClasses()
        val precipClasses = fetchPrecipClasses()
        val stations = fetchStations()
        val observationsMap = fetchObservations()
        val warnings = fetchWarnings()
        val uvItems = fetchUVIndex()

        return@withContext createIPMAWeatherData(
            locationData = location,
            dailyForecastResponse = dailyForecast,
            weatherTypes = weatherTypes,
            windSpeedClasses = windSpeedClasses,
            precipClasses = precipClasses,
            stations = stations,
            observationsMap = observationsMap,
            warnings = warnings,
            uvItems = uvItems,
            idAreaAviso = idAreaAviso
        )
    }

    @Throws(WeatherException::class)
    override suspend fun getAlerts(location: LocationData): Collection<WeatherAlert>? = withContext(Dispatchers.IO) {
        val warnings = fetchWarnings()
        if (warnings.isEmpty()) return@withContext emptyList()

        val globalIdStr = updateLocationQuery(location)
        val globalId = globalIdStr.toIntOrNull()
        val ipmaLocs = (mLocationProvider as IPMALocationProvider).getIPMALocations()
        val locObj = ipmaLocs.find { it.globalIdLocal == globalId }
        val targetArea = locObj?.idAreaAviso ?: location.region ?: return@withContext emptyList()

        val zoneId = runCatching { ZoneId.of(location.tzLong) }.getOrElse { ZoneId.of("Europe/Lisbon") }
        val now = ZonedDateTime.now(zoneId)

        val alertList = ArrayList<WeatherAlert>()
        for (warn in warnings) {
            if (!warn.idAreaAviso.equals(targetArea, ignoreCase = true)) continue

            val level = warn.awarenessLevelID?.lowercase()?.trim() ?: continue
            if (level == "green") continue

            val start = runCatching {
                warn.startTime?.let { ZonedDateTime.of(LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId) }
            }.getOrNull()

            val end = runCatching {
                warn.endTime?.let { ZonedDateTime.of(LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId) }
            }.getOrNull()

            if (start != null && end != null) {
                if (now.isBefore(start) || now.isAfter(end)) {
                    continue
                }
            }

            alertList.add(
                WeatherAlert().apply {
                    title = warn.awarenessTypeName ?: "Aviso IPMA"
                    message = warn.text?.takeIf { it.isNotBlank() } ?: warn.awarenessTypeName
                    attribution = "IPMA"
                    severity = IPMAUtils.getAlertSeverity(level)
                    date = start ?: now
                    expiresDate = end ?: now.plusDays(1)
                }
            )
        }

        return@withContext alertList
    }

    private suspend fun fetchDailyForecast(globalId: Int): IPMADailyForecastResponse? {
        val url = String.format(DAILY_FORECAST_URL, globalId)
        return fetchJson(url, IPMADailyForecastResponse::class.java, maxAgeMinutes = 60)
    }

    private suspend fun fetchWeatherTypes(): Map<Int, IPMAWeatherType> {
        cachedWeatherTypes?.let { return it }

        val resp = fetchJson(WEATHER_TYPES_URL, IPMAWeatherTypeResponse::class.java, maxAgeMinutes = 1440)
        val map = resp?.data?.mapNotNull { item ->
            item.idWeatherType?.let { it to item }
        }?.toMap() ?: emptyMap()

        if (map.isNotEmpty()) {
            cachedWeatherTypes = map
        }
        return map
    }

    private suspend fun fetchWindSpeedClasses(): Map<Int, IPMAWindSpeedClass> {
        cachedWindSpeedClasses?.let { return it }

        val resp = fetchJson(WIND_SPEED_CLASSES_URL, IPMAWindSpeedClassResponse::class.java, maxAgeMinutes = 1440)
        val map = resp?.data?.mapNotNull { item ->
            val key = IPMAUtils.cleanAnyInt(item.classWindSpeed)
            key?.let { it to item }
        }?.toMap() ?: emptyMap()

        if (map.isNotEmpty()) {
            cachedWindSpeedClasses = map
        }
        return map
    }

    private suspend fun fetchPrecipClasses(): Map<Int, IPMAPrecipClass> {
        cachedPrecipClasses?.let { return it }

        val resp = fetchJson(PRECIP_CLASSES_URL, IPMAPrecipClassResponse::class.java, maxAgeMinutes = 1440)
        val map = resp?.data?.mapNotNull { item ->
            val key = IPMAUtils.cleanAnyInt(item.classPrecInt)
            key?.let { it to item }
        }?.toMap() ?: emptyMap()

        if (map.isNotEmpty()) {
            cachedPrecipClasses = map
        }
        return map
    }

    private suspend fun fetchStations(): List<IPMAStation> {
        cachedStations?.let { return it }

        var response: Response? = null
        try {
            checkRateLimit(getWeatherAPI())
            val request = Request.Builder()
                .url(STATIONS_URL)
                .cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build())
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getWeatherAPI(), response)

            val stream = response.getStream()
            val list = JSONParser.deserializer<List<IPMAStation>>(stream, com.squareup.moshi.Types.newParameterizedType(List::class.java, IPMAStation::class.java))
            val stations = list ?: emptyList()

            if (stations.isNotEmpty()) {
                cachedStations = stations
            }
            return stations
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMAWeatherProvider: error fetching stations")
            return emptyList()
        } finally {
            response?.closeQuietly()
        }
    }

    private suspend fun fetchObservations(): Map<String, Map<String, IPMAObservation>> {
        var response: Response? = null
        try {
            checkRateLimit(getWeatherAPI())
            val request = Request.Builder()
                .url(OBSERVATIONS_URL)
                .cacheControl(CacheControl.Builder().maxAge(30, TimeUnit.MINUTES).build())
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getWeatherAPI(), response)

            val stream = response.getStream()
            val type = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, IPMAObservation::class.java)
            )
            val obsMap = JSONParser.deserializer<Map<String, Map<String, IPMAObservation>>>(stream, type)
            return obsMap ?: emptyMap()
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMAWeatherProvider: error fetching observations")
            return emptyMap()
        } finally {
            response?.closeQuietly()
        }
    }

    private suspend fun fetchWarnings(): List<IPMAWarning> {
        var response: Response? = null
        try {
            checkRateLimit(getWeatherAPI())
            val request = Request.Builder()
                .url(WARNINGS_URL)
                .cacheControl(CacheControl.Builder().maxAge(15, TimeUnit.MINUTES).build())
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getWeatherAPI(), response)

            val stream = response.getStream()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, IPMAWarning::class.java)
            val list = JSONParser.deserializer<List<IPMAWarning>>(stream, type)
            return list ?: emptyList()
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMAWeatherProvider: error fetching warnings")
            return emptyList()
        } finally {
            response?.closeQuietly()
        }
    }

    private suspend fun fetchUVIndex(): List<IPMAUVItem> {
        var response: Response? = null
        try {
            checkRateLimit(getWeatherAPI())
            val request = Request.Builder()
                .url(UV_URL)
                .cacheControl(CacheControl.Builder().maxAge(60, TimeUnit.MINUTES).build())
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getWeatherAPI(), response)

            val stream = response.getStream()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, IPMAUVItem::class.java)
            val list = JSONParser.deserializer<List<IPMAUVItem>>(stream, type)
            return list ?: emptyList()
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMAWeatherProvider: error fetching UV index")
            return emptyList()
        } finally {
            response?.closeQuietly()
        }
    }

    private suspend fun <T> fetchJson(url: String, type: Class<T>, maxAgeMinutes: Int = 30): T? {
        var response: Response? = null
        try {
            checkRateLimit(getWeatherAPI())
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.Builder().maxAge(maxAgeMinutes, TimeUnit.MINUTES).build())
                .build()

            response = httpClient.newCall(request).await()
            checkForErrors(getWeatherAPI(), response)

            val stream = response.getStream()
            return JSONParser.deserializer(stream, type)
        } catch (ex: Exception) {
            Logger.writeLine(Log.ERROR, ex, "IPMAWeatherProvider: error fetching $url")
            if (ex is IOException) {
                throw WeatherException(ErrorStatus.NETWORKERROR, ex)
            }
            return null
        } finally {
            response?.closeQuietly()
        }
    }

    override fun getWeatherIcon(icon: String?): String {
        val code = icon?.toIntOrNull()
        if (code != null) {
            return IPMAUtils.getWeatherIcon(code)
        }
        return icon ?: WeatherIcons.NA
    }
}
