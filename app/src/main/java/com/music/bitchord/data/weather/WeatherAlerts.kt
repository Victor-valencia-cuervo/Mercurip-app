package com.music.bitchord.data.weather

import android.content.Context
import com.music.bitchord.ui.hermes.HermesState
import com.music.bitchord.ui.pets.PetsBus
import com.music.bitchord.ui.toast.MercurioToasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Avisos del clima estilo Hermes: toasts casuales + estado para que las mascotas reaccionen. */
object WeatherAlerts {
    private const val FILE = "mercurio_weather"
    private const val COOLDOWN_MS = 3L * 60L * 60L * 1000L

    /** Clave de WEATHER (phrases.json) según el clima actual. */
    fun petKey(w: WeatherCurrent): String? = when {
        w.code in 95..99 -> "storm"
        w.code in 51..67 || w.code in 80..82 -> "rain"
        w.code == 45 || w.code == 48 -> "fog"
        w.max >= 30 -> "hot"
        w.min <= 14 -> "cold"
        w.uv >= 8 -> "uv"
        !w.isDay -> "night"
        w.code in 2..3 -> "cloud"
        w.code == 0 -> "sun"
        else -> null
    }

    /** Consulta el clima, avisa a las mascotas y muestra toasts (máx. uno cada 3 h). */
    suspend fun check(ctx: Context, lat: Double = 5.8747, lon: Double = -75.5667) {
        val w = try {
            withContext(Dispatchers.IO) { WeatherRepository.fetch(lat, lon, true) }
        } catch (_: Exception) {
            return
        }
        PetsBus.weather = petKey(w)
        HermesState.weather = w

        val prefs = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("lastToast", 0L) < COOLDOWN_MS) return
        var shown = false
        if (w.code in 95..99) { MercurioToasts.show("Tormenta eléctrica cerca"); shown = true }
        else if (w.pop >= 60) { MercurioToasts.show("Lluvia ${w.pop}%"); shown = true }
        if (w.max >= 30) { MercurioToasts.show("Calor ${w.max}°"); shown = true }
        if (w.uv >= 8) { MercurioToasts.show("UV ${w.uv} por las nubes"); shown = true }
        if (w.min <= 14) { MercurioToasts.show("Frío de ${w.min}°"); shown = true }
        if (shown) prefs.edit().putLong("lastToast", now).apply()
    }
}
