package com.music.bitchord.ui.hermes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

class HermesNavItem(val id: String, val label: String, val emoji: String)

val HERMES_NAV = listOf(
    HermesNavItem("home", "Inicio", "🏠"),
    HermesNavItem("music", "Música", "🎵"),
    HermesNavItem("weather", "Clima", "🌤️"),
    HermesNavItem("notes", "Notas y recordatorios", "📝"),
    HermesNavItem("pets", "Mascotas", "🐾"),
    HermesNavItem("cloud", "Nube y perfil", "☁️"),
    HermesNavItem("settings", "Ajustes", "⚙️"),
)

/** Menú lateral (hamburguesa) al estilo Hermes. */
@Composable
fun HermesDrawer(
    open: Boolean,
    current: String,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    petsOn: Boolean,
    onTogglePets: () -> Unit,
) {
    val p = hermesPalette()
    val accent = hermesAccent()
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            )
        }
        AnimatedVisibility(
            open,
            enter = slideInHorizontally(tween(300)) { -it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(p.s, RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                    .pointerInput(Unit) { }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MERCURIO",
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                        letterSpacing = 6.sp,
                        color = accent,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = p.m, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HERMES_NAV.forEach { item ->
                        val active = item.id == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { onSelect(item.id) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.emoji, fontSize = 18.sp)
                            Spacer(Modifier.size(14.dp))
                            Text(
                                item.label,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (active) accent else p.t,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("v1.0", fontSize = 10.sp, letterSpacing = 2.sp, color = p.m, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onTogglePets),
                        contentAlignment = Alignment.Center,
                    ) { Text("🐾", fontSize = 18.sp, modifier = Modifier.alpha(if (petsOn) 1f else 0.35f)) }
                }
            }
        }
    }
}

/** Marco común para Clima, Notas, Mascotas y Nube: mismo fondo, encabezado y menú que el inicio. */
@Composable
fun HermesFrame(
    title: String,
    onMenu: () -> Unit,
    bottomPad: Dp,
    content: @Composable () -> Unit,
) {
    val p = hermesPalette()
    HermesBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HermesMenuButton(onMenu)
                Spacer(Modifier.size(14.dp))
                Text(title, fontFamily = FontFamily.Serif, fontSize = 26.sp, color = p.t)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPad)) { content() }
        }
    }
}

/** Mini reproductor de las pantallas Hermes (el de BitChord queda debajo del marco). */
@Composable
fun HermesMiniPlayer(
    title: String,
    artist: String,
    coverUrl: String?,
    playing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = hermesPalette()
    val accent = hermesAccent()
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.s.copy(alpha = 0.96f))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(p.s2)) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.t, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (artist.isNotBlank()) Text(artist, color = p.m, fontSize = 12.sp, maxLines = 1)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 15.sp) }
    }
}
