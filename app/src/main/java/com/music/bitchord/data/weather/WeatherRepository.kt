package com.music.bitchord.data.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherCurrent(
    val temp: Int,
    val feels: Int,
    val max: Int,
    val min: Int,
    val pop: Int,
    val uv: Int,
    val code: Int,
    val isDay: Boolean,
    val title: String,
    val advice: String,
    val sym: String,
)

object WeatherRepository {
    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
        }
        return try {
            conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            conn.disconnect()
        }
    }

    fun describe(code: Int, isDay: Boolean): Pair<String, String> {
        val t = when {
            code == 0 -> "Despejado"
            code == 1 -> "Casi despejado"
            code == 2 -> "Parcialmente nublado"
            code == 3 -> "Nublado"
            code == 45 || code == 48 -> "Neblina"
            code in 51..57 -> "Llovizna"
            code in 61..65 || code in 80..82 -> "Lluvia"
            code == 66 || code == 67 -> "Lluvia engelante"
            code in 71..77 || code == 85 || code == 86 -> "Nieve"
            code >= 95 -> "Tormenta"
            else -> "Variable"
        }
        val say = when {
            code == 0 || code == 1 -> "Dia lindo, aprovéchalo."
            code == 2 -> "Nubecitas, pero todo bien."
            code == 3 -> "Cielo tapado, tranqui."
            code == 45 || code == 48 -> "Hay neblina, ve con cuidado."
            code in 51..57 -> "Llovizna suave, lleva algo."
            code in 61..65 || code in 80..82 -> "Lleva paraguas, va a llover."
            code == 66 || code == 67 -> "Lluvia engelante, mejor adentro."
            code in 71..77 || code == 85 || code == 86 -> "Está nevando, abrígate."
            code >= 95 -> "Tormenta cerca. Mejor adentro."
            else -> "El clima anda indeciso."
        }
        return t to say
    }

    fun fetch(lat: Double, lon: Double, celsius: Boolean = true): WeatherCurrent {
        val tempUnit = if (celsius) "celsius" else "fahrenheit"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,is_day,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max" +
            "&forecast_days=1&temperature_unit=$tempUnit&timezone=auto"
        val d = getJson(url)
        val cur = d.getJSONObject("current")
        val daily = d.getJSONObject("daily")
        val code = cur.getInt("weather_code")
        val isDay = cur.getInt("is_day") == 1
        val (title, say) = describe(code, isDay)
        val max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt()
        val min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt()
        val pop = daily.getJSONArray("precipitation_probability_max").getInt(0)
        val uv = Math.round(daily.getJSONArray("uv_index_max").getDouble(0)).toInt()
        val sym = if (celsius) "°C" else "°F"
        val tips = mutableListOf<String>()
        if (pop >= 60) tips.add("lleva paraguas")
        else if (pop >= 30) tips.add("podría llover, ten un paraguas a mano")
        if (uv >= 8) tips.add("protector solar")
        val hot = if (celsius) 30 else 86
        val cold = if (celsius) 14 else 57
        if (max >= hot) tips.add("toma bastante agua")
        if (min <= cold) tips.add("abrígate en la mañana")
        val advice = if (tips.isNotEmpty()) tips[0].replaceFirstChar { it.uppercase() } + if (tips.size > 1) ", " + tips.drop(1).joinToString(", ") + "." else "." else say
        return WeatherCurrent(
            temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
            feels = Math.round(cur.getDouble("apparent_temperature")).toInt(),
            max = max,
            min = min,
            pop = pop,
            uv = uv,
            code = code,
            isDay = isDay,
            title = title,
            advice = advice,
            sym = sym,
        )
    }
}