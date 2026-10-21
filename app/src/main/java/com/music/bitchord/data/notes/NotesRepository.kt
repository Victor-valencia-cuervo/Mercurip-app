package com.music.bitchord.data.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class MercurioNote(
    val id: Long,
    val text: String,
    val color: Int,
    val reminderAt: Long? = null,
)

object NotesRepository {
    private const val PREF = "mercurio_notes"
    private const val KEY = "notes"

    fun load(ctx: Context): MutableList<MercurioNote> {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<MercurioNote>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                MercurioNote(
                    id = o.getLong("id"),
                    text = o.getString("text"),
                    color = o.getInt("color"),
                    reminderAt = if (o.has("reminderAt") && !o.isNull("reminderAt")) o.getLong("reminderAt") else null,
                )
            )
        }
        return out
    }

    fun save(ctx: Context, notes: List<MercurioNote>) {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val arr = JSONArray()
        notes.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id)
            o.put("text", n.text)
            o.put("color", n.color)
            if (n.reminderAt != null) o.put("reminderAt", n.reminderAt) else o.put("reminderAt", JSONObject.NULL)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(ctx: Context, text: String, color: Int): MercurioNote {
        val notes = load(ctx)
        val n = MercurioNote(System.currentTimeMillis(), text, color)
        notes.add(0, n)
        save(ctx, notes)
        return n
    }

    fun delete(ctx: Context, id: Long) {
        val notes = load(ctx).filter { it.id != id }
        save(ctx, notes)
    }

    /** Programa el aviso y guarda la hora en la nota (para mostrarla en "Próximos recordatorios" del inicio). */
    fun scheduleAndStore(ctx: Context, noteId: Long, text: String, delayMillis: Long) {
        val at = System.currentTimeMillis() + delayMillis
        val updated = load(ctx).map { if (it.id == noteId) it.copy(reminderAt = at) else it }
        save(ctx, updated)
        scheduleReminder(ctx, text, delayMillis)
    }

    fun scheduleReminder(ctx: Context, text: String, delayMillis: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, NoteReminderReceiver::class.java).putExtra("text", text)
        val pi = PendingIntent.getBroadcast(ctx, text.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pi)
    }
}