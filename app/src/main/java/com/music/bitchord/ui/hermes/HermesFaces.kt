package com.music.bitchord.ui.hermes

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.weather.WeatherCurrent
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class FaceReaction(val face: String, val kind: String, val say: String)

/** Qué carita y qué frase toca según música, hora, inactividad y clima (port de faceMood.js). */
object HermesFaces {
    private fun slot(): Long = System.currentTimeMillis() / 1_800_000L

    private fun pick(list: List<String>, seed: Float, salt: Int = 0): String {
        val v = seed.toDouble() * 9301.0 + (salt + slot() * 57).toDouble() * 49297.0
        val f = ((v % 1.0) + 1.0) % 1.0
        return list[(f * list.size).toInt().coerceIn(0, list.size - 1)]
    }

    private fun toC(t: Int, sym: String): Float = if (sym == "°F") (t - 32) * 5f / 9f else t.toFloat()

    fun reactTo(
        weather: WeatherCurrent?, hour: Int, playing: Boolean, title: String?,
        idleSec: Int, seed: Float, name: String,
    ): FaceReaction {
        val late = hour >= 23 || hour < 5
        val sleeping = !playing && idleSec >= (if (late) 45 else 180)
        val n = if (name.isNotBlank()) ", $name" else ""

        if (sleeping) {
            return FaceReaction(
                "dormido", "sleep",
                pick(
                    if (late) listOf("Zzz… ya es tarde$n. Mañana seguimos.", "Shhh, es de madrugada. Yo también duermo.", "Hora de descansar$n. Yo ya me dormí.")
                    else listOf("Zzz… me quedé dormido. Toca algo y despierto.", "Shhh, descansando. Toca la pantalla y vuelvo.", "Zzz… llevo rato sin verte por aquí."),
                    seed,
                ),
            )
        }
        if (playing) {
            val t = if (!title.isNullOrBlank()) "«$title»" else "esta canción"
            return FaceReaction(
                "musica", "music",
                pick(listOf("Sonando $t. Buen gusto$n.", "Con audífonos y $t.", "$t… súbele que está buena.", "En modo música, no molesten."), seed, 1),
            )
        }
        if (weather == null) return FaceReaction("pensando", "load", "Despertando…")

        val c = toC(weather.temp, weather.sym)
        val cf = toC(weather.feels, weather.sym)
        val feelTxt = if (kotlin.math.abs(weather.feels - weather.temp) >= 2) " Se siente como ${weather.feels}${weather.sym}." else ""
        val warm = c >= 26
        val hot = c >= 30
        val cold = c <= 14
        val tip = if (hot) "Hidrátate bien." else if (warm) "Día de ropa liviana." else if (cold) "Abrígate, hace fresquito." else ""
        fun withTip(s: String) = "$s$feelTxt${if (tip.isNotEmpty() && seed > 0.4f) " $tip" else ""}".trim()

        val code = weather.code
        var face = "feliz"
        var base: String
        when {
            code == 0 || code == 1 -> {
                if (!weather.isDay) {
                    face = "enamorado"; base = pick(listOf("Noche despejada, mira esas estrellas.", "Cielo limpio esta noche, una belleza."), seed, 2)
                } else if (hot) {
                    face = "lentes"; base = pick(listOf("Solazo con cielo limpio, modo gafas.", "Sol a full. Protector y gafas."), seed, 3)
                } else {
                    face = if (warm || cf > 18) "enamorado" else "feliz"
                    base = pick(listOf("Día despejado, aprovéchalo.", "Cielo limpio, qué belleza de día.", "Hoy el sol está de tu lado."), seed, 4)
                }
            }
            code == 2 -> { face = "guino"; base = pick(listOf("Nubecitas por aquí y por allá, todo bien.", "Sol y nubes jugando a las escondidas.", "Parcialmente nublado, buen plan para salir."), seed, 5) }
            code == 3 -> { face = "neutral"; base = pick(listOf("Cielo tapado, plan tranquilo.", "Todo gris y calmado, ideal para un tinto.", "Nublado total, el día pide pausa."), seed, 6) }
            code == 45 || code == 48 -> { face = "pensando"; base = pick(listOf("Neblina espesa, ve con cuidado.", "Se ve todo borroso afuera, maneja con calma."), seed, 7) }
            code in 51..57 -> { face = "llanto"; base = pick(listOf("Llovizna suave, lleva algo por si acaso.", "Cae una lluvia finita, chaqueta a la mano."), seed, 8) }
            code in 61..65 || code in 80..82 -> { face = "llanto"; base = pick(listOf("Está lloviendo, no olvides el paraguas.", "Día de lluvia: paraguas y buena música.", "Lluvia afuera, adentro se está mejor."), seed, 9) }
            code == 66 || code == 67 -> { face = "sorpresa"; base = "Lluvia helada, mejor quédate bajo techo." }
            code in 71..77 || code == 85 || code == 86 -> { face = "sorpresa"; base = "Está nevando, abrígate bien." }
            code >= 95 -> { face = "sorpresa"; base = pick(listOf("Tormenta eléctrica cerca, mejor adentro.", "Truenos por la zona, desconecta lo importante."), seed, 10) }
            else -> { face = "pensando"; base = "El clima anda indeciso hoy." }
        }
        if (c >= 33 && face != "llanto" && face != "sorpresa") {
            face = "diablito"
            base = pick(listOf("Calor del infierno, busca sombra.", "Está haciendo un calorcito sospechoso."), seed, 11)
        }
        return FaceReaction(face, "weather", withTip(base))
    }
}

// ---------- tinte de las caritas PNG (hue-rotate del acento) ----------

private fun hueOf(r: Float, g: Float, b: Float): Float? {
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    if (d < 0.08f) return null
    val h = when (mx) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    }
    return (h * 60f + 360f) % 360f
}

fun colorHue(c: Color): Float? = hueOf(c.red, c.green, c.blue)

/** Tono dominante (0-360) de la imagen ignorando píxeles grises, oscuros o transparentes. */
fun imageHue(img: ImageBitmap): Float? {
    val bmp = img.asAndroidBitmap()
    val s = android.graphics.Bitmap.createScaledBitmap(bmp, 32, 32, true)
    val px = IntArray(32 * 32)
    s.getPixels(px, 0, 32, 0, 0, 32, 32)
    var x = 0.0
    var y = 0.0
    var w = 0.0
    for (p in px) {
        if ((p ushr 24) < 200) continue
        val r = ((p shr 16) and 255) / 255f
        val g = ((p shr 8) and 255) / 255f
        val b = (p and 255) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val sat = if (mx > 0f) (mx - mn) / mx else 0f
        if (sat < 0.3f || mx < 70f / 255f) continue
        val h = hueOf(r, g, b) ?: continue
        x += cos(h * PI / 180.0) * sat
        y += sin(h * PI / 180.0) * sat
        w += sat
    }
    if (w <= 0.0) return null
    return (((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0).toFloat()
}

private fun hueRotate(deg: Float): ColorFilter {
    val a = (deg * PI / 180.0)
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    val m = floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** Carita de Hermes: PNG teñido con el acento, o pixel-art dibujado con el color del app. */
@Composable
fun PixelFace(
    face: String,
    kind: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    onClick: (() -> Unit)? = null,
) {
    val inf = rememberInfiniteTransition(label = "face")
    val bop = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "bop")
    val sleep = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sleep")
    val float = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    var m = modifier.size(size)
    if (onClick != null) {
        m = m.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    }
    Box(
        m.graphicsLayer {
            val d = density
            when (kind) {
                "music" -> { translationY = -5f * d * bop.value; rotationZ = (bop.value - 0.5f) * 4f }
                "sleep" -> { val sc = 1f + 0.04f * sleep.value; scaleX = sc; scaleY = sc; alpha = 0.85f + 0.15f * sleep.value }
                else -> { translationY = -3f * d * float.value }
            }
        },
    ) {
        Crossfade(targetState = face, animationSpec = tween(350), label = "faceSwap") { f ->
            FaceBody(f, accent)
        }
    }
}

@Composable
private fun FaceBody(face: String, accent: Color) {
    val ctx = LocalContext.current
    val pix = HermesContent.pixelFaces[face]
    if (pix != null) {
        val body = lerp(accent, Color.White, 0.28f)
        val blush = lerp(accent, Color(0xFFFF7AA8), 0.45f)
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / 12f
            pix.forEachIndexed { y, row ->
                var x = 0
                while (x < row.length) {
                    val ch = row[x]
                    val col = when (ch) {
                        '#' -> body
                        'o' -> Color(0xFF1A1022)
                        'w' -> Color.White
                        's' -> blush
                        else -> null
                    }
                    if (col == null) { x++; continue }
                    var w = 1
                    while (x + w < row.length && row[x + w] == ch) w++
                    drawRect(col, Offset(x * cell, y * cell), Size(w * cell + 0.5f, cell + 0.5f))
                    x += w
                }
            }
        }
    } else {
        val bmp = remember(face) { HermesContent.faceBitmap(ctx, face) }
        if (bmp != null) {
            val base = remember(bmp) { imageHue(bmp) }
            val acc = colorHue(accent)
            val filter = if (base != null && acc != null) hueRotate(acc - base) else null
            Image(
                bitmap = bmp,
                contentDescription = null,
                colorFilter = filter,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
