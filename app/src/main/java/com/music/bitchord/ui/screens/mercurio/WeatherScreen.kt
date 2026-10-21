package com.music.bitchord.ui.screens.mercurio

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.weather.WeatherCurrent
import com.music.bitchord.data.weather.WeatherRepository
import com.music.bitchord.ui.hermes.HCard
import com.music.bitchord.ui.hermes.HermesState
import com.music.bitchord.ui.hermes.hermesPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun heroColors(code: Int, isDay: Boolean): List<Color> {
    val group = when {
        code == 0 || code == 1 -> "sun"
        code == 2 -> "part"
        code == 3 -> "cloud"
        code == 45 || code == 48 -> "fog"
        code in 51..67 || code in 80..82 -> "rain"
        code >= 95 -> "storm"
        code in 71..77 || code == 85 || code == 86 -> "snow"
        else -> "part"
    }
    if (!isDay && (group == "sun" || group == "part")) return listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
    return when (group) {
        "sun" -> listOf(Color(0xFF38BDF8), Color(0xFFFBBF24))
        "part" -> listOf(Color(0xFF7DD3FC), Color(0xFF94A3B8))
        "cloud" -> listOf(Color(0xFF94A3B8), Color(0xFF64748B))
        "fog" -> listOf(Color(0xFFA8A29E), Color(0xFF78716C))
        "rain" -> listOf(Color(0xFF475569), Color(0xFF312E81))
        "storm" -> listOf(Color(0xFF334155), Color(0xFF1E1B4B))
        else -> listOf(Color(0xFFBAE6FD), Color(0xFFCBD5E1))
    }
}

private fun icon(code: Int, isDay: Boolean): String = when {
    code == 0 || code == 1 -> if (isDay) "☀️" else "🌙"
    code == 2 -> if (isDay) "⛅" else "☁️"
    code == 3 -> "☁️"
    code == 45 || code == 48 -> "🌫️"
    code in 51..67 || code in 80..82 -> "🌧️"
    code in 71..77 || code == 85 || code == 86 -> "❄️"
    code >= 95 -> "⛈️"
    else -> "🌤️"
}

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    val p = hermesPalette()
    var weather by remember { mutableStateOf<WeatherCurrent?>(HermesState.weather) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(weather == null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        try {
            val w = withContext(Dispatchers.IO) { WeatherRepository.fetch(5.8747, -75.5667, true) }
            weather = w
            HermesState.weather = w
            error = null
        } catch (e: Exception) {
            error = e.message ?: "sin conexión"
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val w = weather
        if (w == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text("No se pudo cargar el clima: ${error ?: ""}", color = p.m, textAlign = TextAlign.Center)
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(heroColors(w.code, w.isDay)))
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(icon(w.code, w.isDay), fontSize = 56.sp)
                    Text("${w.temp}${w.sym}", fontFamily = FontFamily.Serif, fontSize = 64.sp, color = Color.White)
                    Text(w.title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Máx ${w.max}° · Mín ${w.min}°", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(10.dp))
                    Text(w.advice, fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("🌡️", "Sensación", "${w.feels}${w.sym}", Modifier.weight(1f))
                Stat("☂️", "Lluvia", "${w.pop}%", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("🕶️", "Índice UV", "${w.uv}", Modifier.weight(1f))
                Stat("🌓", "Ahora", if (w.isDay) "Día" else "Noche", Modifier.weight(1f))
            }
            if (error != null) Text("Sin conexión: mostrando lo último guardado.", color = p.m, fontSize = 12.sp)
        }
        Text(
            if (loading && weather != null) "Actualizando…" else "↻ Actualizar",
            color = p.m,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable { reload++ }.padding(12.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Stat(emoji: String, label: String, value: String, modifier: Modifier) {
    val p = hermesPalette()
    Box(modifier) {
        HCard {
            Column {
                Text("$emoji  $label", fontSize = 12.sp, color = p.m)
                Spacer(Modifier.height(6.dp))
                Text(value, fontSize = 24.sp, fontFamily = FontFamily.Serif, color = p.t)
            }
        }
    }
}
