package com.music.bitchord.ui.pets

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject

/** Poder especial de una mascota: id del guion + probabilidad + fotogramas propios (clave -> celda(s) de la tira; varias celdas = animación). */
data class PetSpecialDef(val id: String, val chance: Float, val sf: Map<String, List<Int>>)

data class PetDef(
    val id: String,
    val name: String,
    val n: Int,
    val cellW: Int,
    val cellH: Int,
    val dispW: Float,
    val dispH: Float,
    val frames: Map<String, List<Int>>,
    val voice: List<String>,
    val quotes: List<String>,
    val quoteEveryMs: Long,
    val special: PetSpecialDef?,
    val fps: Float?,
    val grabFrame: Int?,
    val file: String,
    val hover: Boolean,
    val musicPhrases: List<String>,
    val weatherPhrases: Map<String, List<String>>,
)

/** Un paso del guion de un poder. Los campos que no apliquen a cada paso `t` quedan en su valor por defecto. */
data class SpStep(
    val t: String,
    val s: String = "",
    val ms: Long = 0L,
    val k: String = "",
    val e: String = "",
    val n: Int = 0,
    val spread: Float = 50f,
    val c: String = "#ffffff",
    val at: String = "self",
    val a: Float = 1f,
    val to: String = "far",
    val h: Float = 60f,
    val peak: Float = 0.3f,
    val tin: Long = 150L,
    val tout: Long = 700L,
    val dist: Float = 300f,
    val kScale: Float = 1f,
    val ids: List<String> = emptyList(),
)

object PetStore {
    private var loaded = false
    private var builtin: List<PetDef> = emptyList()
    var custom: List<PetDef> by mutableStateOf(emptyList())
        private set
    val catalog: List<PetDef> get() = builtin + custom
    var scripts: Map<String, List<SpStep>> = emptyMap()
        private set
    var phrases: JSONObject = JSONObject()
        private set
    private val sprites = HashMap<String, ImageBitmap>()
    private var batsBmp: ImageBitmap? = null

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }
    }

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        val am = ctx.applicationContext.assets
        fun text(path: String) = am.open(path).bufferedReader().use { it.readText() }

        val arr = JSONArray(text("pets/catalog.json"))
        val list = ArrayList<PetDef>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cell = o.getJSONArray("cell")
            val disp = o.getJSONArray("disp")
            val fr = HashMap<String, List<Int>>()
            val fo = o.getJSONObject("frames")
            for (k in fo.keys()) {
                val a = fo.getJSONArray(k)
                fr[k] = (0 until a.length()).map { a.getInt(it) }
            }
            val sp = o.optJSONObject("sp")?.let { s ->
                val sf = HashMap<String, List<Int>>()
                s.optJSONObject("sf")?.let { f ->
                    for (k in f.keys()) {
                        val v = f.opt(k)
                        sf[k] = if (v is JSONArray) (0 until v.length()).map { i -> v.getInt(i) } else listOf(f.getInt(k))
                    }
                }
                PetSpecialDef(s.getString("id"), s.optDouble("chance", 0.04).toFloat(), sf)
            }
            val q = o.optJSONObject("quotes")
            val wp = HashMap<String, List<String>>()
            o.optJSONObject("weatherPhrases")?.let { w -> for (k in w.keys()) wp[k] = w.optJSONArray(k).strings() }
            val id = o.getString("id")
            list += PetDef(
                id = id,
                name = o.getString("name"),
                n = o.getInt("n"),
                cellW = cell.getInt(0),
                cellH = cell.getInt(1),
                dispW = disp.getDouble(0).toFloat(),
                dispH = disp.getDouble(1).toFloat(),
                frames = fr,
                voice = o.optJSONArray("voice").strings(),
                quotes = q?.optJSONArray("list").strings(),
                quoteEveryMs = q?.optLong("every", 0L) ?: 0L,
                special = sp,
                fps = if (o.isNull("fps")) null else o.optDouble("fps").toFloat(),
                grabFrame = if (o.isNull("grabFrame")) null else o.optInt("grabFrame"),
                file = if (id == "marine") "pets/marine.png" else "pets/$id.webp",
                hover = o.optBoolean("hover", false),
                musicPhrases = o.optJSONArray("musicPhrases").strings(),
                weatherPhrases = wp,
            )
        }
        builtin = list

        val so = JSONObject(text("pets/specials.json"))
        val sc = HashMap<String, List<SpStep>>()
        for (id in so.keys()) {
            val steps = so.getJSONArray(id)
            sc[id] = (0 until steps.length()).map { idx ->
                val s = steps.getJSONObject(idx)
                SpStep(
                    t = s.getString("t"),
                    s = s.optString("s", ""),
                    ms = s.optLong("ms", 0L),
                    k = s.optString("k", ""),
                    e = s.optString("e", ""),
                    n = s.optInt("n", 0),
                    spread = s.optDouble("spread", 50.0).toFloat(),
                    c = s.optString("c", "#ffffff"),
                    at = s.optString("at", "self"),
                    a = s.optDouble("a", 1.0).toFloat(),
                    to = s.optString("to", "far"),
                    h = s.optDouble("h", 60.0).toFloat(),
                    peak = s.optDouble("peak", 0.3).toFloat(),
                    tin = s.optLong("in", 150L),
                    tout = s.optLong("out", 700L),
                    dist = s.optDouble("dist", 300.0).toFloat(),
                    kScale = s.optDouble("k", 1.0).toFloat().takeIf { s.has("k") && s.opt("k") is Number } ?: 1f,
                    ids = s.optJSONArray("ids").strings(),
                )
            }
        }
        scripts = sc
        phrases = JSONObject(text("pets/phrases.json"))
        loadCustom(ctx)
        loaded = true
    }

    fun byId(id: String): PetDef? = catalog.firstOrNull { it.id == id }

    fun isCustom(id: String) = custom.any { it.id == id }

    private const val CUSTOM_PREF = "mercurio_pets_custom"

    private fun customDef(id: String, name: String, w: Int, h: Int, phrases: List<String>) = PetDef(
        id = id, name = name, n = 1, cellW = w, cellH = h,
        dispW = 80f * w / h, dispH = 80f,
        frames = mapOf("idle" to listOf(0)),
        voice = phrases, quotes = emptyList(), quoteEveryMs = 0L,
        special = null, fps = null, grabFrame = null,
        file = "file:", hover = false, musicPhrases = emptyList(), weatherPhrases = emptyMap(),
    )

    private fun loadCustom(ctx: Context) {
        val raw = ctx.applicationContext.getSharedPreferences(CUSTOM_PREF, Context.MODE_PRIVATE).getString("list", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<PetDef>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += customDef(o.getString("id"), o.getString("name"), o.getInt("w"), o.getInt("h"), o.optJSONArray("phrases").strings())
        }
        custom = out
    }

    private fun customFile(ctx: Context, id: String) = java.io.File(ctx.applicationContext.filesDir, "pets_custom/$id.png")

    /** Crea una mascota con tu imagen (fija). Devuelve su id o null si la imagen no se pudo leer. */
    fun addCustom(ctx: Context, name: String, uri: android.net.Uri, phrases: List<String>): String? {
        val app = ctx.applicationContext
        val bmp = try {
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        val maxSide = 256
        val k = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        val scaled = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
        val id = "custom_" + System.currentTimeMillis().toString(36)
        val f = customFile(app, id)
        f.parentFile?.mkdirs()
        f.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val prefs = app.getSharedPreferences(CUSTOM_PREF, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
        val o = JSONObject()
        o.put("id", id); o.put("name", name.ifBlank { "Mi mascota" }.take(24))
        o.put("w", scaled.width); o.put("h", scaled.height)
        o.put("phrases", JSONArray(phrases.map { it.trim() }.filter { it.isNotEmpty() }.take(40)))
        arr.put(o)
        prefs.edit().putString("list", arr.toString()).apply()
        loadCustom(app)
        return id
    }

    fun removeCustom(ctx: Context, id: String) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(CUSTOM_PREF, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
        val keep = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).getString("id") != id) keep.put(arr.getJSONObject(i))
        prefs.edit().putString("list", keep.toString()).apply()
        customFile(app, id).delete()
        sprites.remove(id)
        loadCustom(app)
    }

    fun sprite(ctx: Context, def: PetDef): ImageBitmap? {
        sprites[def.id]?.let { return it }
        if (def.file == "file:") {
            return BitmapFactory.decodeFile(customFile(ctx, def.id).absolutePath)?.asImageBitmap()?.also { sprites[def.id] = it }
        }
        return try {
            ctx.applicationContext.assets.open(def.file).use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()?.also { sprites[def.id] = it }
        } catch (_: Exception) {
            null
        }
    }

    fun bats(ctx: Context): ImageBitmap? {
        batsBmp?.let { return it }
        return try {
            ctx.applicationContext.assets.open("pets/fx/bats.png").use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()?.also { batsBmp = it }
        } catch (_: Exception) {
            null
        }
    }

    fun phraseList(key: String): List<String> = phrases.optJSONArray(key).strings()
}

/** Preferencias de las mascotas (persisten en SharedPreferences). */
object PetPrefs {
    private const val FILE = "mercurio_pets"
    var enabled by mutableStateOf(true)
    var selected by mutableStateOf(listOf("tripulante"))
    var size by mutableStateOf(1f)          // 0.8 S / 1.0 M / 1.3 L
    var talk by mutableStateOf(true)
    var floatMode by mutableStateOf(false)  // false = caminan por el suelo
    private var inited = false

    fun init(ctx: Context) {
        if (inited) return
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        enabled = p.getBoolean("enabled", true)
        selected = (p.getString("selected", "tripulante") ?: "tripulante").split(",").filter { it.isNotBlank() }.take(5)
        size = p.getFloat("size", 1f)
        talk = p.getBoolean("talk", true)
        floatMode = p.getBoolean("float", false)
        inited = true
    }

    fun save(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled)
            .putString("selected", selected.joinToString(","))
            .putFloat("size", size)
            .putBoolean("talk", talk)
            .putBoolean("float", floatMode)
            .apply()
    }
}

/** Canal entre la app (música, clima, reproductor) y las mascotas. */
object PetsBus {
    var playing by mutableStateOf(false)
    var songTitle by mutableStateOf("")
    /** rain, hot, cold, storm, night, ... (claves de WEATHER en phrases.json) o null. */
    var weather by mutableStateOf<String?>(null)
    /** true mientras el full reproductor está abierto: las mascotas se ocultan. */
    var fullPlayerOpen by mutableStateOf(false)
    /** pedido de la pantalla Mascotas para probar el poder de una mascota. */
    var testSpecialFor by mutableStateOf<String?>(null)
    /** altura (dp) ocupada abajo por barras (mini reproductor + navegación) */
    var bottomInsetDp by mutableStateOf(150f)
}
