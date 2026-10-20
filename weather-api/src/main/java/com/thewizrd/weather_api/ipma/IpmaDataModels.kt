package com.thewizrd.weather_api.ipma

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IpmaDistrictItem(
    @Json(name = "idAreaAviso") val idAreaAviso: String?,
    @Json(name = "idCidades") val idCidades: Int?,
    @Json(name = "globalIdLocal") val globalIdLocal: Int?,
    @Json(name = "latitude") val latitude: String?,
    @Json(name = "local") val local: String?,
    @Json(name = "longitude") val longitude: String?,
    @Json(name = "idRegiao") val idRegiao: Int?
)

@JsonClass(generateAdapter = true)
data class DailyForecastResponse(
    @Json(name = "owner") val owner: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "dataUpdate") val dataUpdate: String?,
    @Json(name = "globalIdLocal") val globalIdLocal: Int?,
    @Json(name = "data") val data: List<DailyForecastItem>?
)

@JsonClass(generateAdapter = true)
data class DailyForecastItem(
    @Json(name = "forecastDate") val forecastDate: String?,
    @Json(name = "tMin") val tMin: Double?,
    @Json(name = "tMax") val tMax: Double?,
    @Json(name = "precipitaProb") val precipitaProb: Double?,
    @Json(name = "idWeatherType") val idWeatherType: Int?,
    @Json(name = "predWindDir") val predWindDir: String?,
    @Json(name = "classWindSpeed") val classWindSpeed: Int?,
    @Json(name = "classPrecInt") val classPrecInt: Int?
)

@JsonClass(generateAdapter = true)
data class WeatherTypeResponse(
    @Json(name = "owner") val owner: String?,
    @Json(name = "data") val data: List<WeatherTypeItem>?
)

@JsonClass(generateAdapter = true)
data class WeatherTypeItem(
    @Json(name = "idWeatherType") val idWeatherType: Int?,
    @Json(name = "descWeatherTypePT") val descWeatherTypePT: String?,
    @Json(name = "descWeatherTypeEN") val descWeatherTypeEN: String?
)

@JsonClass(generateAdapter = true)
data class StationItem(
    @Json(name = "idEstacaoMeteo") val idEstacaoMeteo: Int?,
    @Json(name = "latitude") val latitude: Double?,
    @Json(name = "longitude") val longitude: Double?,
    @Json(name = "localEstacao") val localEstacao: String?
)

@JsonClass(generateAdapter = true)
data class StationObservation(
    @Json(name = "temperatura") val temperatura: Double?,
    @Json(name = "humidade") val humidade: Double?,
    @Json(name = "intensidadeVentoKM") val intensidadeVentoKM: Double?,
    @Json(name = "intensidadeVento") val intensidadeVento: Int?,
    @Json(name = "idDireccVento") val idDireccVento: Int?,
    @Json(name = "precAcumulada") val precAcumulada: Double?,
    @Json(name = "pressao") val pressao: Double?
)

@JsonClass(generateAdapter = true)
data class WarningItem(
    @Json(name = "idAreaAviso") val idAreaAviso: String?,
    @Json(name = "awarenessTypeName") val awarenessTypeName: String?,
    @Json(name = "awarenessLevelID") val awarenessLevelID: String?,
    @Json(name = "startTime") val startTime: String?,
    @Json(name = "endTime") val endTime: String?,
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class UvResponse(
    @Json(name = "owner") val owner: String?,
    @Json(name = "data") val data: List<UvItem>?
)

@JsonClass(generateAdapter = true)
data class UvItem(
    @Json(name = "globalIdLocal") val globalIdLocal: Int?,
    @Json(name = "data") val data: String?,
    @Json(name = "iUv") val iUv: Double?,
    @Json(name = "intervaloHora") val intervaloHora: String?,
    @Json(name = "idPeriodo") val idPeriodo: Int?
)
