package com.thewizrd.weather_api

import com.thewizrd.shared_resources.icons.WeatherIcons
import com.thewizrd.shared_resources.locationdata.LocationData
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.weather_api.ipma.IPMADailyForecastResponse
import com.thewizrd.weather_api.ipma.IPMALocation
import com.thewizrd.weather_api.ipma.IPMAObservation
import com.thewizrd.weather_api.ipma.IPMAPrecipClass
import com.thewizrd.weather_api.ipma.IPMAStation
import com.thewizrd.weather_api.ipma.IPMAStationGeometry
import com.thewizrd.weather_api.ipma.IPMAStationProperties
import com.thewizrd.weather_api.ipma.IPMAUVItem
import com.thewizrd.weather_api.ipma.IPMAUtils
import com.thewizrd.weather_api.ipma.IPMAWarning
import com.thewizrd.weather_api.ipma.IPMAWeatherType
import com.thewizrd.weather_api.ipma.IPMAWindSpeedClass
import com.thewizrd.weather_api.ipma.createIPMAWeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class IPMAUnitTests {

    @Test
    fun testForecastJsonParsing() {
        val sampleJson = """
            {
                "owner": "IPMA",
                "country": "PT",
                "data": [
                    {
                        "precipitaProb": "0.0",
                        "tMin": "12.9",
                        "tMax": "20.1",
                        "predWindDir": "NW",
                        "idWeatherType": 5,
                        "classWindSpeed": 2,
                        "longitude": "-9.1286",
                        "forecastDate": "2026-10-20",
                        "latitude": "38.7660",
                        "classPrecInt": 1
                    }
                ],
                "globalIdLocal": 1110600,
                "dataUpdate": "2026-10-20T17:31:02"
            }
        """.trimIndent()

        val response = JSONParser.deserializer<IPMADailyForecastResponse>(sampleJson, IPMADailyForecastResponse::class.java)
        assertNotNull(response)
        assertEquals(1110600, response?.globalIdLocal)
        assertEquals(1, response?.data?.size)

        val item = response?.data?.get(0)
        assertNotNull(item)
        assertEquals("12.9", item?.tMin)
        assertEquals("20.1", item?.tMax)
        assertEquals("0.0", item?.precipitaProb)
        assertEquals(5, item?.idWeatherType)
        assertEquals("NW", item?.predWindDir)
        assertEquals("2026-10-20", item?.forecastDate)
    }

    @Test
    fun testWeatherCodeMapping() {
        assertEquals(WeatherIcons.DAY_SUNNY, IPMAUtils.getWeatherIcon(1))
        assertEquals(WeatherIcons.DAY_PARTLY_CLOUDY, IPMAUtils.getWeatherIcon(2))
        assertEquals(WeatherIcons.CLOUDY, IPMAUtils.getWeatherIcon(4))
        assertEquals(WeatherIcons.DAY_SHOWERS, IPMAUtils.getWeatherIcon(6))
        assertEquals(WeatherIcons.DAY_THUNDERSTORM, IPMAUtils.getWeatherIcon(19))
        assertEquals(WeatherIcons.DAY_SNOW, IPMAUtils.getWeatherIcon(18))
        assertEquals(WeatherIcons.NA, IPMAUtils.getWeatherIcon(-99))

        assertEquals("Céu limpo", IPMAUtils.getWeatherDescriptionPT(1))
        assertEquals("Clear sky", IPMAUtils.getWeatherDescriptionEN(1))
        assertEquals("Trovoada", IPMAUtils.getWeatherDescriptionPT(19))
        assertEquals("Thunderstorms", IPMAUtils.getWeatherDescriptionEN(19))
    }

    @Test
    fun testStringToNumberConversionDefensive() {
        assertEquals(12.9f, IPMAUtils.cleanStringFloat("12.9")!!, 0.001f)
        assertEquals(0.0f, IPMAUtils.cleanStringFloat("0.0")!!, 0.001f)
        assertNull(IPMAUtils.cleanStringFloat("malformed"))
        assertNull(IPMAUtils.cleanStringFloat(""))
        assertNull(IPMAUtils.cleanStringFloat(null))
    }

    @Test
    fun testSentinelValuesHandling() {
        assertNull(IPMAUtils.cleanDouble(-99.0))
        assertNull(IPMAUtils.cleanDouble(-99.00))
        assertNull(IPMAUtils.cleanFloat(-99.0f))
        assertNull(IPMAUtils.cleanStringFloat("-99.0"))
        assertNull(IPMAUtils.cleanStringFloat("-99"))
        assertNull(IPMAUtils.cleanAnyInt("-99"))
        assertNull(IPMAUtils.cleanAnyInt(-99))

        assertEquals(15.5, IPMAUtils.cleanDouble(15.5)!!, 0.001)
        assertEquals(2, IPMAUtils.cleanAnyInt("2")!!)
    }

    @Test
    fun testHaversineDistanceAndNearestStation() {
        // Lisboa coordinates: 38.7223, -9.1393
        // Station 1: Porto (~275 km)
        // Station 2: Lisboa EPPO (~5 km)
        val distPorto = IPMAUtils.calculateHaversineDistance(38.7223, -9.1393, 41.1579, -8.6291)
        val distLisboa = IPMAUtils.calculateHaversineDistance(38.7223, -9.1393, 38.7660, -9.1286)

        assertTrue(distLisboa < distPorto)
        assertTrue(distLisboa < 10.0) // Within 10 km
    }

    @Test
    fun testStationObservationsOrderedByTimestamp() {
        val locationData = LocationData().apply {
            name = "Lisboa"
            latitude = 38.7223
            longitude = -9.1393
            tzLong = "Europe/Lisbon"
        }

        val stations = listOf(
            IPMAStation(
                type = "Feature",
                geometry = IPMAStationGeometry("Point", listOf(-9.1286, 38.7660)),
                properties = IPMAStationProperties(1200535L, "Lisboa Geofísico")
            )
        )

        val observationsMap = mapOf(
            "2026-10-20T07:00" to mapOf(
                "1200535" to IPMAObservation(temperatura = 14.0, humidade = 80.0)
            ),
            "2026-10-20T08:00" to mapOf(
                "1200535" to IPMAObservation(temperatura = 16.5, humidade = 75.0)
            )
        )

        val weather = createIPMAWeatherData(
            locationData = locationData,
            dailyForecastResponse = null,
            weatherTypes = emptyMap(),
            windSpeedClasses = emptyMap(),
            precipClasses = emptyMap(),
            stations = stations,
            observationsMap = observationsMap,
            warnings = emptyList(),
            uvItems = emptyList(),
            idAreaAviso = "LSB"
        )

        // Must select the most recent valid observation (08:00)
        assertEquals(16.5f, weather.condition?.tempC!!, 0.001f)
        assertEquals(75, weather.atmosphere?.humidity)
    }

    @Test
    fun testMissingStationObservationHandling() {
        val locationData = LocationData().apply {
            name = "Lisboa"
            latitude = 38.7223
            longitude = -9.1393
            tzLong = "Europe/Lisbon"
        }

        val stations = listOf(
            IPMAStation(
                type = "Feature",
                geometry = IPMAStationGeometry("Point", listOf(-9.1286, 38.7660)),
                properties = IPMAStationProperties(1200535L, "Lisboa Geofísico")
            )
        )

        // Station observation has sentinel value -99.0
        val observationsMap = mapOf(
            "2026-10-20T08:00" to mapOf(
                "1200535" to IPMAObservation(temperatura = -99.0, humidade = -99.0)
            )
        )

        val weather = createIPMAWeatherData(
            locationData = locationData,
            dailyForecastResponse = null,
            weatherTypes = emptyMap(),
            windSpeedClasses = emptyMap(),
            precipClasses = emptyMap(),
            stations = stations,
            observationsMap = observationsMap,
            warnings = emptyList(),
            uvItems = emptyList(),
            idAreaAviso = "LSB"
        )

        // Temperature must be null (explicitly unavailable) and NOT synthesized from forecast
        assertNull(weather.condition?.tempC)
        assertNull(weather.condition?.tempF)
    }

    @Test
    fun testWarningFilteringByAreaAndActivePeriod() {
        val zoneId = ZoneId.of("Europe/Lisbon")
        val now = ZonedDateTime.now(zoneId)

        val startStr = now.minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val endStr = now.plusHours(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val expiredStr = now.minusHours(10).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        val warnings = listOf(
            // Active warning for LSB (Lisboa)
            IPMAWarning(
                idAreaAviso = "LSB",
                awarenessTypeName = "Vento",
                awarenessLevelID = "orange",
                startTime = startStr,
                endTime = endStr,
                text = "Vento forte com rajadas"
            ),
            // Expired warning for LSB
            IPMAWarning(
                idAreaAviso = "LSB",
                awarenessTypeName = "Precipitação",
                awarenessLevelID = "yellow",
                startTime = expiredStr,
                endTime = expiredStr,
                text = "Chuva forte"
            ),
            // Warning for different area (AVR - Aveiro)
            IPMAWarning(
                idAreaAviso = "AVR",
                awarenessTypeName = "Trovoada",
                awarenessLevelID = "red",
                startTime = startStr,
                endTime = endStr,
                text = "Trovoada frequente"
            ),
            // Green warning (should be ignored)
            IPMAWarning(
                idAreaAviso = "LSB",
                awarenessTypeName = "Neve",
                awarenessLevelID = "green",
                startTime = startStr,
                endTime = endStr,
                text = ""
            )
        )

        val locationData = LocationData().apply {
            name = "Lisboa"
            latitude = 38.7223
            longitude = -9.1393
            region = "LSB"
            tzLong = "Europe/Lisbon"
        }

        val weather = createIPMAWeatherData(
            locationData = locationData,
            dailyForecastResponse = null,
            weatherTypes = emptyMap(),
            windSpeedClasses = emptyMap(),
            precipClasses = emptyMap(),
            stations = emptyList(),
            observationsMap = emptyMap(),
            warnings = warnings,
            uvItems = emptyList(),
            idAreaAviso = "LSB"
        )

        val alerts = weather.weatherAlerts?.toList() ?: emptyList()
        assertEquals(1, alerts.size)
        assertEquals("Vento", alerts[0].title)
        assertEquals("Vento forte com rajadas", alerts[0].message)
    }

    @Test
    fun testUvRecordSelection() {
        val todayStr = ZonedDateTime.now(ZoneId.of("Europe/Lisbon")).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val uvItems = listOf(
            IPMAUVItem(globalIdLocal = 1110600, data = todayStr, iUv = "6.5"),
            IPMAUVItem(globalIdLocal = 1010500, data = todayStr, iUv = "3.2")
        )

        val locationData = LocationData().apply {
            name = "Lisboa"
            latitude = 38.7223
            longitude = -9.1393
            tzLong = "Europe/Lisbon"
        }

        val dailyForecast = IPMADailyForecastResponse(globalIdLocal = 1110600)

        val weather = createIPMAWeatherData(
            locationData = locationData,
            dailyForecastResponse = dailyForecast,
            weatherTypes = emptyMap(),
            windSpeedClasses = emptyMap(),
            precipClasses = emptyMap(),
            stations = emptyList(),
            observationsMap = emptyMap(),
            warnings = emptyList(),
            uvItems = uvItems,
            idAreaAviso = "LSB"
        )

        assertNotNull(weather.condition?.uv)
        assertEquals(6.5f, weather.condition?.uv?.index!!, 0.001f)
        assertEquals("Alto", IPMAUtils.getUVCategory(weather.condition?.uv?.index!!))
    }
}
