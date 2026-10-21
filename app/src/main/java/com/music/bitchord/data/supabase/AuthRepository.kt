package com.music.bitchord.data.supabase

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            MercurioSupabase.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Unit
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            MercurioSupabase.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            Unit
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { MercurioSupabase.client.auth.signOut() }
    }

    fun currentUserEmail(): String? = MercurioSupabase.client.auth.currentUserOrNull()?.email
}