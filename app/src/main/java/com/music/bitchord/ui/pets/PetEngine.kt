package com.music.bitchord.ui.pets

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val emoji: String,
    val color: Int,
    val size: Float,
    val gravity: Float = 0f,
)

class BatFly(
    val sx: Float, val sy: Float, val ex: Float, val ey: Float,
    val t0: Long, val durMs: Long, val variant: Int,
)

class PetActor(val def: PetDef, val sprite: ImageBitmap) {
    // Estado observado por la UI
    var x by mutableFloatStateOf(0f)          // centro horizontal (px)
    var y by mutableFloatStateOf(0f)          // pies (px)
    var hopY by mutableFloatStateOf(0f)
    var jitterX by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)
    var scaleK by mutableFloatStateOf(1f)
    var dir by mutableIntStateOf(1)
    var cell by mutableIntStateOf(0)
    var bubble by mutableStateOf<String?>(null)

    // Estado interno (no observado)
    var vx = 0f
    var vy = 0f
    var state = "fall"
    var stateLeft = 0f
    var animT = 0f
    var floatY = 0f
    var fixedSeq: List<Int>? = null
    var busy = false
    var dragging = false
    var bubbleUntil = 0L
    var lastSelfSp = 0L
    var lastQuote = 0L
    var lastTouch = 0L
    var pokes = 0
    var pokeWindowStart = 0L
    var job: Job? = null
}

/**
 * Mundo de mascotas: física + comportamiento + intérprete de poderes (guiones de specials.json).
 * Todo corre en el hilo principal (LaunchedEffect + scope de la composición).
 */
class PetWorld(private val scope: CoroutineScope, private val ctx: Context) {
    val actors = mutableStateListOf<PetActor>()
    val particles = ArrayList<Particle>()
    val bats = ArrayList<BatFly>()
    var veilColor by mutableIntStateOf(0)
    var veilAlpha by mutableFloatStateOf(0f)
    var fxTick by mutableIntStateOf(0)
    private var veilJob: Job? = null

    var w = 0f
    var h = 0f
    var d = 1f
    var floorY = 0f
    var sizeScale = 1f
    var floatMode = false
    var talk = true
    private var lastGlobalSp = 0L
    private var greeted = false

    private val runners = setOf("deportivo", "gato", "perro", "caballo", "dragon", "rick", "morty", "loky", "dex")

    fun petW(a: PetActor) = a.def.dispW * d * sizeScale
    fun petH(a: PetActor) = a.def.dispH * d * sizeScale

    // ---------- alta / baja de mascotas ----------
    fun sync(selected: List<String>) {
        val want = selected.mapNotNull { PetStore.byId(it) }
        actors.filter { a -> want.none { it.id == a.def.id } }.forEach { a ->
            a.job?.cancel()
            actors.remove(a)
        }
        for (def in want) {
            if (actors.none { it.def.id == def.id }) {
                val bmp = PetStore.sprite(ctx, def) ?: continue
                val a = PetActor(def, bmp)
                a.x = if (w > 0f) (0.15f + Random.nextFloat() * 0.7f) * w else 200f
                a.y = -petH(a)
                a.floatY = floorY
                a.lastTouch = now()
                actors.add(a)
            }
        }
    }

    private fun now() = System.currentTimeMillis()
    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun <T> pick(l: List<T>): T? = if (l.isEmpty()) null else l[Random.nextInt(l.size)]

    // ---------- burbujas ----------
    fun say(a: PetActor, text: String?, ms: Long = 2600L) {
        if (!talk || text.isNullOrBlank()) return
        a.bubble = text.replace("{n}", "").replace("{t}", PetsBus.songTitle.ifBlank { "esta canción" })
        a.bubbleUntil = now() + ms
    }

    fun poke(a: PetActor) {
        if (a.busy) return
        val t = now()
        a.lastTouch = t
        if (t - a.pokeWindowStart > 4000) { a.pokeWindowStart = t; a.pokes = 0 }
        a.pokes++
        if (a.pokes >= 6) {
            say(a, pick(PetStore.phraseList("DIZZY")), 2600)
            a.state = "alert"; a.stateLeft = 2800f; a.pokes = 0
        } else {
            say(a, pick(PetStore.phraseList("POKE")), 2200)
            a.state = if (Random.nextBoolean()) "love" else "jump"
            a.stateLeft = if (a.state == "love") 1800f else 950f
        }
        a.vx = 0f
    }

    fun grab(a: PetActor) {
        if (a.busy) return
        a.dragging = true
        a.lastTouch = now()
        a.vx = 0f; a.vy = 0f
        if (Random.nextFloat() < 0.5f) say(a, pick(PetStore.phraseList("PICKED_UP")), 2000)
    }

    fun release(a: PetActor, tossVx: Float, tossVy: Float) {
        a.dragging = false
        a.vx = tossVx
        a.vy = tossVy
        a.state = "fall"
    }

    // ---------- frame loop ----------
    fun tick(dtMs: Float) {
        if (w <= 0f || h <= 0f) return
        val t = now()
        if (!greeted && actors.isNotEmpty()) {
            greeted = true
            greet(actors[0])
        }
        for (a in actors.toList()) update(a, dtMs, t)
        // partículas
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dtMs
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dtMs / 1000f
            p.y += p.vy * dtMs / 1000f
            p.vy += p.gravity * dtMs / 1000f
        }
        bats.removeAll { t - it.t0 > it.durMs }
        // reto de la pantalla Mascotas
        PetsBus.testSpecialFor?.let { id ->
            PetsBus.testSpecialFor = null
            val target = actors.firstOrNull { it.def.id == id } ?: actors.firstOrNull()
            if (target != null) {
                val sp = target.def.special
                if (sp != null) startSpecial(target, sp.id, force = true)
            }
        }
        fxTick++
    }

    private fun greet(a: PetActor) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val key = if (hour < 12) "morning" else if (hour < 19) "afternoon" else "evening"
        val arr: JSONArray? = PetStore.phrases.optJSONObject("GREET")?.optJSONArray(key)
        val list = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }
        say(a, pick(list), 3000)
    }

    private fun targetY(a: PetActor): Float {
        if (floatMode || a.def.hover) {
            if (a.floatY <= 0f || a.floatY > floorY) a.floatY = floorY - rnd(30f, 120f) * d
            return a.floatY
        }
        return floorY
    }

    private fun animKey(a: PetActor): String = when (a.state) {
        "fall", "drag", "toss" -> "jump"
        "land" -> "idle"
        else -> a.state
    }

    private fun animate(a: PetActor, key: String, dtMs: Float) {
        val frames = a.def.frames[key] ?: a.def.frames["idle"] ?: listOf(0)
        val fps = when (key) {
            "walk" -> a.def.fps ?: 6f
            "run" -> a.def.fps?.let { it * 1.5f } ?: 9f
            "idle" -> 2.2f
            "love" -> 2.6f
            "alert" -> 8f
            else -> 3f
        }
        a.animT += dtMs / 1000f * fps
        a.cell = frames[(a.animT.toInt()) % frames.size]
    }

    private fun update(a: PetActor, dt: Float, t: Long) {
        if (a.bubble != null && t > a.bubbleUntil) a.bubble = null
        if (!PetPrefs.talk && a.bubble != null) a.bubble = null
        val pw = petW(a)
        val margin = pw * 0.5f

        if (a.dragging) {
            a.cell = a.def.grabFrame ?: (a.def.frames["jump"]?.firstOrNull() ?: 0)
            return
        }
        if (a.busy) {
            val seq = a.fixedSeq
            if (seq != null && seq.isNotEmpty()) {
                a.animT += dt / 1000f * 7f
                a.cell = seq[a.animT.toInt() % seq.size]
            } else {
                animate(a, "idle", dt)
            }
            return
        }
        val ty = targetY(a)
        when (a.state) {
            "fall", "toss" -> {
                a.vy += 2400f * d * dt / 1000f
                a.y += a.vy * dt / 1000f
                a.x += a.vx * dt / 1000f
                a.vx *= 0.995f
                a.x = a.x.coerceIn(margin, max(margin, w - margin))
                animate(a, "jump", dt)
                if (a.y >= ty) {
                    a.y = ty; a.vy = 0f; a.vx = 0f
                    a.state = "land"; a.stateLeft = 350f
                }
            }
            "sleep" -> {
                a.stateLeft -= dt
                a.y += (ty - a.y) * min(1f, dt * 0.006f)
                animate(a, "sleep", dt)
                if (a.stateLeft <= 0f) {
                    say(a, pick(PetStore.phraseList("WAKE")), 2400)
                    a.state = "idle"; a.stateLeft = 1500f
                }
            }
            else -> {
                a.stateLeft -= dt
                a.y += (ty - a.y) * min(1f, dt * 0.006f)
                if (a.state == "walk" || a.state == "run") {
                    val sp = (if (a.state == "run") 95f else 42f) * d * sizeScale
                    a.x += a.dir * sp * dt / 1000f
                    if (a.x < margin) { a.x = margin; a.dir = 1 }
                    if (a.x > w - margin) { a.x = w - margin; a.dir = -1 }
                }
                animate(a, animKey(a), dt)
                if (a.stateLeft <= 0f) decide(a, t)
            }
        }
        // frases propias ("quotes") por temporizador
        if (a.def.quotes.isNotEmpty() && a.def.quoteEveryMs > 0 && t - a.lastQuote > a.def.quoteEveryMs && a.bubble == null) {
            a.lastQuote = t
            say(a, pick(a.def.quotes), 3200)
        }
    }

    private fun setState(a: PetActor, s: String, ms: Float) {
        a.state = s; a.stateLeft = ms; a.animT = 0f
    }

    private fun decide(a: PetActor, t: Long) {
        val def = a.def
        // poder especial
        val sp = def.special
        if (sp != null && t - a.lastSelfSp > 50_000L && t - lastGlobalSp > 9_000L &&
            Random.nextFloat() < sp.chance * 2.5f
        ) {
            startSpecial(a, sp.id)
            return
        }
        // clima
        val wKey = PetsBus.weather
        if (wKey != null && Random.nextFloat() < 0.10f) {
            val lines = def.weatherPhrases[wKey].orEmpty().ifEmpty { weatherList(wKey) }
            say(a, pick(lines), 3400)
            when (wKey) {
                "hot", "sun", "uv" -> { setState(a, "hot", 7000f); return }
                "cold", "rain", "storm" -> { setState(a, if (wKey == "cold") "cold" else "idle", 7000f); return }
            }
        }
        // música
        if (PetsBus.playing && Random.nextFloat() < 0.28f) {
            val lines = def.musicPhrases.ifEmpty { PetStore.phraseList("MUSIC") }
            say(a, pick(lines), 3400)
            setState(a, "music", 5000f)
            return
        }
        // dormir si lleva rato sin que lo toquen
        if (t - a.lastTouch > 240_000L && Random.nextFloat() < 0.15f) {
            setState(a, "sleep", rnd(9000f, 16000f))
            a.lastTouch = t - 180_000L
            return
        }
        if (Random.nextFloat() < 0.10f) say(a, pick(def.voice.ifEmpty { PetStore.phraseList("FUN") }), 2800)
        val r = Random.nextFloat()
        val canRun = def.id in runners
        when {
            r < 0.42f -> {
                a.dir = if (a.x < w * 0.2f) 1 else if (a.x > w * 0.8f) -1 else if (Random.nextBoolean()) 1 else -1
                setState(a, "walk", rnd(2500f, 6000f))
            }
            r < 0.50f && canRun -> {
                a.dir = if (a.x < w * 0.5f) 1 else -1
                setState(a, "run", rnd(1500f, 3500f))
            }
            r < 0.57f -> setState(a, "wave", 2600f)
            r < 0.64f -> setState(a, "jump", 950f)
            r < 0.69f -> setState(a, "alert", 3500f)
            r < 0.73f -> setState(a, "love", 3000f)
            else -> setState(a, "idle", rnd(2000f, 5000f))
        }
        if (floatMode || def.hover) a.floatY = floorY - rnd(30f, if (floatMode) h * 0.6f else 120f) * d
    }

    private fun weatherList(key: String): List<String> {
        val arr = PetStore.phrases.optJSONObject("WEATHER")?.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    fun musicSay() {
        val a = actors.firstOrNull { !it.busy } ?: return
        say(a, pick(a.def.musicPhrases.ifEmpty { PetStore.phraseList("MUSIC") }), 3800)
        if (a.state == "idle" || a.state == "walk") setState(a, "music", 5000f)
    }

    fun weatherSay(key: String) {
        val a = actors.firstOrNull { !it.busy } ?: return
        say(a, pick(a.def.weatherPhrases[key].orEmpty().ifEmpty { weatherList(key) }), 4200)
    }

    // ---------- poderes ----------
    fun startSpecial(a: PetActor, id: String, force: Boolean = false) {
        if (a.busy || a.dragging) return
        val steps = PetStore.scripts[id] ?: return
        val t = now()
        a.busy = true
        a.lastSelfSp = t
        lastGlobalSp = t
        a.vx = 0f
        a.job = scope.launch {
            try {
                runSteps(a, steps, 0)
            } catch (_: CancellationException) {
            } finally {
                a.busy = false
                a.fixedSeq = null
                a.alpha = 1f; a.scaleK = 1f; a.hopY = 0f; a.jitterX = 0f
                a.y = targetY(a)
                setState(a, "idle", 1200f)
            }
        }
    }

    private suspend fun runSteps(a: PetActor, steps: List<SpStep>, depth: Int) {
        for (st in steps) exec(a, st, depth)
    }

    private fun parseColor(s: String): Int = try {
        android.graphics.Color.parseColor(s)
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }

    private suspend fun exec(a: PetActor, st: SpStep, depth: Int) {
        val pw = petW(a)
        val ph = petH(a)
        val margin = pw * 0.6f
        when (st.t) {
            "say" -> {
                say(a, st.s, st.ms)
                delay(if (talk) (st.ms * 0.8f).toLong() else 400L)
            }
            "frame" -> {
                val seq = a.def.special?.sf?.get(st.k) ?: a.def.frames[st.k]
                if (seq != null && seq.isNotEmpty()) {
                    a.fixedSeq = seq
                    a.animT = 0f
                    a.cell = seq[0]
                }
            }
            "wait" -> delay(st.ms)
            "burst" -> burst(a.x, a.y - ph * 0.5f, st)
            "shower" -> {
                val n = max(1, st.n)
                for (i in 0 until n) {
                    particles += Particle(
                        x = rnd(0f, w), y = -20f * d,
                        vx = rnd(-30f, 30f) * d, vy = rnd(120f, 260f) * d,
                        life = (h / (150f * d)) * 1000f + 800f, maxLife = (h / (150f * d)) * 1000f + 800f,
                        emoji = st.e, color = android.graphics.Color.WHITE, size = rnd(14f, 24f) * d,
                    )
                    delay(max(16L, st.ms / n))
                }
            }
            "veil" -> {
                veilJob?.cancel()
                veilColor = parseColor(st.c)
                veilJob = scope.launch {
                    animateFloat(veilAlpha, st.peak, st.tin) { veilAlpha = it }
                    animateFloat(veilAlpha, 0f, st.tout) { veilAlpha = it }
                }
                delay(st.tin)
            }
            "dash" -> {
                val tx = when (st.to) {
                    "rand" -> rnd(margin, max(margin + 1f, w - margin))
                    "near" -> (a.x + a.dir * 120f * d).coerceIn(margin, w - margin)
                    else -> if (a.x < w / 2f) w - margin else margin
                }
                val sx = a.x
                a.dir = if (tx >= sx) 1 else -1
                val dur = max(200L, st.ms)
                val t0 = System.nanoTime()
                var lastTrail = 0L
                while (true) {
                    val el = (System.nanoTime() - t0) / 1_000_000L
                    val p = (el.toFloat() / dur).coerceIn(0f, 1f)
                    val e = p * p * (3f - 2f * p)
                    a.x = sx + (tx - sx) * e
                    if (st.e.isNotEmpty() && el - lastTrail > 70L) {
                        lastTrail = el
                        particles += Particle(a.x - a.dir * pw * 0.3f, a.y - ph * 0.25f, rnd(-10f, 10f) * d, rnd(-12f, 4f) * d,
                            600f, 600f, st.e, android.graphics.Color.WHITE, 18f * d)
                    }
                    if (p >= 1f) break
                    delay(16)
                }
            }
            "hop" -> {
                val n = max(1, st.n)
                for (i in 0 until n) {
                    val t0 = System.nanoTime()
                    while (true) {
                        val p = ((System.nanoTime() - t0) / 1_000_000f / 380f).coerceIn(0f, 1f)
                        a.hopY = -4f * st.h * d * p * (1f - p)
                        if (p >= 1f) break
                        delay(16)
                    }
                    a.hopY = 0f
                }
            }
            "teleport" -> {
                a.x = rnd(margin, max(margin + 1f, w - margin))
                a.dir = if (Random.nextBoolean()) 1 else -1
            }
            "fade" -> animateFloat(a.alpha, st.a, st.ms) { a.alpha = it }
            "scale" -> animateFloat(a.scaleK, st.kScale, st.ms) { a.scaleK = it }
            "shake" -> {
                val t0 = System.nanoTime()
                while ((System.nanoTime() - t0) / 1_000_000L < st.ms) {
                    a.jitterX = rnd(-6f, 6f) * d
                    delay(30)
                }
                a.jitterX = 0f
            }
            "fly" -> {
                val ex = rnd(margin, max(margin + 1f, w - margin))
                val ey = rnd(h * 0.15f, h * 0.55f)
                bats += BatFly(a.x, a.y - ph * 0.5f, ex, ey, now(), st.ms, Random.nextInt(6))
                delay(st.ms)
                a.x = ex
                a.y = targetY(a)
            }
            "proj" -> {
                val ms = max(200L, st.ms)
                val spd = st.dist * d / (ms / 1000f)
                particles += Particle(a.x + a.dir * pw * 0.4f, a.y - ph * 0.5f, a.dir * spd, 0f,
                    ms.toFloat(), ms.toFloat(), st.e, android.graphics.Color.WHITE, 26f * d)
                delay((ms * 0.7f).toLong())
            }
            "pick" -> if (depth < 2) {
                val id = pick(st.ids)
                val sub = id?.let { PetStore.scripts[it] }
                if (sub != null) runSteps(a, sub, depth + 1)
            }
        }
    }

    private fun burst(cx: Float, cy: Float, st: SpStep) {
        val n = max(1, st.n)
        val col = parseColor(st.c)
        for (i in 0 until n) {
            val ang = rnd(0f, (2 * PI).toFloat())
            val spd = rnd(0.35f, 1f) * st.spread * d * 2.2f
            particles += Particle(
                cx, cy, cos(ang) * spd, sin(ang) * spd - 20f * d,
                rnd(650f, 1100f), 1100f, st.e, col,
                (if (st.e.isEmpty()) rnd(4f, 8f) else rnd(16f, 26f)) * d,
                gravity = if (st.e.isEmpty()) 0f else 60f * d,
            )
        }
    }

    private suspend fun animateFloat(from: Float, to: Float, ms: Long, onValue: (Float) -> Unit) {
        if (ms <= 0L) { onValue(to); return }
        val t0 = System.nanoTime()
        while (true) {
            val p = ((System.nanoTime() - t0) / 1_000_000f / ms).coerceIn(0f, 1f)
            onValue(from + (to - from) * p)
            if (p >= 1f) break
            delay(16)
        }
    }
}
