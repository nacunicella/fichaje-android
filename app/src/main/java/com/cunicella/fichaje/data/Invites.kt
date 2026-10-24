package com.cunicella.fichaje.data

import android.content.Context
import android.content.SharedPreferences

/** Código de invitación escrito en la pantalla de entrada; se canjea en cuanto hay sesión. */
object Invites {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("invites", Context.MODE_PRIVATE)
    }

    fun pending(): String? = prefs?.getString("code", null)?.takeIf { it.isNotBlank() }
    fun set(code: String) { prefs?.edit()?.putString("code", code.trim().uppercase())?.apply() }
    fun clear() { prefs?.edit()?.remove("code")?.apply() }

    /** Texto para compartir por WhatsApp o lo que sea. */
    fun shareText(company: String, name: String, code: String): String =
        "Hola $name, sumate al equipo de $company para fichar tus horas.\n\n" +
            "1) Instalá la app: https://github.com/nacunicella/fichaje-android/releases/latest/download/fichaje.apk\n" +
            "2) Abrila, tocá \"Tengo un código\" y poné el código $code con tu email y una contraseña.\n\n" +
            "Listo, ya quedás vinculado."
}
