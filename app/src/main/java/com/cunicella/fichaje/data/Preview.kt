package com.cunicella.fichaje.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Vista previa para el admin: la app se ve como la ve un usuario normal. Solo cambia la pantalla, no el rol. */
object Preview {
    private var prefs: SharedPreferences? = null
    var asUser by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("preview", Context.MODE_PRIVATE)
            asUser = prefs?.getBoolean("asUser", false) ?: false
        }
    }

    fun set(v: Boolean) {
        asUser = v
        prefs?.edit()?.putBoolean("asUser", v)?.apply()
    }
}
