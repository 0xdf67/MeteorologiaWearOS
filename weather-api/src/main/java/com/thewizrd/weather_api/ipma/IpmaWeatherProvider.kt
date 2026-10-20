package com.thewizrd.weather_api.ipma

import android.util.Log
import com.ibm.icu.util.ULocale
import com.thewizrd.shared_resources.exceptions.ErrorStatus
import com.thewizrd.shared_resources.exceptions.WeatherException
import com.thewizrd.shared_resources.icons.WeatherIcons
import com.thewizrd.shared_resources.json.listType
import com.thewizrd.shared_resources.json.mapType
import com.thewizrd.shared_resources.locationdata.LocationData
import com.thewizrd.shared_resources.locationdata.LocationQuery
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.await
import com.thewizrd.shared_resources.okhttp3.OkHttp3Utils.getStream
import com.thewizrd.shared_resources.remoteconfig.remoteConfigService
import com.thewizrd.shared_resources.sharedDeps
import com.thewizrd.shared_resources.utils.ConversionMethods
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.LocaleUtils
import com.thewizrd.shared_resources.utils.LocationUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.ZoneIdCompat
import com.thewizrd.shared_resources.weatherdata.WeatherAPI
import com.thewizrd.shared_resources.weatherdata.model.Atmosphere
import com.thewizrd.shared_resources.weatherdata.model.Condition
import com.thewizrd.shared_resources.weatherdata.model.Forecast
import com.thewizrd.shared_resources.weatherdata.model.Precipitation
import com.thewizrd.shared_resources.weatherdata.model.UV
import com.thewizrd.shared_resources.weatherdata.model.Weather
import com.thewizrd.shared_resources.weatherdata.model.WeatherAlert
import com.thewizrd.shared_resources.weatherdata.model.Wind
import com.thewizrd.weather_api.extras.cacheRequestIfNeeded
import com.thewizrd.weather_api.nws.SolCalcAstroProvider
import com.thewizrd.weather_api.smc.SunMoonCalcProvider
import com.thewizrd.weather_api.utils.APIRequestUtils.checkForErrors
import com.thewizrd.weather_api.utils.APIRequestUtils.checkRateLimit
import com.thewizrd.weather_api.weatherModule
import com.thewizrd.weather_api.weatherdata.WeatherProviderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class IpmaWeatherProvider : WeatherProviderImpl() {
    companion object {
        private const val FORECAST_URL = "https://api.ipma.pt/open-data/forecast/meteorology/cities/daily/%s.json"
        private const val WEATHER_TYPES_URL = "https://api.ipma.pt/open-data/weather-type-classe.json"
        private const val STATIONS_URL = "https://api.ipma.pt/open-data/observation/meteorology/stations/stations.json"
        private const val OBSERVATIONS_URL = "https://api.ipma.pt/open-data/observation/meteorology/stations/observations.json"
        private const val WARNINGS_URL = "https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"
        private const val UV_URL = "https://api.ipma.pt/open-data/forecast/meteorology/uv/uv.json"
    }

    init {
        mLocationProvider = runCatching {
            weatherModule.locationProviderFactory.getLocationProvider(
                remoteConfigService.getLocationProvider(getWeatherAPI())
            )
        }.getOrElse {
            IpmaLocationProvider()
        }
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

    override suspend fun isKeyValid(key: String?): Boolean {
        return false
    }

    override fun getAPIKey(): String? {
        return null
    }

    @Throws(WeatherException::class)
    override suspend fun getWeatherData(location: LocationData): Weather = withContext(Dispatchers.IO) {
        val globalIdLocal = location.query
        if (globalIdLocal.isNullOrBlank()) {
            throw WeatherException(ErrorStatus.INVALIDURL)
        }

        val client = sharedDeps.httpClient
        val uLocale = ULocale.forLocale(LocaleUtils.getLocale())
        val isPortuguese = uLocale.language.equals("pt", ignoreCase = true)

        var weather: Weather? = null
        var wEx: WeatherException? = null

        try {
            checkRateLimit()

            // 1. Fetch Forecast
            val forecastReq = Request.Builder()
                .cacheRequestIfNeeded(false, 2, TimeUnit.HOURS)
                .url(String.format(Locale.ROOT, FORECAST_URL, globalIdLocal))
                .build()
            val forecastResp = client.newCall(forecastReq).await()
            checkForErrors(forecastResp)
            val forecastStream = forecastResp.getStream()
            val forecastRoot = JSONParser.deserializer<DailyForecastResponse>(forecastStream, DailyForecastResponse::class.java)
            forecastStream.closeQuietly()

            // 2. Fetch Weather Types
            val wtReq = Request.Builder()
                .cacheRequestIfNeeded(false, 14, TimeUnit.DAYS)
                .url(WEATHER_TYPES_URL)
                .build()
            val wtResp = client.newCall(wtReq).await()
            val wtStream = wtResp.getStream()
            val wtRoot = JSONParser.deserializer<WeatherTypeResponse>(wtStream, WeatherTypeResponse::class.java)
            wtStream.closeQuietly()

            val weatherTypesMap = wtRoot?.data?.associateBy { it.idWeatherType }

            // 3. Fetch Observations & Stations for current conditions
            var currentTemp: Double? = null
            var currentHumidity: Double? = null
            var currentPressure: Double? = null
            var currentWindSpeed: Double? = null
            var currentWindDir: Int? = null
            var currentPrecip: Double? = null
            var updateTime: String? = forecastRoot?.dataUpdate

            try {
                val stReq = Request.Builder().url(STATIONS_URL).build()
                val stResp = client.newCall(stReq).await()
                val stStream = stResp.getStream()
                val stations = JSONParser.deserializer<List<StationItem>>(stStream, listType<StationItem>())
                stStream.closeQuietly()

                if (stations != null && stations.isNotEmpty()) {
                    // Find closest station
                    var closestStation: StationItem? = null
                    var minDist = Double.MAX_VALUE
                    for (st in stations) {
                        if (st.latitude != null && st.longitude != null) {
                            val dist = abs(ConversionMethods.calculateHaversine(location.latitude, location.longitude, st.latitude, st.longitude))
                            if (dist < minDist) {
                                minDist = dist
                                closestStation = st
                            }
                        }
                    }

                    if (closestStation?.idEstacaoMeteo != null) {
                        val obsReq = Request.Builder().url(OBSERVATIONS_URL).build()
                        val obsResp = client.newCall(obsReq).await()
                        val obsStream = obsResp.getStream()
                        // Observations map: timestamp -> (stationId -> StationObservation)
                        val obsMap = JSONParser.deserializer<Map<String, Map<String, StationObservation>>>(
                            obsStream,
                            mapType<String, Map<String, StationObservation>>()
                        )
                        obsStream.closeQuietly()

                        if (obsMap != null) {
                            // Sort timestamps descending to get most recent
                            val sortedTimestamps = obsMap.keys.sortedDescending()
                            val stationIdStr = closestStation.idEstacaoMeteo.toString()
                            for (ts in sortedTimestamps) {
                                val stationObs = obsMap[ts]?.get(stationIdStr)
                                if (stationObs != null) {
                                    val t = stationObs.temperatura
                                    if (t != null && t > -90.0) {
                                        currentTemp = t
                                        currentHumidity = stationObs.humidade?.takeIf { it > -90.0 }
                                        currentPressure = stationObs.pressao?.takeIf { it > -90.0 }
                                        currentWindSpeed = stationObs.intensidadeVentoKM?.takeIf { it > -90.0 }
                                        currentWindDir = stationObs.idDireccVento?.takeIf { it >= 0 }
                                        currentPrecip = stationObs.precAcumulada?.takeIf { it > -90.0 }
                                        updateTime = ts
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Logger.writeLine(Log.WARN, ex, "IpmaWeatherProvider: failed to fetch current observations")
            }

            // 4. Fetch UV Index
            var uvIndex: Double? = null
            try {
                val uvReq = Request.Builder().url(UV_URL).build()
                val uvResp = client.newCall(uvReq).await()
                val uvStream = uvResp.getStream()
                val uvRoot = JSONParser.deserializer<UvResponse>(uvStream, UvResponse::class.java)
                uvStream.closeQuietly()

                val todayStr = LocalDate.now().toString()
                val uvItem = uvRoot?.data?.find { it.globalIdLocal == forecastRoot?.globalIdLocal && it.data == todayStr }
                uvIndex = uvItem?.iUv
            } catch (ex: Exception) {
                Logger.writeLine(Log.WARN, ex, "IpmaWeatherProvider: failed to fetch UV index")
            }

            // 5. Fetch Warnings
            val alerts = mutableListOf<WeatherAlert>()
            try {
                val warnReq = Request.Builder().url(WARNINGS_URL).build()
                val warnResp = client.newCall(warnReq).await()
                val warnStream = warnResp.getStream()
                val warnList = JSONParser.deserializer<List<WarningItem>>(warnStream, listType<WarningItem>())
                warnStream.closeQuietly()

                if (warnList != null) {
                    val areaAviso = location.region
                    val now = Instant.now()
                    for (w in warnList) {
                        if (w.idAreaAviso.isNullOrBlank() || w.idAreaAviso.equals(areaAviso, ignoreCase = true)) {
                            val start = runCatching { Instant.parse(w.startTime) }.getOrNull() ?: now
                            val end = runCatching { Instant.parse(w.endTime) }.getOrNull() ?: now.plusSeconds(3600)
                            if (now.isBefore(end)) {
                                val severity = when (w.awarenessLevelID?.lowercase()) {
                                    "vermelho", "red" -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.SEVERE
                                    "laranja", "orange" -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.MODERATE
                                    else -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.MINOR
                                }
                                alerts.add(
                                    WeatherAlert().apply {
                                        title = w.awarenessTypeName ?: "Weather Warning"
                                        message = w.text ?: ""
                                        type = com.thewizrd.shared_resources.weatherdata.model.WeatherAlertType.SEVERE_WEATHER
                                        this.severity = severity
                                        date = start.atZone(ZoneOffset.UTC)
                                        expiresDate = end.atZone(ZoneOffset.UTC)
                                    }
                                )
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                Logger.writeLine(Log.WARN, ex, "IpmaWeatherProvider: failed to fetch warnings")
            }

            // Build Weather model from forecastRoot
            weather = Weather().apply {
                this.weatherAPI = getWeatherAPI()
                this.location = updateWeatherDataLocation(location, null)

                updateTime = forecastRoot?.dataUpdate
                if (!updateTime.isNullOrBlank()) {
                    this.updateTime = ZonedDateTime.parse(updateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC))
                } else {
                    this.updateTime = ZonedDateTime.now(ZoneOffset.UTC)
                }

                val forecastList = forecastRoot?.data
                if (!forecastList.isNullOrEmpty()) {
                    val todayForecast = forecastList[0]
                    val wt = todayForecast.idWeatherType?.let { weatherTypesMap?.get(it) }
                    val desc = if (isPortuguese) wt?.descWeatherTypePT else wt?.descWeatherTypeEN

                    condition = Condition().apply {
                        temp = currentTemp?.let { Math.round(it).toString() } ?: todayForecast.tMax?.let { Math.round(it).toString() }
                        high = todayForecast.tMax?.let { Math.round(it).toString() }
                        low = todayForecast.tMin?.let { Math.round(it).toString() }
                        condition = desc ?: "N/A"
                        icon = getWeatherIcon(todayForecast.idWeatherType)
                    }

                    atmosphere = Atmosphere().apply {
                        humidity = currentHumidity?.let { Math.round(it).toInt() }
                        pressureMb = currentPressure
                        uvIndex?.let { uv = UV(it) }
                    }

                    wind = Wind().apply {
                        speedMph = currentWindSpeed?.let { ConversionMethods.kmhToMph(it) }
                        speedKph = currentWindSpeed
                        direction = currentWindDir ?: 0
                    }

                    forecast = forecastList.map { item ->
                        val itemWt = item.idWeatherType?.let { weatherTypesMap?.get(it) }
                        val itemDesc = if (isPortuguese) itemWt?.descWeatherTypePT else itemWt?.descWeatherTypeEN
                        Forecast().apply {
                            date = runCatching { LocalDate.parse(item.forecastDate) }.getOrNull()?.atStartOfDay(ZoneOffset.UTC) ?: ZonedDateTime.now(ZoneOffset.UTC)
                            high = item.tMax?.let { Math.round(it).toString() }
                            low = item.tMin?.let { Math.round(it).toString() }
                            condition = itemDesc ?: "N/A"
                            icon = getWeatherIcon(item.idWeatherType)
                            extras = com.thewizrd.shared_resources.weatherdata.model.ForecastExtras().apply {
                                pop = item.precipitaProb?.toInt()
                                windKph = null
                            }
                        }
                    }
                }

                weatherAlerts = alerts.takeIf { it.isNotEmpty() }

                val tzId = ZoneIdCompat.of("Europe/Lisbon")
                val astro = runCatching {
                    SunMoonCalcProvider().getAstronomyData(location, ZonedDateTime.now(tzId))
                }.getOrNull() ?: SolCalcAstroProvider().getAstronomyData(location, ZonedDateTime.now(tzId))
                astronomy = astro
            }
        } catch (ex: Exception) {
            if (ex is IOException) {
                wEx = WeatherException(ErrorStatus.NETWORKERROR, ex)
            } else if (ex is WeatherException) {
                wEx = ex
            } else {
                wEx = WeatherException(ErrorStatus.NOWEATHER, ex)
            }
            Logger.writeLine(Log.ERROR, ex, "IpmaWeatherProvider: error getting weather data")
        }

        if (wEx != null) throw wEx
        return@withContext weather ?: throw WeatherException(ErrorStatus.NOWEATHER)
    }

    override fun getWeatherIcon(icon: String?): String {
        return icon?.toIntOrNull()?.let { getWeatherIcon(it) } ?: WeatherIcons.NA
    }

    private fun getWeatherIcon(idWeatherType: Int?): String {
        return when (idWeatherType) {
            1 -> WeatherIcons.DAY_SUNNY
            2, 3 -> WeatherIcons.DAY_PARTLY_CLOUDY
            4, 5 -> WeatherIcons.CLOUDY
            in 6..9 -> WeatherIcons.SHOWERS
            in 10..12 -> WeatherIcons.RAIN_MIX
            in 13..15 -> WeatherIcons.SNOW
            in 16..18 -> WeatherIcons.THUNDERSTORM
            in 19..21 -> WeatherIcons.FOG
            22 -> WeatherIcons.SNOWFLAKE_COLD
            23, 24 -> WeatherIcons.SNOW
            25 -> WeatherIcons.RAIN_MIX
            26 -> WeatherIcons.STRONG_WIND
            27, 28 -> WeatherIcons.RAIN
            else -> WeatherIcons.NA
        }
    }
}
