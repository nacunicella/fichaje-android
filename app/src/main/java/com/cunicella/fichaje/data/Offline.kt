package com.cunicella.fichaje.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

/** Una fichada hecha en el teléfono. Se aplica a la foto local al instante y se manda al servidor en orden. */
@Serializable
data class Op(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,              // start | pause | resume | stop | note
    val at: Instant,
    val memberId: String,
    val shiftId: String? = null,   // puede ser un id local ("local-…") hasta que se sincronice
    val holiday: Boolean = false,
    val rate: Double = 0.0,
    val note: String? = null,
    val edit: ShiftDraft? = null,  // para EDIT: cómo tiene que quedar la jornada
) {
    companion object {
        const val START = "start"; const val PAUSE = "pause"; const val RESUME = "resume"; const val STOP = "stop"; const val NOTE = "note"; const val EDIT = "edit"
        fun localId() = "local-" + UUID.randomUUID().toString()
    }
}

fun Snapshot.mapShift(id: String, f: (Shift) -> Shift): Snapshot = copy(
    mineToday = mineToday.map { if (it.id == id) f(it) else it },
    openAll = openAll.map { if (it.id == id) f(it) else it }.filter { Calc.isOpen(it) },
)

fun Shift.closePauses(at: Instant) = copy(pauses = pauses.map { if (it.endAt == null) it.copy(endAt = at) else it })

/** Resultado esperado de una fichada sobre la foto local. */
fun Snapshot.apply(op: Op): Snapshot = when (op.kind) {
    Op.START -> {
        val s = Shift(id = op.shiftId ?: Op.localId(), memberId = op.memberId, startAt = op.at, holiday = op.holiday)
        copy(mineToday = mineToday + s, openAll = openAll + s)
    }
    Op.PAUSE -> mapShift(op.shiftId!!) { it.copy(pauses = it.pauses + Pause("local-" + op.id, it.id, op.at)) }
    Op.RESUME -> mapShift(op.shiftId!!) { it.closePauses(op.at) }
    Op.STOP -> mapShift(op.shiftId!!) { it.closePauses(op.at).copy(endAt = op.at, rate = op.rate.takeIf { r -> r > 0 }) }
    Op.NOTE -> mapShift(op.shiftId!!) { it.copy(note = op.note) }
    Op.EDIT -> mapShift(op.shiftId!!) { op.edit!!.asShift(it) }
    else -> this
}

/**
 * Modo sin conexión. La última foto del servidor queda guardada en el teléfono; las fichadas
 * que no se pudieron mandar quedan en cola y se reintentan, en orden, en cuanto hay señal.
 */
object Offline {
    private var prefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    /** true cuando el último intento de hablar con el servidor falló por red. */
    val offline = MutableStateFlow(false)
    /** Cantidad de fichadas esperando señal. */
    val pendingCount = MutableStateFlow(0)
    /** Fichadas que el servidor rechazó al sincronizar (p. ej. la jornada ya estaba cerrada). */
    val dropped = MutableStateFlow(0)

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("offline", Context.MODE_PRIVATE)
            pendingCount.value = pending().size
        }
    }

    // ---- cola

    private fun pending(): List<Op> = prefs?.getString("ops", null)?.let { runCatching { json.decodeFromString(ListSerializer(Op.serializer()), it) }.getOrNull() } ?: emptyList()
    private fun savePending(ops: List<Op>) {
        prefs?.edit()?.putString("ops", json.encodeToString(ListSerializer(Op.serializer()), ops))?.apply()
        pendingCount.value = ops.size
    }
    fun hasPending() = pendingCount.value > 0

    private fun idMap(): Map<String, String> = prefs?.getString("ids", null)?.let { runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull() } ?: emptyMap()
    private fun mapId(local: String, server: String) {
        prefs?.edit()?.putString("ids", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), idMap() + (local to server)))?.apply()
    }
    private fun resolve(id: String?): String? = id?.let { idMap()[it] ?: it }

    // ---- foto local

    fun cache(s: Snapshot) {
        prefs?.edit()?.putString("snap", runCatching { json.encodeToString(Snapshot.serializer(), s) }.getOrNull())?.putLong("snapAt", System.currentTimeMillis())?.apply()
    }

    /** La última foto del servidor con las fichadas pendientes ya aplicadas. */
    fun localSnapshot(): Snapshot? {
        val base = prefs?.getString("snap", null)?.let { runCatching { json.decodeFromString(Snapshot.serializer(), it) }.getOrNull() } ?: return null
        return pending().fold(base) { s, op -> s.apply(op) }
    }

    /** Última carga completa de Equipo/Historial/Cobrar, para mostrarla sin señal (solo lectura). */
    fun cacheStore(s: Store) {
        prefs?.edit()?.putString("store", runCatching { json.encodeToString(Store.serializer(), s) }.getOrNull())?.apply()
    }
    fun localStore(): Store? = prefs?.getString("store", null)?.let { runCatching { json.decodeFromString(Store.serializer(), it) }.getOrNull() }

    // ---- red

    fun isNetError(e: Throwable): Boolean {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            val n = t.javaClass.simpleName
            if (t is IOException || t is Supa.NoSession || n.contains("Timeout") || n.contains("HttpRequestException") || n.contains("UnresolvedAddress") || n.contains("ConnectException")) return true
            t = t.cause; depth++
        }
        return false
    }

    /**
     * Manda una fichada. Si hay cola o no hay señal, la deja en cola (la foto local ya la muestra).
     * Devuelve true si quedó pendiente.
     */
    suspend fun submit(op: Op): Boolean = lock.withLock {
        val queue = pending()
        if (queue.isEmpty()) {
            val r = runCatching { if (!Supa.ensureSession()) throw Supa.NoSession(); execute(op) }
            if (r.isSuccess) { offline.value = false; return@withLock false }
            val e = r.exceptionOrNull()!!
            if (!isNetError(e)) throw e
        }
        savePending(queue + op)
        offline.value = true
        flushLocked()
    }

    /** Reintenta la cola en orden. Devuelve true si todavía queda algo pendiente. */
    suspend fun flush(): Boolean = lock.withLock { flushLocked() }

    private suspend fun flushLocked(): Boolean {
        var queue = pending()
        if (queue.isEmpty()) return false
        if (!Supa.ensureSession()) { offline.value = true; return true }
        while (queue.isNotEmpty()) {
            val op = queue.first()
            val r = runCatching { execute(op) }
            val e = r.exceptionOrNull()
            if (e != null && isNetError(e)) { offline.value = true; savePending(queue); return true }
            if (e != null) dropped.value += 1 // el servidor la rechazó: gana lo que ya estaba
            queue = queue.drop(1)
            savePending(queue)
        }
        offline.value = false
        return false
    }

    private suspend fun execute(op: Op) {
        val id = resolve(op.shiftId)
        when (op.kind) {
            Op.START -> {
                val saved = Repo.start(op.memberId, op.holiday, op.at)
                op.shiftId?.let { mapId(it, saved.id) }
            }
            Op.PAUSE -> Repo.pause(id!!, op.at)
            Op.RESUME -> Repo.resume(id!!, op.at)
            Op.STOP -> Repo.stopById(id!!, op.rate, op.at)
            Op.NOTE -> Repo.setNote(id!!, op.note)
            Op.EDIT -> Repo.saveEditById(id!!, op.edit!!, op.rate)
        }
    }
}
