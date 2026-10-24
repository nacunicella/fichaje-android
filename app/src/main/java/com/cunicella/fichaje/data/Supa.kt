package com.cunicella.fichaje.data

import com.cunicella.fichaje.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.exceptions.RestException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** Cliente único de Supabase, compartido por la app y el widget. */
object Supa {
    val client: SupabaseClient by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            requestTimeout = 15.seconds
            install(Auth)
            install(Postgrest)
        }
    }

    /** Espera a que la sesión guardada termine de cargar (lo necesita el widget, que arranca en frío). */
    suspend fun awaitSession(): Boolean {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated || status is SessionStatus.RefreshFailure
    }

    private val refreshLock = Mutex()

    /**
     * Deja una sesión válida antes de consultar. Si el token venció (p. ej. la app estuvo
     * una hora en segundo plano) o falló el refresco automático, lo renueva ahora: sin esto
     * la consulta sale con la clave anónima y Postgres responde "permission denied".
     */
    suspend fun ensureSession(): Boolean = refreshLock.withLock {
        val auth = client.auth
        val status = auth.sessionStatus.first { it !is SessionStatus.Initializing }
        if (status is SessionStatus.NotAuthenticated) return@withLock false
        val current = auth.currentSessionOrNull()
        if (current == null || current.expiresAt < Clock.System.now() + 60.seconds) {
            val stored = current ?: runCatching { auth.sessionManager.loadSession() }.getOrNull()
            if (stored != null) runCatching { auth.importSession(auth.refreshSession(stored.refreshToken)) }
        }
        val s = auth.currentSessionOrNull()
        s != null && s.expiresAt > Clock.System.now()
    }

    /** Mensaje corto para mostrar, sin URLs ni headers. */
    fun friendly(e: Throwable): String = when (e) {
        is NoSession -> "Sin conexión. Probá de nuevo en un rato."
        is RestException -> e.error
        else -> "Sin conexión. Probá de nuevo en un rato."
    }

    class NoSession : Exception("sin sesión")
}
