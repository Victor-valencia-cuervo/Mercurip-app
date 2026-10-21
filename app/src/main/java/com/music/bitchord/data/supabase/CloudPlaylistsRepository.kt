package com.music.bitchord.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class CloudPlaylist(val id: String = "", val name: String = "", val user_id: String = "")

@Serializable
data class CloudTrack(val id: String = "", val playlist_id: String = "", val title: String = "", val artist: String = "", val url: String = "")

// Filas a insertar: sin "id" para que Postgres genere el suyo (default gen_random_uuid()).
@Serializable
private data class CloudPlaylistInsert(val name: String, val user_id: String)

@Serializable
private data class CloudTrackInsert(val playlist_id: String, val title: String, val artist: String, val url: String)

object CloudPlaylistsRepository {
    suspend fun list(): List<CloudPlaylist> = withContext(Dispatchers.IO) {
        MercurioSupabase.client.postgrest.from("playlists").select().decodeList<CloudPlaylist>()
    }

    suspend fun create(name: String, userId: String): CloudPlaylist = withContext(Dispatchers.IO) {
        MercurioSupabase.client.postgrest.from("playlists")
            .insert(CloudPlaylistInsert(name = name, user_id = userId)) { select() }
            .decodeSingle<CloudPlaylist>()
    }

    suspend fun addTrack(playlistId: String, title: String, artist: String, url: String) = withContext(Dispatchers.IO) {
        MercurioSupabase.client.postgrest.from("playlist_tracks")
            .insert(CloudTrackInsert(playlist_id = playlistId, title = title, artist = artist, url = url))
    }
}