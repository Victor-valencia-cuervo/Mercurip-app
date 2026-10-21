package com.music.bitchord.ui.pets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Capa de mascotas por toda la pantalla. Solo los rectángulos de las mascotas reciben toques;
 * el resto (efectos, burbujas, velo) deja pasar los gestos a la app. Se oculta con el full reproductor.
 */
@Composable
fun PetsOverlay(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    remember {
        PetStore.load(ctx)
        PetPrefs.init(ctx)
        true
    }
    val scope = rememberCoroutineScope()
    val world = remember { PetWorld(scope, ctx) }
    val visible = PetPrefs.enabled && !PetsBus.fullPlayerOpen
    val density = LocalDensity.current.density

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        world.w = wPx
        world.h = hPx
        world.d = density
        world.floorY = hPx - PetsBus.bottomInsetDp * density
        world.sizeScale = PetPrefs.size
        world.floatMode = PetPrefs.floatMode
        world.talk = PetPrefs.talk

        LaunchedEffect(PetPrefs.selected, PetPrefs.enabled, wPx) {
            if (PetPrefs.enabled) world.sync(PetPrefs.selected)
        }
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val nw = androidx.compose.runtime.withFrameNanos { it }
                val dt = ((nw - last) / 1_000_000f).coerceAtMost(64f)
                last = nw
                world.tick(dt)
            }
        }
        LaunchedEffect(PetsBus.weather) {
            val w = PetsBus.weather ?: return@LaunchedEffect
            delay(1500)
            world.weatherSay(w)
        }
        LaunchedEffect(PetsBus.songTitle, PetsBus.playing) {
            if (!PetsBus.playing || PetsBus.songTitle.isBlank()) return@LaunchedEffect
            delay(1200)
            world.musicSay()
        }

        if (visible) {
            FxLayer(world)
            world.actors.forEach { a -> key(a.def.id) { PetView(a, world) } }
            world.actors.forEach { a -> key("b_" + a.def.id) { PetBubble(a) } }
        }
    }
}

@Composable
private fun FxLayer(world: PetWorld) {
    val ctx = LocalContext.current
    val bats = remember { PetStore.bats(ctx) }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val tick = world.fxTick
        if (world.veilAlpha > 0.003f) {
            drawRect(Color(world.veilColor).copy(alpha = world.veilAlpha.coerceIn(0f, 1f)))
        }
        val nowMs = System.currentTimeMillis()
        if (bats != null) {
            for (b in world.bats.toList()) {
                val t = ((nowMs - b.t0).toFloat() / b.durMs).coerceIn(0f, 1f)
                val x = b.sx + (b.ex - b.sx) * t
                val y = b.sy + (b.ey - b.sy) * t + sin(t * 2f * PI.toFloat() * 3f) * 22f * density
                val col = if (((nowMs - b.t0) / 130L) % 2L == 0L) 0 else 1
                val dw = (57 * density * 0.8f).toInt()
                val dh = (39 * density * 0.8f).toInt()
                val fade = if (t < 0.1f) t / 0.1f else if (t > 0.9f) (1f - t) / 0.1f else 1f
                drawImage(
                    image = bats,
                    srcOffset = IntOffset(col * 57, b.variant.coerceIn(0, 5) * 39),
                    srcSize = IntSize(57, 39),
                    dstOffset = IntOffset((x - dw / 2f).toInt(), (y - dh / 2f).toInt()),
                    dstSize = IntSize(dw, dh),
                    alpha = fade.coerceIn(0f, 1f),
                    filterQuality = FilterQuality.None,
                )
            }
        }
        for (p in world.particles.toList()) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val fade = if (a < 0.4f) a / 0.4f else 1f
            if (p.emoji.isEmpty()) {
                drawCircle(Color(p.color).copy(alpha = fade * 0.85f), radius = p.size / 2f, center = Offset(p.x, p.y))
            } else {
                drawIntoCanvas { c ->
                    paint.textSize = p.size
                    paint.alpha = (255 * fade).toInt()
                    c.nativeCanvas.drawText(p.emoji, p.x, p.y, paint)
                }
            }
        }
    }
}

@Composable
private fun PetView(a: PetActor, world: PetWorld) {
    val density = LocalDensity.current.density
    val wDp = a.def.dispW * PetPrefs.size
    val hDp = a.def.dispH * PetPrefs.size
    val wPx = wDp * density
    val hPx = hDp * density
    Box(
        Modifier
            .offset { IntOffset((a.x - wPx / 2f + a.jitterX).roundToInt(), (a.y + a.hopY - hPx).roundToInt()) }
            .size(wDp.dp, hDp.dp)
            .graphicsLayer {
                scaleX = a.scaleK * (if (a.dir < 0) -1f else 1f)
                scaleY = a.scaleK
                alpha = a.alpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .pointerInput(a) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val bw = size.width.toFloat()
                    val bh = size.height.toFloat()
                    val grabDX = down.position.x - bw / 2f
                    val grabDY = down.position.y - bh
                    var moved = false
                    var vx = 0f
                    var vy = 0f
                    var prevX = 0f
                    var prevY = 0f
                    var prevT = down.uptimeMillis
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            ch.consume()
                            break
                        }
                        val originX = a.x - bw / 2f + a.jitterX
                        val originY = a.y + a.hopY - bh
                        val rootX = originX + ch.position.x
                        val rootY = originY + ch.position.y
                        if (!moved && !a.busy && (ch.position - down.position).getDistance() > slop) {
                            moved = true
                            world.grab(a)
                            prevX = rootX
                            prevY = rootY
                            prevT = ch.uptimeMillis
                        }
                        if (moved) {
                            val dtMs = (ch.uptimeMillis - prevT).coerceAtLeast(1L).toFloat()
                            vx = 0.6f * vx + 0.4f * ((rootX - prevX) / dtMs * 1000f)
                            vy = 0.6f * vy + 0.4f * ((rootY - prevY) / dtMs * 1000f)
                            prevX = rootX
                            prevY = rootY
                            prevT = ch.uptimeMillis
                            a.x = (rootX - grabDX).coerceIn(bw / 2f, (world.w - bw / 2f).coerceAtLeast(bw / 2f))
                            a.y = (rootY - grabDY).coerceIn(bh, world.h)
                            ch.consume()
                        }
                    }
                    if (moved) {
                        val lim = 1800f * density
                        world.release(a, vx.coerceIn(-lim, lim), vy.coerceIn(-lim, lim))
                    } else {
                        world.poke(a)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawOval(
                Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(size.width * 0.2f, size.height - 5f * density),
                size = Size(size.width * 0.6f, 7f * density),
            )
            drawImage(
                image = a.sprite,
                srcOffset = IntOffset(a.cell * a.def.cellW, 0),
                srcSize = IntSize(a.def.cellW, a.def.cellH),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun PetBubble(a: PetActor) {
    val text = a.bubble ?: return
    val density = LocalDensity.current.density
    Box(
        Modifier.layout { measurable, constraints ->
            val p = measurable.measure(Constraints(maxWidth = (constraints.maxWidth * 0.62f).toInt()))
            layout(p.width, p.height) {
                val petH = a.def.dispH * PetPrefs.size * density * a.scaleK
                val x = (a.x - p.width / 2f).coerceIn(8f, (constraints.maxWidth - p.width - 8).toFloat().coerceAtLeast(8f))
                val y = (a.y + a.hopY - petH - p.height - 6f * density).coerceAtLeast(8f)
                p.place(IntOffset(x.roundToInt(), y.roundToInt()))
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xEE16161E),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
