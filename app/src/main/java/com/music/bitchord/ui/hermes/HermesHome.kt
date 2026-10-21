package com.music.bitchord.ui.hermes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.notes.NotesRepository
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun hermesPalette(): HermesPalette =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) HermesDark else HermesLight

@Composable
fun hermesAccent(): Color {
    val a by animateColorAsState(HermesState.liveAccent, tween(700), label = "hermesAccent")
    return a
}

/** Fondo de Hermes: degradado teñido con el acento (cambia con la canción). */
@Composable
fun HermesBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = hermesPalette()
    val accent = hermesAccent()
    val top = lerp(p.bg, accent, if (p.dark) 0.30f else 0.18f)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, p.bg, p.bg)))
            .pointerInput(Unit) { },
    ) { content() }
}

@Composable
fun HCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = hermesPalette()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = p.s,
        border = BorderStroke(1.dp, p.bd),
    ) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun HermesMenuButton(onClick: () -> Unit) {
    val p = hermesPalette()
    Box(
        Modifier
            .size(44.dp)
            .background(p.s.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Menu, contentDescription = "Abrir menú", tint = p.t)
    }
}

@Composable
fun HermesHome(
    onMenu: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMusic: () -> Unit,
    songTitle: String,
    songArtist: String,
    playing: Boolean,
    bottomPad: Dp,
) {
    val ctx = LocalContext.current
    remember { HermesContent.load(ctx); true }
    val p = hermesPalette()
    val accent = hermesAccent()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idle by remember { mutableIntStateOf(0) }
    var poke by remember { mutableStateOf<FaceReaction?>(null) }
    val seed = remember { kotlin.random.Random.nextFloat() }
    val notes = remember { NotesRepository.load(ctx) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            idle += 1
            nowMs = System.currentTimeMillis()
        }
    }
    LaunchedEffect(poke) {
        if (poke != null) {
            delay(6000)
            poke = null
        }
    }
    LaunchedEffect(playing) { if (playing) idle = 0 }

    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val loc = Locale("es", "CO")
    val weekday = java.text.SimpleDateFormat("EEEE", loc).format(Date(nowMs))
    val clock = java.text.SimpleDateFormat("HH:mm", loc).format(Date(nowMs))
    val period = if (hour in 5..11) "morning" else if (hour in 12..18) "afternoon" else "evening"
    val greeting = remember(period) {
        val l = HermesContent.greetings[period].orEmpty()
        val g = if (l.isEmpty()) "Hola{n}." else l[(seed * l.size).toInt().coerceIn(0, l.size - 1)]
        g.replace("{n}", if (HermesState.userName.isNotBlank() && seed > 0.35f) ", ${HermesState.userName}" else "")
    }
    val day = (nowMs - TimeZone.getDefault().getOffset(nowMs)) / 86_400_000L
    val quote = HermesContent.quotes.takeIf { it.isNotEmpty() }?.let { it[(((day * 7919L + 13L) % it.size) + it.size).toInt() % it.size] }

    val pending = notes.filter { (it.reminderAt ?: 0L) > nowMs }.sortedBy { it.reminderAt }.take(3)
    val auto = HermesFaces.reactTo(HermesState.weather, hour, playing, songTitle, idle, seed, HermesState.userName)
    val reaction = poke ?: if (pending.isNotEmpty() && auto.kind != "sleep" && auto.kind != "music") {
        FaceReaction("travieso", auto.kind, "${auto.say} Tienes ${pending.size} pendiente${if (pending.size > 1) "s" else ""} por ahí.")
    } else auto

    HermesBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { HermesMenuButton(onMenu) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "MERCURIO",
                    fontFamily = FontFamily.Serif,
                    fontSize = 40.sp,
                    letterSpacing = 10.sp,
                    color = p.t,
                )
                Spacer(Modifier.height(10.dp))
                Text(weekday.uppercase(Locale.ROOT), fontSize = 11.sp, letterSpacing = 3.sp, color = p.m)
                Text(clock, fontSize = 14.sp, color = accent)
                Spacer(Modifier.height(12.dp))
                Text(
                    "“$greeting”",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    color = p.m,
                    textAlign = TextAlign.Center,
                )
            }

            HCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PixelFace(
                        face = reaction.face,
                        kind = reaction.kind,
                        accent = accent,
                        onClick = {
                            val faces = HermesContent.allFaces.filter { it != reaction.face }
                            if (faces.isNotEmpty()) {
                                val says = HermesContent.pokeSays
                                poke = FaceReaction(
                                    faces[kotlin.random.Random.nextInt(faces.size)], "poke",
                                    if (says.isEmpty()) "¡Ey!" else says[kotlin.random.Random.nextInt(says.size)],
                                )
                                idle = 0
                            }
                        },
                    )
                    Text(reaction.say, color = p.t, fontSize = 14.sp, textAlign = TextAlign.Center)
                    val w = HermesState.weather
                    if (w != null) {
                        Text(
                            "${w.temp}${w.sym} · ${w.title} · máx ${w.max}° / mín ${w.min}°" + (if (w.pop >= 30) " · ☂ ${w.pop}%" else "") + " →",
                            color = p.m,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable(onClick = onOpenWeather),
                        )
                    }
                }
            }

            if (quote != null) {
                HCard {
                    Column {
                        Text("❝", color = accent, fontSize = 28.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(quote.t, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = p.t)
                        Spacer(Modifier.height(10.dp))
                        Text(quote.a.uppercase(Locale.ROOT), fontSize = 11.sp, letterSpacing = 2.sp, color = accent)
                    }
                }
            }

            if (songTitle.isNotBlank()) {
                HCard(Modifier.clickable(onClick = onOpenMusic)) {
                    Column {
                        Text(if (playing) "SONANDO AHORA" else "ÚLTIMA CANCIÓN", fontSize = 11.sp, letterSpacing = 2.sp, color = p.m)
                        Spacer(Modifier.height(6.dp))
                        Text(songTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = p.t, maxLines = 1)
                        if (songArtist.isNotBlank()) Text(songArtist, fontSize = 13.sp, color = accent, maxLines = 1)
                    }
                }
            }

            HCard(Modifier.clickable(onClick = onOpenNotes)) {
                Column {
                    Text("PRÓXIMOS RECORDATORIOS", fontSize = 11.sp, letterSpacing = 2.sp, color = p.m)
                    Spacer(Modifier.height(10.dp))
                    if (pending.isEmpty()) {
                        Text("Nada pendiente.", fontSize = 14.sp, color = p.m)
                    } else {
                        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", loc)
                        pending.forEach { n ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⏰", fontSize = 14.sp)
                                Spacer(Modifier.size(8.dp))
                                Text(n.text, color = p.t, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.size(8.dp))
                                Text(fmt.format(Date(n.reminderAt ?: 0L)), color = p.m, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
