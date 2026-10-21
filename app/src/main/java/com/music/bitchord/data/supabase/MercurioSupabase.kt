package com.music.bitchord.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MercurioSupabase {
    const val URL = "https://ubfgfxjfwmasbkptsnay.supabase.co"
    const val KEY = "sb_publishable_YeKf7TTGsv-Vcai8il549A_2sboEbHU"

    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = URL, supabaseKey = KEY) {
            install(Auth)
            install(Postgrest)
        }
    }

    /**
     * Comprueba URL, clave y tablas: pide 1 fila de `playlists`.
     * Ok -> conectado y esquema creado. Error "relation ... does not exist" -> falta correr mercurio_supabase.sql.
     */
    suspend fun checkConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest.from("playlists").select { limit(1) }
            "Conectado a Supabase y tabla playlists accesible"
        }
    }
}
