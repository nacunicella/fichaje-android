package com.cunicella.fichaje.data

import com.cunicella.fichaje.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.first

/** Cliente único de Supabase, compartido por la app y el widget. */
object Supa {
    val client: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
        }
    }

    /** Espera a que la sesión guardada termine de cargar (lo necesita el widget, que arranca en frío). */
    suspend fun awaitSession(): Boolean {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated || status is SessionStatus.RefreshFailure
    }
}
