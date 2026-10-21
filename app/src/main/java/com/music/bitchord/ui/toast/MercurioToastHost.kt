package com.music.bitchord.ui.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ToastType(val color: Color, val glyph: String, val ms: Long) {
    SUCCESS(Color(0xFF3DDC84), "✓", 3200L),
    INFO(Color(0xFF5B8CFF), "i", 3400L),
    WARNING(Color(0xFFFFB020), "!", 5200L),
    ERROR(Color(0xFFFF5A5F), "✕", 4600L),
}

class ToastItem(val id: Long, val type: ToastType, val title: String, val desc: String?)

/**
 * Avisos al estilo Hermes (Sileo): título corto + descripción casual + tipo.
 * `show(msg)` mantiene la firma antigua y traduce frases largas a título/descripcion con [RULES].
 */
object MercurioToasts {
    val items = mutableStateListOf<ToastItem>()
    private var nextId = 1L

    private class Rule(val re: Regex, val type: ToastType, val title: (MatchResult) -> String, val desc: ((MatchResult) -> String)? = null)

    private val RULES = listOf(
        Rule(Regex("tormenta el[eé]ctrica cerca", RegexOption.IGNORE_CASE), ToastType.WARNING, { "Tormenta cerca" }, { "Mejor adentro, que eso está feo" }),
        Rule(Regex("lluvia (\\d+)%", RegexOption.IGNORE_CASE), ToastType.WARNING, { "${it.groupValues[1]}% de que llueva" }, { "Es muy probable que llueva hoy, lleva algo por si sales" }),
        Rule(Regex("calor (\\d+)°", RegexOption.IGNORE_CASE), ToastType.WARNING, { "${it.groupValues[1]}°" }, { "El día está muy caliente, lo mejor es usar bloqueador" }),
        Rule(Regex("uv \\d+ por las nubes", RegexOption.IGNORE_CASE), ToastType.WARNING, { "UV por las nubes" }, { "Ni se te ocurra salir sin bloqueador" }),
        Rule(Regex("fr[ií]o de ", RegexOption.IGNORE_CASE), ToastType.WARNING, { "Mañana fría" }, { "Abrígate bien que está haciendo un frío bravo" }),
        Rule(Regex("avisos del clima activados", RegexOption.IGNORE_CASE), ToastType.SUCCESS, { "Avisos listos" }, { "Te aviso de lluvia, calor, frío y más" }),
        Rule(Regex("recordatorio en (.+)", RegexOption.IGNORE_CASE), ToastType.SUCCESS, { "Recordatorio listo" }, { "Te aviso en ${it.groupValues[1]}" }),
        Rule(Regex("copia guardada", RegexOption.IGNORE_CASE), ToastType.SUCCESS, { "Copia guardada" }),
        Rule(Regex("no se pudo (.+)", RegexOption.IGNORE_CASE), ToastType.ERROR, { "No se pudo ${it.groupValues[1].replaceFirstChar { c -> c.lowercase() }}" }, { "Intenta de nuevo en un momento" }),
    )

    fun show(msg: String) {
        val r = RULES.firstOrNull { it.re.containsMatchIn(msg) }
        if (r != null) {
            val m = r.re.find(msg)!!
            push(r.type, r.title(m), r.desc?.invoke(m))
        } else {
            push(ToastType.INFO, msg, null)
        }
    }

    fun success(title: String, desc: String? = null) = push(ToastType.SUCCESS, title, desc)
    fun info(title: String, desc: String? = null) = push(ToastType.INFO, title, desc)
    fun warning(title: String, desc: String? = null) = push(ToastType.WARNING, title, desc)
    fun error(title: String, desc: String? = null) = push(ToastType.ERROR, title, desc)

    fun push(type: ToastType, title: String, desc: String?) {
        // evita duplicados idénticos consecutivos
        if (items.any { it.title == title && it.desc == desc }) return
        items.add(ToastItem(nextId++, type, title, desc))
        while (items.size > 3) items.removeAt(0)
    }

    fun dismiss(item: ToastItem) {
        items.remove(item)
    }

    @Deprecated("usa dismiss(item)")
    fun dismiss(msg: String) {
        items.removeAll { it.title == msg }
    }
}

@Composable
fun MercurioToastHost() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MercurioToasts.items.toList().forEach { item ->
                key(item.id) { ToastCard(item) }
            }
        }
    }
}

@Composable
private fun ToastCard(item: ToastItem) {
    val visible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(item.id) {
        visible.value = true
        delay(item.type.ms)
        visible.value = false
        delay(260)
        MercurioToasts.dismiss(item)
    }
    AnimatedVisibility(
        visible = visible.value,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(220)),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF2111118),
            border = BorderStroke(1.dp, item.type.color.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier.clickable { visible.value = false },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(24.dp).background(item.type.color.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.type.glyph, color = item.type.color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (!item.desc.isNullOrBlank()) {
                        Text(item.desc, color = Color(0xFFB4B4C0), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
