package com.music.bitchord.ui.screens.mercurio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.supabase.AuthRepository
import com.music.bitchord.data.supabase.CloudPlaylist
import com.music.bitchord.data.supabase.CloudPlaylistsRepository
import com.music.bitchord.data.supabase.MercurioSupabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun CloudScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(AuthRepository.currentUserEmail()) }
    var playlists by remember { mutableStateOf<List<CloudPlaylist>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        if (user != null) {
            loading = true
            try { playlists = CloudPlaylistsRepository.list() } catch (e: Exception) { status = e.message }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            scope.launch {
                loading = true
                MercurioSupabase.checkConnection()
                    .onSuccess { status = it }
                    .onFailure { status = "Sin conexión o falta el esquema: ${it.message}" }
                loading = false
            }
        }) { Text("Probar conexión") }
        Spacer(Modifier.height(8.dp))
        if (user == null) {
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = {
                    scope.launch {
                        loading = true
                        AuthRepository.signIn(email, password).onSuccess { user = AuthRepository.currentUserEmail(); status = "Sesión iniciada" }.onFailure { status = it.message }
                        loading = false
                    }
                }) { Text("Entrar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch {
                        loading = true
                        AuthRepository.signUp(email, password).onSuccess { user = AuthRepository.currentUserEmail(); status = "Cuenta creada" }.onFailure { status = it.message }
                        loading = false
                    }
                }) { Text("Registrar") }
            }
        } else {
            Text("Usuario: $user")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    AuthRepository.signOut()
                    user = null
                    playlists = emptyList()
                }
            }) { Text("Salir") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    try {
                        val p = CloudPlaylistsRepository.create("Nueva playlist", MercurioSupabase.client.auth.currentUserOrNull()?.id ?: "")
                        playlists = playlists + p
                    } catch (e: Exception) { status = e.message }
                }
            }) { Text("Crear playlist") }
            Spacer(Modifier.height(8.dp))
            if (loading) CircularProgressIndicator()
            LazyColumn {
                items(playlists, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(p.name, Modifier.padding(12.dp))
                    }
                }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}