package com.music.bitchord.ui.screens.mercurio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.notes.NotesRepository
import com.music.bitchord.ui.toast.MercurioToasts

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var notes by remember { mutableStateOf<List<com.music.bitchord.data.notes.MercurioNote>>(NotesRepository.load(ctx)) }
    var text by remember { mutableStateOf("") }
    val colors = listOf(0xFF6366F1.toInt(), 0xFF22C55E.toInt(), 0xFFF59E0B.toInt(), 0xFFEC4899.toInt())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Nueva nota") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (text.isNotBlank()) {
                    NotesRepository.add(ctx, text.trim(), colors[notes.size % colors.size])
                    notes = NotesRepository.load(ctx)
                    text = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(notes, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).padding(end = 8.dp))
                        Text(n.text, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            NotesRepository.scheduleAndStore(ctx, n.id, n.text, 60 * 60 * 1000L)
                            notes = NotesRepository.load(ctx)
                            MercurioToasts.show("Recordatorio en 1h")
                        }) { Text("1h") }
                        TextButton(onClick = {
                            NotesRepository.delete(ctx, n.id)
                            notes = NotesRepository.load(ctx)
                        }) { Text("X") }
                    }
                }
            }
        }
    }
}