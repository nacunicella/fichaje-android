package com.cunicella.fichaje.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Modo discreto: con un toque se tapan todos los montos (pantallas, widget y notificación). */
object Privacy {
    private var prefs: SharedPreferences? = null
    var hidden by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("privacy", Context.MODE_PRIVATE)
            hidden = prefs?.getBoolean("hidden", false) ?: false
        }
    }

    fun toggle() {
        hidden = !hidden
        prefs?.edit()?.putBoolean("hidden", hidden)?.apply()
    }
}
