package com.music.bitchord.ui.screens.mercurio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.pets.PetDef
import com.music.bitchord.ui.pets.PetPrefs
import com.music.bitchord.ui.pets.PetStore
import com.music.bitchord.ui.pets.PetsBus
import com.music.bitchord.ui.toast.MercurioToasts
import kotlin.math.roundToInt

private const val MAX_PETS = 5

@Composable
fun PetsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    remember {
        PetStore.load(ctx)
        PetPrefs.init(ctx)
        true
    }
    val all = PetStore.catalog
    fun persist() = PetPrefs.save(ctx)
    var showCreate by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingRow("Mascotas activas", PetPrefs.enabled) { PetPrefs.enabled = it; persist() }
                        SettingRow("Hablar (globos de texto)", PetPrefs.talk) { PetPrefs.talk = it; persist() }
                        SettingRow("Modo flotante (si no, caminan por el suelo)", PetPrefs.floatMode) { PetPrefs.floatMode = it; persist() }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Tamaño", Modifier.padding(end = 4.dp))
                            listOf("S" to 0.8f, "M" to 1f, "L" to 1.3f).forEach { (label, v) ->
                                FilterChip(
                                    selected = PetPrefs.size == v,
                                    onClick = { PetPrefs.size = v; persist() },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            "Elige hasta $MAX_PETS (${PetPrefs.selected.size}/$MAX_PETS). Tócalas en pantalla para jugar, arrástralas y lánzalas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                val id = PetPrefs.selected.firstOrNull()
                                if (id == null) MercurioToasts.warning("Elige una mascota", "Marca al menos una para probar su poder")
                                else PetsBus.testSpecialFor = id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Probar poder de la primera elegida") }
                        OutlinedButton(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Crear mi mascota (con tu imagen)") }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                items(all, key = { it.id }) { def ->
                    val on = def.id in PetPrefs.selected
                    val del: (() -> Unit)? = if (PetStore.isCustom(def.id)) {
                        {
                            PetStore.removeCustom(ctx, def.id)
                            PetPrefs.selected = PetPrefs.selected - def.id
                            persist()
                        }
                    } else null
                    PetCard(def, on, del) {
                        val cur = PetPrefs.selected
                        if (on) {
                            PetPrefs.selected = cur - def.id
                        } else if (cur.size >= MAX_PETS) {
                            MercurioToasts.warning("Máximo $MAX_PETS mascotas", "Quita una para poner otra")
                        } else {
                            PetPrefs.selected = cur + def.id
                            if (!PetPrefs.enabled) PetPrefs.enabled = true
                        }
                        persist()
                    }
                }
            }
        }
    }
    if (showCreate) {
        CreatePetDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, uri, phrases ->
                val id = PetStore.addCustom(ctx, name, uri, phrases)
                if (id == null) {
                    MercurioToasts.error("No se pudo leer la imagen", "Prueba con otra foto o un PNG")
                } else {
                    PetPrefs.selected = (PetPrefs.selected + id).takeLast(MAX_PETS)
                    PetPrefs.enabled = true
                    persist()
                    MercurioToasts.success("Mascota creada", "Ya está caminando por tu pantalla")
                }
                showCreate = false
            },
        )
    }
}

@Composable
private fun CreatePetDialog(onDismiss: () -> Unit, onCreate: (String, android.net.Uri, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phrases by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) uri = u }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear mi mascota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(
                    value = phrases,
                    onValueChange = { phrases = it },
                    label = { Text("Frases (una por línea)") },
                    minLines = 3,
                )
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uri == null) "Elegir imagen" else "Imagen elegida ✓")
                }
                Text("Tip: usa un PNG con fondo transparente para que se vea mejor.", fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = uri != null,
                onClick = { uri?.let { onCreate(name, it, phrases.lines()) } },
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SettingRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun PetCard(def: PetDef, selected: Boolean, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(def.id) { PetStore.sprite(ctx, def) }
    val cell = def.frames["idle"]?.firstOrNull() ?: 0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                if (bmp != null) {
                    Canvas(Modifier.size(76.dp)) {
                        val scale = minOf(size.width / def.cellW, size.height / def.cellH)
                        val dw = (def.cellW * scale).roundToInt()
                        val dh = (def.cellH * scale).roundToInt()
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset(cell * def.cellW, 0),
                            srcSize = IntSize(def.cellW, def.cellH),
                            dstOffset = IntOffset(((size.width - dw) / 2f).roundToInt(), (size.height - dh).roundToInt()),
                            dstSize = IntSize(dw, dh),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
            }
            Text(
                def.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (def.special != null) Text("⚡ poder", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar", fontSize = 11.sp) }
        }
    }
}
