package com.thewizrd.weather_api.ipma

import com.thewizrd.shared_resources.icons.WeatherIcons
import com.thewizrd.shared_resources.utils.WeatherUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object IPMAUtils {
    /**
     * Haversine distance in kilometers between two lat/lon points.
     */
    fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Clean numeric Double measurement treating -99.0 and NaN as unavailable.
     */
    fun cleanDouble(value: Double?): Double? {
        if (value == null || value.isNaN() || value == -99.0 || value == -99.00 || value == -99f.toDouble()) {
            return null
        }
        return value
    }

    /**
     * Clean numeric Float measurement treating -99.0 and NaN as unavailable.
     */
    fun cleanFloat(value: Float?): Float? {
        if (value == null || value.isNaN() || value == -99.0f || value == -99f) {
            return null
        }
        return value
    }

    /**
     * Clean numeric measurement provided as String.
     */
    fun cleanStringFloat(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        val parsed = value.trim().toFloatOrNull() ?: return null
        return cleanFloat(parsed)
    }

    /**
     * Safely parse integer or string representation of class integers.
     */
    fun cleanAnyInt(value: Any?): Int? {
        if (value == null) return null
        val intVal = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        } ?: return null

        if (intVal == -99) return null
        return intVal
    }

    /**
     * Map IPMA weather type code to WeatherIcons constant.
     */
    fun getWeatherIcon(idWeatherType: Int?): String {
        return when (idWeatherType) {
            1 -> WeatherIcons.DAY_SUNNY
            2 -> WeatherIcons.DAY_PARTLY_CLOUDY
            3 -> WeatherIcons.DAY_SUNNY_OVERCAST
            4 -> WeatherIcons.CLOUDY
            5 -> WeatherIcons.DAY_CLOUDY
            6 -> WeatherIcons.DAY_SHOWERS
            7 -> WeatherIcons.DAY_SPRINKLE
            8 -> WeatherIcons.DAY_RAIN
            9 -> WeatherIcons.DAY_RAIN
            10 -> WeatherIcons.DAY_SPRINKLE
            11 -> WeatherIcons.DAY_RAIN_WIND
            12 -> WeatherIcons.DAY_RAIN
            13 -> WeatherIcons.DAY_SPRINKLE
            14 -> WeatherIcons.DAY_RAIN_WIND
            15 -> WeatherIcons.DAY_SPRINKLE
            16 -> WeatherIcons.DAY_FOG
            17 -> WeatherIcons.DAY_FOG
            18 -> WeatherIcons.DAY_SNOW
            19 -> WeatherIcons.DAY_THUNDERSTORM
            20 -> WeatherIcons.DAY_STORM_SHOWERS
            21 -> WeatherIcons.DAY_HAIL
            22 -> WeatherIcons.SNOWFLAKE_COLD
            23 -> WeatherIcons.DAY_RAIN_MIX
            24 -> WeatherIcons.DAY_CLOUDY
            25 -> WeatherIcons.DAY_PARTLY_CLOUDY
            26 -> WeatherIcons.OVERCAST
            27 -> WeatherIcons.DAY_SHOWERS
            28 -> WeatherIcons.DAY_FOG
            29 -> WeatherIcons.DAY_PARTLY_CLOUDY
            30 -> WeatherIcons.DAY_SHOWERS
            else -> WeatherIcons.NA
        }
    }

    /**
     * Fallback Portuguese weather condition descriptions by weather type ID.
     */
    fun getWeatherDescriptionPT(idWeatherType: Int?): String? {
        return when (idWeatherType) {
            1 -> "Céu limpo"
            2 -> "Céu pouco nublado"
            3 -> "Céu parcialmente nublado"
            4 -> "Céu muito nublado ou encoberto"
            5 -> "Céu nublado por nuvens altas"
            6 -> "Aguaceiros"
            7 -> "Aguaceiros fracos"
            8 -> "Aguaceiros fortes"
            9 -> "Chuva ou chuvisco"
            10 -> "Chuva fraca"
            11 -> "Chuva forte"
            12 -> "Chuva intermitente"
            13 -> "Chuva fraca intermitente"
            14 -> "Chuva forte intermitente"
            15 -> "Chuvisco"
            16 -> "Neblina"
            17 -> "Nevoeiro ou nevoa"
            18 -> "Neve"
            19 -> "Trovoada"
            20 -> "Aguaceiros e trovoada"
            21 -> "Granizo"
            22 -> "Geada"
            23 -> "Aguaceiros de neve"
            24 -> "Nuvens de desenvolvimento convector"
            25 -> "Céu muito nublado com abertas"
            26 -> "Céu muito nublado"
            27 -> "Céu muito nublado com aguaceiros"
            28 -> "Nevoeiro a dissipar"
            29 -> "Céu limpo com períodos de muito nublado"
            30 -> "Céu muito nublado com aguaceiros"
            else -> null
        }
    }

    /**
     * Fallback English weather condition descriptions by weather type ID.
     */
    fun getWeatherDescriptionEN(idWeatherType: Int?): String? {
        return when (idWeatherType) {
            1 -> "Clear sky"
            2 -> "Partly cloudy"
            3 -> "Sunny intervals"
            4 -> "Cloudy"
            5 -> "Cloudy (high clouds)"
            6 -> "Showers"
            7 -> "Light showers"
            8 -> "Heavy showers"
            9 -> "Rain / drizzle"
            10 -> "Light rain"
            11 -> "Heavy rain"
            12 -> "Intermittent rain"
            13 -> "Intermittent light rain"
            14 -> "Intermittent heavy rain"
            15 -> "Drizzle"
            16 -> "Mist"
            17 -> "Fog"
            18 -> "Snow"
            19 -> "Thunderstorms"
            20 -> "Showers and thunderstorms"
            21 -> "Hail"
            22 -> "Frost"
            23 -> "Snow showers"
            24 -> "Convective clouds"
            25 -> "Cloudy with sunny intervals"
            26 -> "Very cloudy"
            27 -> "Cloudy with showers"
            28 -> "Fog clearing"
            29 -> "Clear with cloudy periods"
            30 -> "Very cloudy with showers"
            else -> null
        }
    }

    /**
     * Convert IPMA wind direction ID or wind direction string to wind degrees.
     */
    fun getWindDirectionDegrees(idDireccVento: Int?): Int? {
        return when (idDireccVento) {
            1 -> 0    // N
            2 -> 45   // NE
            3 -> 90   // E
            4 -> 135  // SE
            5 -> 180  // S
            6 -> 225  // SW
            7 -> 270  // W
            8 -> 315  // NW
            else -> null
        }
    }

    fun getWindDirectionDegrees(predWindDir: String?): Int? {
        if (predWindDir.isNullOrBlank()) return null
        return when (predWindDir.uppercase().trim()) {
            "N" -> 0
            "NNE", "NE" -> 45
            "ENE", "E" -> 90
            "ESE", "SE" -> 135
            "SSE", "S" -> 180
            "SSW", "SW" -> 225
            "WSW", "W" -> 270
            "NW", "NNW" -> 315
            else -> null
        }
    }

    /**
     * Map UV index value to official category text.
     */
    fun getUVCategory(uvIndex: Float): String {
        return when {
            uvIndex < 3f -> "Baixo"
            uvIndex < 6f -> "Moderado"
            uvIndex < 8f -> "Alto"
            uvIndex < 11f -> "Muito Alto"
            else -> "Extremo"
        }
    }

    /**
     * Map awarenessLevelID to WeatherAlertSeverity.
     */
    fun getAlertSeverity(awarenessLevelID: String?): com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity {
        return when (awarenessLevelID?.lowercase()?.trim()) {
            "yellow" -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.MODERATE
            "orange" -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.SEVERE
            "red" -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.EXTREME
            else -> com.thewizrd.shared_resources.weatherdata.model.WeatherAlertSeverity.MINOR
        }
    }
}
