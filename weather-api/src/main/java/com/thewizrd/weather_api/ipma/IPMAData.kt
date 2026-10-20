package com.thewizrd.weather_api.ipma

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IPMALocation(
    @field:Json(name = "idRegiao") var idRegiao: Int? = null,
    @field:Json(name = "idAreaAviso") var idAreaAviso: String? = null,
    @field:Json(name = "idConcelho") var idConcelho: Int? = null,
    @field:Json(name = "globalIdLocal") var globalIdLocal: Int? = null,
    @field:Json(name = "latitude") var latitude: String? = null,
    @field:Json(name = "idDistrito") var idDistrito: Int? = null,
    @field:Json(name = "local") var local: String? = null,
    @field:Json(name = "longitude") var longitude: String? = null
)

@JsonClass(generateAdapter = true)
data class IPMALocationResponse(
    @field:Json(name = "owner") var owner: String? = null,
    @field:Json(name = "country") var country: String? = null,
    @field:Json(name = "data") var data: List<IPMALocation>? = null
)

@JsonClass(generateAdapter = true)
data class IPMADailyForecast(
    @field:Json(name = "precipitaProb") var precipitaProb: String? = null,
    @field:Json(name = "tMin") var tMin: String? = null,
    @field:Json(name = "tMax") var tMax: String? = null,
    @field:Json(name = "predWindDir") var predWindDir: String? = null,
    @field:Json(name = "idWeatherType") var idWeatherType: Int? = null,
    @field:Json(name = "classWindSpeed") var classWindSpeed: Any? = null,
    @field:Json(name = "latitude") var latitude: String? = null,
    @field:Json(name = "longitude") var longitude: String? = null,
    @field:Json(name = "forecastDate") var forecastDate: String? = null,
    @field:Json(name = "classPrecInt") var classPrecInt: Any? = null
)

@JsonClass(generateAdapter = true)
data class IPMADailyForecastResponse(
    @field:Json(name = "owner") var owner: String? = null,
    @field:Json(name = "country") var country: String? = null,
    @field:Json(name = "data") var data: List<IPMADailyForecast>? = null,
    @field:Json(name = "globalIdLocal") var globalIdLocal: Int? = null,
    @field:Json(name = "dataUpdate") var dataUpdate: String? = null
)

@JsonClass(generateAdapter = true)
data class IPMAWeatherType(
    @field:Json(name = "descWeatherTypeEN") var descWeatherTypeEN: String? = null,
    @field:Json(name = "descWeatherTypePT") var descWeatherTypePT: String? = null,
    @field:Json(name = "idWeatherType") var idWeatherType: Int? = null
)

@JsonClass(generateAdapter = true)
data class IPMAWeatherTypeResponse(
    @field:Json(name = "owner") var owner: String? = null,
    @field:Json(name = "country") var country: String? = null,
    @field:Json(name = "data") var data: List<IPMAWeatherType>? = null
)

@JsonClass(generateAdapter = true)
data class IPMAWindSpeedClass(
    @field:Json(name = "descClassWindSpeedDailyEN") var descClassWindSpeedDailyEN: String? = null,
    @field:Json(name = "descClassWindSpeedDailyPT") var descClassWindSpeedDailyPT: String? = null,
    @field:Json(name = "classWindSpeed") var classWindSpeed: Any? = null
)

@JsonClass(generateAdapter = true)
data class IPMAWindSpeedClassResponse(
    @field:Json(name = "owner") var owner: String? = null,
    @field:Json(name = "country") var country: String? = null,
    @field:Json(name = "data") var data: List<IPMAWindSpeedClass>? = null
)

@JsonClass(generateAdapter = true)
data class IPMAPrecipClass(
    @field:Json(name = "descClassPrecIntEN") var descClassPrecIntEN: String? = null,
    @field:Json(name = "descClassPrecIntPT") var descClassPrecIntPT: String? = null,
    @field:Json(name = "classPrecInt") var classPrecInt: Any? = null
)

@JsonClass(generateAdapter = true)
data class IPMAPrecipClassResponse(
    @field:Json(name = "owner") var owner: String? = null,
    @field:Json(name = "country") var country: String? = null,
    @field:Json(name = "data") var data: List<IPMAPrecipClass>? = null
)

@JsonClass(generateAdapter = true)
data class IPMAStationGeometry(
    @field:Json(name = "type") var type: String? = null,
    @field:Json(name = "coordinates") var coordinates: List<Double>? = null // [lon, lat]
)

@JsonClass(generateAdapter = true)
data class IPMAStationProperties(
    @field:Json(name = "idEstacao") var idEstacao: Long? = null,
    @field:Json(name = "localEstacao") var localEstacao: String? = null
)

@JsonClass(generateAdapter = true)
data class IPMAStation(
    @field:Json(name = "type") var type: String? = null,
    @field:Json(name = "geometry") var geometry: IPMAStationGeometry? = null,
    @field:Json(name = "properties") var properties: IPMAStationProperties? = null
)

@JsonClass(generateAdapter = true)
data class IPMAObservation(
    @field:Json(name = "temperatura") var temperatura: Double? = null,
    @field:Json(name = "humidade") var humidade: Double? = null,
    @field:Json(name = "intensidadeVentoKM") var intensidadeVentoKM: Double? = null,
    @field:Json(name = "intensidadeVento") var intensidadeVento: Double? = null,
    @field:Json(name = "idDireccVento") var idDireccVento: Int? = null,
    @field:Json(name = "precAcumulada") var precAcumulada: Double? = null,
    @field:Json(name = "pressao") var pressao: Double? = null,
    @field:Json(name = "radiacao") var radiacao: Double? = null
)

@JsonClass(generateAdapter = true)
data class IPMAWarning(
    @field:Json(name = "idAreaAviso") var idAreaAviso: String? = null,
    @field:Json(name = "awarenessTypeName") var awarenessTypeName: String? = null,
    @field:Json(name = "awarenessLevelID") var awarenessLevelID: String? = null,
    @field:Json(name = "startTime") var startTime: String? = null,
    @field:Json(name = "endTime") var endTime: String? = null,
    @field:Json(name = "text") var text: String? = null
)

@JsonClass(generateAdapter = true)
data class IPMAUVItem(
    @field:Json(name = "globalIdLocal") var globalIdLocal: Int? = null,
    @field:Json(name = "data") var data: String? = null,
    @field:Json(name = "iUv") var iUv: String? = null,
    @field:Json(name = "intervaloHora") var intervaloHora: String? = null,
    @field:Json(name = "idPeriodo") var idPeriodo: Int? = null
)
