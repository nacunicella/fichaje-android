package com.cunicella.fichaje.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.datetime.Instant

/** Todo lo que se lee y escribe en Supabase. La auditoría la registra la base sola (triggers). */
object Repo {
    private val db get() = Supa.client
    private val withPauses = Columns.raw("*, pauses(*)")

    suspend fun me(): Member? {
        val uid = db.auth.currentUserOrNull()?.id ?: return null
        val me: Member? = db.from("members").select { filter { eq("user_id", uid); exact("deleted_at", null) } }.decodeSingleOrNull()
        if (me != null) return me
        // Si entró con un código de invitación, lo canjeamos ahora que hay sesión.
        val code = Invites.pending() ?: return null
        val ok = runCatching { claimInvite(code) }.getOrDefault(false)
        if (ok) Invites.clear()
        return if (ok) db.from("members").select { filter { eq("user_id", uid); exact("deleted_at", null) } }.decodeSingleOrNull() else null
    }

    suspend fun settings(): Settings = db.from("settings").select().decodeSingle()

    suspend fun activeMembers(): List<Member> =
        db.from("members").select {
            filter { eq("active", true); exact("deleted_at", null) }
            order("first_name", Order.ASCENDING)
        }.decodeList()

    suspend fun openShifts(): List<Shift> =
        db.from("shifts").select(withPauses) {
            filter {
                exact("end_at", null)
                exact("hours", null)
                exact("deleted_at", null)
            }
        }.decodeList()

    suspend fun shiftsSince(memberId: String, since: Instant): List<Shift> =
        db.from("shifts").select(withPauses) {
            filter {
                eq("member_id", memberId)
                gte("start_at", since.toString())
                exact("deleted_at", null)
            }
            order("start_at", Order.ASCENDING)
        }.decodeList()

    suspend fun start(memberId: String, holiday: Boolean, at: Instant = Clock.System.now()) {
        db.from("shifts").insert(NewShift(memberId, at, holiday))
    }

    suspend fun pause(shiftId: String) {
        db.from("pauses").insert(NewPause(shiftId, Clock.System.now()))
    }

    suspend fun resume(shiftId: String) = closePauses(shiftId, Clock.System.now())

    suspend fun setHoliday(shiftId: String, holiday: Boolean) {
        db.from("shifts").update({ set("holiday", holiday) }) { filter { eq("id", shiftId) } }
    }

    /** Cierra la jornada y congela la tarifa. Devuelve false si duró menos de un minuto (se descarta). */
    suspend fun stop(shift: Shift, rate: Double, at: Instant = Clock.System.now()): Boolean {
        closePauses(shift.id, at)
        val closed = shift.copy(endAt = at, pauses = shift.pauses.map { if (it.endAt == null) it.copy(endAt = at) else it })
        if (Calc.workMs(closed, at) < 60_000) {
            db.from("shifts").delete { filter { eq("id", shift.id) } }
            return false
        }
        db.from("shifts").update({
            set("end_at", at.toString())
            set("rate", rate)
        }) { filter { eq("id", shift.id) } }
        return true
    }

    suspend fun setNote(shiftId: String, note: String?) {
        db.from("shifts").update({ set("note", note?.trim()?.ifBlank { null }) }) { filter { eq("id", shiftId) } }
    }

    /** Guarda lo que se cambió en el editor de jornada: entrada, salida y pausas. */
    suspend fun saveEdit(shift: Shift, d: ShiftDraft, rate: Double) {
        db.from("shifts").update({
            set("start_at", d.start.toString())
            set("end_at", d.end?.toString())
            set<Double?>("hours", null)
            set("note", d.note?.trim()?.ifBlank { null })
            if (d.end != null && shift.rate == null) set("rate", rate)
        }) { filter { eq("id", shift.id) } }
        val keep = d.pauses.mapNotNull { it.id }.toSet()
        shift.pauses.filter { it.id !in keep }.forEach { p ->
            db.from("pauses").delete { filter { eq("id", p.id) } }
        }
        d.pauses.forEach { p ->
            val old = shift.pauses.firstOrNull { it.id == p.id }
            when {
                p.id == null -> db.from("pauses").insert(NewPause(shift.id, p.start, p.end))
                old != null && (old.startAt != p.start || old.endAt != p.end) ->
                    db.from("pauses").update({
                        set("start_at", p.start.toString())
                        set("end_at", p.end?.toString())
                    }) { filter { eq("id", p.id) } }
            }
        }
    }

    // ------------------------------------------------------------ equipo, historial, cobros

    suspend fun allMembers(): List<Member> =
        db.from("members").select { filter { exact("deleted_at", null) }; order("first_name", Order.ASCENDING) }.decodeList()

    /** Todas las jornadas con sus pausas. PostgREST corta en 1000 filas, así que se pide por páginas. */
    suspend fun allShifts(): List<Shift> {
        val page = 1000L
        val out = ArrayList<Shift>()
        var from = 0L
        while (true) {
            val chunk: List<Shift> = db.from("shifts").select(withPauses) {
                filter { exact("deleted_at", null) }
                order("start_at", Order.DESCENDING)
                range(from, from + page - 1)
            }.decodeList()
            out += chunk
            if (chunk.size < page) break
            from += page
        }
        return out
    }

    suspend fun payments(): List<Payment> =
        db.from("payments").select { filter { exact("deleted_at", null) }; order("paid_at", Order.DESCENDING) }.decodeList()

    suspend fun audit(before: Instant?, limit: Long): List<AuditRow> =
        db.from("audit_log").select {
            if (before != null) filter { lt("at", before.toString()) }
            order("at", Order.DESCENDING)
            limit(limit)
        }.decodeList()

    suspend fun addMember(m: NewMember) { db.from("members").insert(m) }

    suspend fun updateMember(id: String, m: NewMember) {
        db.from("members").update({
            set("first_name", m.firstName)
            set("last_name", m.lastName)
            set("email", m.email)
            set("rate", m.rate)
            set("active", m.active)
            set("role", m.role)
        }) { filter { eq("id", id) } }
    }

    /** A la papelera: se puede restaurar durante 30 días. */
    suspend fun deleteMember(id: String) = trash("members", id)

    suspend fun addManual(s: ManualShift, pauseMinutes: Int) {
        val saved: Shift = db.from("shifts").insert(s) { select() }.decodeSingle()
        if (pauseMinutes > 0 && s.hours == null) {
            // una sola pausa, centrada en la jornada (igual que el prototipo)
            val total = (s.endAt - s.startAt).inWholeMilliseconds
            val pz = pauseMinutes * 60_000L
            val st = s.startAt + ((total - pz) / 2).milliseconds
            db.from("pauses").insert(NewPause(saved.id, st, st + pz.milliseconds))
        }
    }

    suspend fun deleteShift(id: String) = trash("shifts", id)

    /** Registra el cobro y marca esas jornadas como cobradas. */
    /** Registra un cobro por monto; lo que falte queda como saldo. worked_seconds guarda el equivalente en horas a la tarifa del momento. */
    suspend fun pay(memberId: String, amount: Double, rate: Double, method: PayMethod?) {
        val seconds = if (rate > 0) (amount / rate * 3600).toLong() else 0L
        db.from("payments").insert(NewPayment(memberId, amount, seconds, 0, method?.key))
    }

    /** Deshace un cobro: el monto vuelve al saldo. */
    suspend fun undoPay(paymentId: String) = trash("payments", id = paymentId)

    // ------------------------------------------------------------ papelera e invitaciones

    private suspend fun trash(table: String, id: String) {
        db.from(table).update({ set("deleted_at", Clock.System.now().toString()) }) { filter { eq("id", id) } }
    }

    suspend fun restore(table: String, id: String) {
        db.from(table).update({ set<String?>("deleted_at", null) }) { filter { eq("id", id) } }
    }

    data class Trash(val shifts: List<Shift>, val payments: List<Payment>, val members: List<Member>)

    suspend fun trash(): Trash = coroutineScope {
        val s = async { db.from("shifts").select(withPauses) { filter { filterNot("deleted_at", FilterOperator.IS, "null") }; order("deleted_at", Order.DESCENDING) }.decodeList<Shift>() }
        val p = async { db.from("payments").select { filter { filterNot("deleted_at", FilterOperator.IS, "null") }; order("deleted_at", Order.DESCENDING) }.decodeList<Payment>() }
        val m = async { db.from("members").select { filter { filterNot("deleted_at", FilterOperator.IS, "null") }; order("deleted_at", Order.DESCENDING) }.decodeList<Member>() }
        Trash(s.await(), p.await(), m.await())
    }

    /** Borra de verdad lo que lleva más de 30 días en la papelera. Falla en silencio si la base todavía no tiene la función. */
    suspend fun purgeTrash() { runCatching { db.postgrest.rpc("purge_trash") } }

    suspend fun newInvite(memberId: String): String =
        db.postgrest.rpc("new_invite", buildJsonObject { put("member", memberId) }).decodeAs<String>()

    suspend fun claimInvite(code: String): Boolean =
        db.postgrest.rpc("claim_invite", buildJsonObject { put("code", code.trim().uppercase()) }).decodeAs<Boolean>()

    suspend fun updateSettings(s: Settings) {
        db.from("settings").update({
            set("company", s.company)
            set("currency", s.currency)
            set("rate", s.rate)
            set("holiday_mult", s.holidayMult)
            set("goal_hours", s.goalHours)
            set("updated_at", Clock.System.now().toString())
        }) { filter { eq("id", 1) } }
    }

    private suspend fun closePauses(shiftId: String, at: Instant) {
        db.from("pauses").update({ set("end_at", at.toString()) }) {
            filter {
                eq("shift_id", shiftId)
                exact("end_at", null)
            }
        }
    }
}

/** Foto de lo que necesitan la pantalla Hoy y el widget. */
data class Snapshot(
    val me: Member?,
    val settings: Settings,
    val members: List<Member>,
    val mineToday: List<Shift>,
    val openAll: List<Shift>,
) {
    val myOpen: Shift? get() = openAll.firstOrNull { it.memberId == me?.id }

    companion object {
        suspend fun load(): Snapshot = coroutineScope {
            if (!Supa.ensureSession()) throw Supa.NoSession()
            // todo en paralelo: es lo que más se nota en la velocidad
            val meQ = async { Repo.me() }
            val settingsQ = async { Repo.settings() }
            val membersQ = async { Repo.activeMembers() }
            val openQ = async { Repo.openShifts() }
            val me = meQ.await()
            val today = if (me == null) emptyList() else Repo.shiftsSince(me.id, Calc.startOfToday())
            val settings = settingsQ.await()
            val members = membersQ.await()
            val open = openQ.await()
            // una jornada abierta desde ayer también cuenta como "hoy"
            val mine = (today + open.filter { it.memberId == me?.id }).distinctBy { it.id }
            Snapshot(me, settings, members, mine, open)
        }
    }
}

/** Lo que se está editando de una jornada. Pausas sin id = nuevas. */
data class PauseDraft(val id: String?, val start: Instant, val end: Instant?)

data class ShiftDraft(val start: Instant, val end: Instant?, val pauses: List<PauseDraft>, val note: String? = null) {
    /** Mismas reglas que el prototipo. Devuelve el error a mostrar, o null si está bien. */
    fun problem(open: Boolean, now: Instant): String? {
        if (end == null && !open) return "Falta la hora de salida."
        if (end != null && end <= start) return "La salida tiene que ser después de la entrada."
        if (start > now || (end != null && end > now)) return "No se puede cargar un horario en el futuro."
        val lim = end ?: now
        pauses.forEachIndexed { i, p ->
            if (p.end == null && end != null) return "La pausa ${i + 1} necesita hora de fin."
            if (p.start < start || (p.end ?: lim) > lim) return "La pausa ${i + 1} tiene que estar entre la entrada y la salida."
            if (p.end != null && p.end <= p.start) return "La pausa ${i + 1} tiene que terminar después de empezar."
        }
        val sorted = pauses.sortedBy { it.start }
        for (i in 1 until sorted.size) if (sorted[i].start < (sorted[i - 1].end ?: lim)) return "Hay pausas que se superponen."
        return null
    }

    fun asShift(base: Shift): Shift = base.copy(
        startAt = start, endAt = end, hours = null, note = note,
        pauses = pauses.map { Pause(it.id ?: "", base.id, it.start, it.end) },
    )

    companion object {
        fun of(s: Shift) = ShiftDraft(s.startAt, s.endAt, s.pauses.sortedBy { it.startAt }.map { PauseDraft(it.id, it.startAt, it.endAt) }, s.note)
    }
}
