package com.music.bitchord.ui.hermes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.music.bitchord.data.weather.WeatherCurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

data class Quote(val t: String, val a: String)

/** Frases, saludos y caritas exportados de Hermes (assets/hermes/content.json). */
object HermesContent {
    private var loaded = false
    var quotes: List<Quote> = emptyList()
        private set
    var greetings: Map<String, List<String>> = emptyMap()
        private set
    var pokeSays: List<String> = emptyList()
        private set
    var faceFiles: Map<String, String> = emptyMap()
        private set
    var pixelFaces: Map<String, List<String>> = emptyMap()
        private set
    private val faceCache = HashMap<String, ImageBitmap>()

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val txt = ctx.applicationContext.assets.open("hermes/content.json").bufferedReader().use { it.readText() }
        val o = JSONObject(txt)
        val q = o.getJSONArray("quotes")
        quotes = (0 until q.length()).map { Quote(q.getJSONObject(it).getString("t"), q.getJSONObject(it).getString("a")) }
        val g = o.getJSONObject("greetings")
        greetings = g.keys().asSequence().associateWith { k ->
            val a = g.getJSONArray(k)
            (0 until a.length()).map { a.getString(it) }
        }
        val p = o.getJSONArray("pokeSays")
        pokeSays = (0 until p.length()).map { p.getString(it) }
        val f = o.getJSONObject("faceFiles")
        faceFiles = f.keys().asSequence().associateWith { f.getString(it) }
        val pf = o.getJSONObject("pixelFaces")
        pixelFaces = pf.keys().asSequence().associateWith { k ->
            val a = pf.getJSONArray(k)
            (0 until a.length()).map { a.getString(it) }
        }
        loaded = true
    }

    val allFaces: List<String> get() = faceFiles.keys.toList() + pixelFaces.keys.toList()

    fun faceBitmap(ctx: Context, face: String): ImageBitmap? {
        val file = faceFiles[face] ?: faceFiles["feliz"] ?: return null
        faceCache[file]?.let { return it }
        return try {
            ctx.applicationContext.assets.open("hermes/caritas/$file.png").use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()?.also { faceCache[file] = it }
        } catch (_: Exception) {
            null
        }
    }
}

data class ArtColors(val main: Color, val deep: Color, val accent: Color)

/** Estado global del tema de Hermes: acento del usuario o color extraído de la carátula que suena. */
object HermesState {
    var weather by mutableStateOf<WeatherCurrent?>(null)
    var baseAccent by mutableStateOf(Color(0xFF6366F1))
    var art by mutableStateOf<ArtColors?>(null)
    var playing by mutableStateOf(false)
    var userName by mutableStateOf("")
    private val cache = HashMap<String, ArtColors>()

    /** Acento vivo: el de la canción mientras suena; si no, el de Ajustes. */
    val liveAccent: Color get() = if (playing) (art?.accent ?: baseAccent) else baseAccent

    suspend fun updateArt(url: String?) {
        if (url.isNullOrBlank()) {
            art = null
            return
        }
        cache[url]?.let { art = it; return }
        val colors = withContext(Dispatchers.IO) { download(url)?.let { analyze(it) } }
        if (colors != null) {
            if (cache.size > 60) cache.clear()
            cache[url] = colors
        }
        art = colors
    }

    private fun download(url: String): Bitmap? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            c.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun analyze(src: Bitmap): ArtColors? {
        val s = Bitmap.createScaledBitmap(src, 48, 48, true)
        val px = IntArray(48 * 48)
        s.getPixels(px, 0, 48, 0, 0, 48, 48)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var br = 0
        var bg = 0
        var bb = 0
        var bs = -1
        for (p in px) {
            if ((p ushr 24) < 128) continue
            val rr = (p shr 16) and 255
            val gg = (p shr 8) and 255
            val bl = p and 255
            r += rr; g += gg; b += bl; n++
            val mx = max(rr, max(gg, bl))
            val mn = min(rr, min(gg, bl))
            val sat = mx - mn
            val lum = mx + mn
            if (sat > bs && lum > 40 && lum < 460) { bs = sat; br = rr; bg = gg; bb = bl }
        }
        if (n == 0) return null
        val avg = floatArrayOf((r / n).toFloat(), (g / n).toFloat(), (b / n).toFloat())
        val vib = if (bs >= 0) floatArrayOf(br.toFloat(), bg.toFloat(), bb.toFloat()) else avg
        return ArtColors(rgb(vib), rgb(avg), rgb(fitAccent(vib)))
    }

    private fun rgb(c: FloatArray) = Color(
        (c[0].coerceIn(0f, 255f)) / 255f,
        (c[1].coerceIn(0f, 255f)) / 255f,
        (c[2].coerceIn(0f, 255f)) / 255f,
    )

    /** Ajusta el vibrante a una luminancia legible (0.22–0.55) sin cambiar el tono. */
    private fun fitAccent(c: FloatArray): FloatArray {
        val lum = (0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]) / 255f
        var f = 1f
        if (lum > 0.55f) f = 0.55f / lum else if (lum < 0.22f) f = 0.22f / max(lum, 0.02f)
        if (f <= 1f) return floatArrayOf(c[0] * f, c[1] * f, c[2] * f)
        val t = min(1f, (f - 1f) * 0.7f)
        return floatArrayOf(c[0] + (255f - c[0]) * t, c[1] + (255f - c[1]) * t, c[2] + (255f - c[2]) * t)
    }
}

/** Paleta de Hermes (oscura / clara) según el tema de la app. */
data class HermesPalette(
    val bg: Color, val s: Color, val s2: Color, val t: Color, val m: Color, val bd: Color, val dark: Boolean,
)

val HermesDark = HermesPalette(
    bg = Color(0xFF09090B), s = Color(0xFF18181B), s2 = Color(0xFF222228),
    t = Color(0xFFFAFAFA), m = Color(0xFFA1A1AA), bd = Color(0x17FFFFFF), dark = true,
)
val HermesLight = HermesPalette(
    bg = Color(0xFFF4F4F5), s = Color(0xFFFFFFFF), s2 = Color(0xFFF0F0F3),
    t = Color(0xFF09090B), m = Color(0xFF6B6B76), bd = Color(0x17000000), dark = false,
)
