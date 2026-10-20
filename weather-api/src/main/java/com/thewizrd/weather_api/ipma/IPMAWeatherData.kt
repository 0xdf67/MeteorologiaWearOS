package com.thewizrd.weather_api.ipma

import com.thewizrd.shared_resources.icons.WeatherIcons
import com.thewizrd.shared_resources.locationdata.LocationData
import com.thewizrd.shared_resources.utils.ConversionMethods
import com.thewizrd.shared_resources.utils.LocaleUtils
import com.thewizrd.shared_resources.utils.getBeaufortScale
import com.thewizrd.shared_resources.utils.getFeelsLikeTemp
import com.thewizrd.shared_resources.weatherdata.WeatherAPI
import com.thewizrd.shared_resources.weatherdata.model.Atmosphere
import com.thewizrd.shared_resources.weatherdata.model.Beaufort
import com.thewizrd.shared_resources.weatherdata.model.Condition
import com.thewizrd.shared_resources.weatherdata.model.Forecast
import com.thewizrd.shared_resources.weatherdata.model.ForecastExtras
import com.thewizrd.shared_resources.weatherdata.model.Location
import com.thewizrd.shared_resources.weatherdata.model.Precipitation
import com.thewizrd.shared_resources.weatherdata.model.UV
import com.thewizrd.shared_resources.weatherdata.model.Weather
import com.thewizrd.shared_resources.weatherdata.model.WeatherAlert
import com.thewizrd.weather_api.smc.SunMoonCalcProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun createIPMAWeatherData(
    locationData: LocationData,
    dailyForecastResponse: IPMADailyForecastResponse?,
    weatherTypes: Map<Int, IPMAWeatherType>,
    windSpeedClasses: Map<Int, IPMAWindSpeedClass>,
    precipClasses: Map<Int, IPMAPrecipClass>,
    stations: List<IPMAStation>,
    observationsMap: Map<String, Map<String, IPMAObservation>>,
    warnings: List<IPMAWarning>,
    uvItems: List<IPMAUVItem>,
    idAreaAviso: String?
): Weather {
    return Weather().apply {
        val zoneId = runCatching { ZoneId.of(locationData.tzLong) }.getOrElse { ZoneId.of("Europe/Lisbon") }
        val now = ZonedDateTime.now(zoneId)

        location = Location().apply {
            name = locationData.name
            latitude = locationData.latitude.toFloat()
            longitude = locationData.longitude.toFloat()
            tzLong = locationData.tzLong ?: zoneId.id
        }

        updateTime = now
        source = WeatherAPI.IPMA
        ttl = 120

        val isPT = LocaleUtils.getLocale().language.equals("pt", ignoreCase = true)

        // Astronomy
        runCatching {
            astronomy = kotlinx.coroutines.runBlocking {
                SunMoonCalcProvider().getAstronomyData(locationData, now)
            }
        }

        // Daily Forecasts
        val dailyList = dailyForecastResponse?.data ?: emptyList()
        val forecastList = ArrayList<Forecast>()

        for (daily in dailyList) {
            val dateStr = daily.forecastDate ?: continue
            val date = runCatching { LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() ?: continue

            val fcast = Forecast().apply {
                this.date = date.atStartOfDay()
                val highC = IPMAUtils.cleanStringFloat(daily.tMax)
                if (highC != null) {
                    this.highC = highC
                    this.highF = ConversionMethods.CtoF(highC)
                }

                val lowC = IPMAUtils.cleanStringFloat(daily.tMin)
                if (lowC != null) {
                    this.lowC = lowC
                    this.lowF = ConversionMethods.CtoF(lowC)
                }

                val weatherType = daily.idWeatherType
                this.icon = IPMAUtils.getWeatherIcon(weatherType)

                val typeObj = weatherType?.let { weatherTypes[it] }
                this.condition = if (isPT) {
                    typeObj?.descWeatherTypePT ?: IPMAUtils.getWeatherDescriptionPT(weatherType)
                } else {
                    typeObj?.descWeatherTypeEN ?: IPMAUtils.getWeatherDescriptionEN(weatherType)
                }

                this.extras = ForecastExtras().apply {
                    pop = IPMAUtils.cleanStringFloat(daily.precipitaProb)?.roundToInt()
                    windDegrees = IPMAUtils.getWindDirectionDegrees(daily.predWindDir)
                }
            }
            forecastList.add(fcast)
        }
        this.forecast = forecastList

        // Find best station observation
        var bestObs: IPMAObservation? = null
        var bestStationName: String? = null
        var bestObsTimestamp: ZonedDateTime? = null

        if (stations.isNotEmpty() && observationsMap.isNotEmpty()) {
            // Sort stations by distance to target location
            val sortedStations = stations.mapNotNull { st ->
                val coords = st.geometry?.coordinates
                if (coords != null && coords.size >= 2) {
                    val lon = coords[0]
                    val lat = coords[1]
                    val dist = IPMAUtils.calculateHaversineDistance(locationData.latitude, locationData.longitude, lat, lon)
                    Triple(st, dist, coords)
                } else null
            }.sortedBy { it.second }

            // Sort observation timestamps descending (newest first)
            val sortedTimestamps = observationsMap.keys.mapNotNull { tsStr ->
                runCatching {
                    val ldt = LocalDateTime.parse(tsStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ZonedDateTime.of(ldt, zoneId) to tsStr
                }.getOrNull()
            }.sortedByDescending { it.first }

            // Find closest station that has a valid observation temperature
            stationLoop@ for (stTriple in sortedStations) {
                val st = stTriple.first
                val stEstacaoId = st.properties?.idEstacao?.toString() ?: continue
                val stName = st.properties?.localEstacao ?: "Station $stEstacaoId"

                for (tsPair in sortedTimestamps) {
                    val obsMap = observationsMap[tsPair.second] ?: continue
                    val obs = obsMap[stEstacaoId] ?: continue

                    val temp = IPMAUtils.cleanDouble(obs.temperatura)
                    if (temp != null) {
                        bestObs = obs
                        bestStationName = stName
                        bestObsTimestamp = tsPair.first
                        break@stationLoop
                    }
                }
            }
        }

        // Today's forecast for fallback condition text / icon
        val todayForecast = forecastList.find { it.date.toLocalDate().isEqual(now.toLocalDate()) }
            ?: forecastList.firstOrNull()

        // Create Condition
        condition = Condition().apply {
            val obsTempC = IPMAUtils.cleanDouble(bestObs?.temperatura)?.toFloat()
            if (obsTempC != null) {
                tempC = obsTempC
                tempF = ConversionMethods.CtoF(obsTempC)
            } else {
                // Temperature is explicitly unavailable if no valid station observation exists
                tempC = null
                tempF = null
            }

            highC = todayForecast?.highC
            highF = todayForecast?.highF
            lowC = todayForecast?.lowC
            lowF = todayForecast?.lowF

            icon = todayForecast?.icon ?: WeatherIcons.NA
            weather = todayForecast?.condition

            val windK = IPMAUtils.cleanDouble(bestObs?.intensidadeVentoKM)?.toFloat()
            if (windK != null) {
                windKph = windK
                windMph = ConversionMethods.kphTomph(windK)
                beaufort = Beaufort(getBeaufortScale(windMph!!.roundToInt()))
            }

            windDegrees = IPMAUtils.getWindDirectionDegrees(bestObs?.idDireccVento)

            val hum = IPMAUtils.cleanDouble(bestObs?.humidade)?.roundToInt()
            if (tempF != null && windMph != null && hum != null) {
                feelslikeF = getFeelsLikeTemp(tempF!!, windMph!!, hum)
                feelslikeC = ConversionMethods.FtoC(feelslikeF!!)
            }

            observationTime = bestObsTimestamp ?: now
        }

        // Atmosphere
        atmosphere = Atmosphere().apply {
            humidity = IPMAUtils.cleanDouble(bestObs?.humidade)?.roundToInt()

            val pressMb = IPMAUtils.cleanDouble(bestObs?.pressao)?.toFloat()
            if (pressMb != null) {
                pressureMb = pressMb
                pressureIn = ConversionMethods.mbToInHg(pressMb)
            }

            val tempCVal = condition?.tempC
            if (tempCVal != null && humidity != null) {
                val dewC = tempCVal - ((100 - humidity!!) / 5.0f)
                dewpointC = dewC
                dewpointF = ConversionMethods.CtoF(dewC)
            }
        }

        // Precipitation
        precipitation = Precipitation().apply {
            pop = todayForecast?.extras?.pop
            val rainMm = IPMAUtils.cleanDouble(bestObs?.precAcumulada)?.toFloat()
            if (rainMm != null) {
                qpfRainMm = rainMm
                qpfRainIn = ConversionMethods.mmToIn(rainMm)
            }
        }

        // UV Index
        val todayStr = now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val globalId = dailyForecastResponse?.globalIdLocal ?: 0
        val uvItem = uvItems.find {
            (it.globalIdLocal == globalId || it.globalIdLocal == 0) && it.data == todayStr
        } ?: uvItems.find { it.data == todayStr }

        val uvVal = uvItem?.iUv?.let { IPMAUtils.cleanStringFloat(it) }
        if (uvVal != null) {
            condition!!.uv = UV(uvVal)
        }

        // Apply UV index to daily forecast extras if available
        for (fcast in forecastList) {
            val fdateStr = fcast.date.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val fUvItem = uvItems.find {
                (it.globalIdLocal == globalId || it.globalIdLocal == 0) && it.data == fdateStr
            }
            val fUvVal = fUvItem?.iUv?.let { IPMAUtils.cleanStringFloat(it) }
            if (fUvVal != null) {
                if (fcast.extras == null) fcast.extras = ForecastExtras()
                fcast.extras.uvIndex = fUvVal
            }
        }

        // Weather Alerts
        val targetArea = idAreaAviso ?: locationData.region
        val alertList = ArrayList<WeatherAlert>()

        if (!targetArea.isNullOrBlank()) {
            for (warn in warnings) {
                if (!warn.idAreaAviso.equals(targetArea, ignoreCase = true)) continue

                val level = warn.awarenessLevelID?.lowercase()?.trim() ?: continue
                if (level == "green") continue // Green is no warning

                val start = runCatching {
                    warn.startTime?.let { ZonedDateTime.of(LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId) }
                }.getOrNull()

                val end = runCatching {
                    warn.endTime?.let { ZonedDateTime.of(LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId) }
                }.getOrNull()

                // Check active warning period
                if (start != null && end != null) {
                    if (now.isBefore(start) || now.isAfter(end)) {
                        continue // Expired or future warning
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
        }
        this.weatherAlerts = alertList
    }
}
