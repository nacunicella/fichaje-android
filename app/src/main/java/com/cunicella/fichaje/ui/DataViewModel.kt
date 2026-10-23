package com.cunicella.fichaje.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cunicella.fichaje.data.Audit
import com.cunicella.fichaje.data.AuditItem
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.widget.ClockWidget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aviso de "cambió algo en la base" para que las pantallas que no lo hicieron se recarguen. */
object Sync {
    private val _changes = MutableSharedFlow<Any>(extraBufferCapacity = 8)
    val changes: SharedFlow<Any> = _changes
    fun changed(source: Any) { _changes.tryEmit(source) }
}

data class DataUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val store: Store? = null,
    val error: String? = null,
    val toast: String? = null,
    val audit: List<AuditItem> = emptyList(),
    val auditLoading: Boolean = false,
    val auditDone: Boolean = false,
)

/** Datos de Equipo, Historial y Cobrar. Hoy tiene su propio modelo, más liviano, para el reloj. */
class DataViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(DataUi())
    val ui: StateFlow<DataUi> = _ui

    init {
        refresh()
        viewModelScope.launch { Sync.changes.collect { if (it !== this@DataViewModel) refresh(quiet = true) } }
    }

    fun refresh(quiet: Boolean = false) = viewModelScope.launch {
        if (!quiet) _ui.update { it.copy(error = null) }
        runCatching { Store.load() }
            .onSuccess { s -> _ui.update { it.copy(loading = false, store = s, error = null) } }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = if (quiet && it.store != null) it.error else "No se pudo cargar. ${Supa.friendly(e)}") } }
        if (_ui.value.audit.isNotEmpty()) loadAudit(reset = true)
    }

    /** Guarda, recarga y avisa a las demás pantallas. Devuelve el mensaje a mostrar. */
    fun run(done: String? = null, block: suspend (Store) -> Unit) {
        val store = _ui.value.store ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null, toast = null) }
        viewModelScope.launch {
            val result = runCatching {
                if (!Supa.ensureSession()) throw Supa.NoSession()
                block(store)
            }
            val fresh = runCatching { Store.load() }.getOrNull()
            _ui.update {
                it.copy(
                    busy = false,
                    store = fresh ?: it.store,
                    error = result.exceptionOrNull()?.let { e -> "No se pudo guardar. ${Supa.friendly(e)}" },
                    toast = if (result.isSuccess) done else null,
                )
            }
            if (result.isSuccess) {
                Sync.changed(this@DataViewModel)
                ClockWidget.refreshAll(getApplication())
                if (_ui.value.audit.isNotEmpty()) loadAudit(reset = true)
            }
        }
    }

    fun clearToast() = _ui.update { it.copy(toast = null) }

    private val pageSize = 200L

    fun loadAudit(reset: Boolean = false) {
        val cur = _ui.value
        if (cur.auditLoading) return
        if (!reset && cur.auditDone) return
        val store = cur.store ?: return
        _ui.update { it.copy(auditLoading = true) }
        viewModelScope.launch {
            val before = if (reset) null else cur.audit.lastOrNull()?.at
            runCatching { Repo.audit(before, pageSize) }
                .onSuccess { rows ->
                    val items = Audit.translate(rows, store)
                    _ui.update {
                        it.copy(
                            auditLoading = false,
                            audit = if (reset) items else it.audit + items,
                            auditDone = rows.size < pageSize,
                        )
                    }
                }
                .onFailure { _ui.update { it.copy(auditLoading = false) } }
        }
    }
}
