package com.cunicella.fichaje.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Todo lo que se lee y escribe en Supabase. La auditoría la registra la base sola (triggers). */
object Repo {
    private val db get() = Supa.client
    private val withPauses = Columns.raw("*, pauses(*)")

    suspend fun me(): Member? {
        val uid = db.auth.currentUserOrNull()?.id ?: return null
        return db.from("members").select { filter { eq("user_id", uid) } }.decodeSingleOrNull()
    }

    suspend fun settings(): Settings = db.from("settings").select().decodeSingle()

    suspend fun activeMembers(): List<Member> =
        db.from("members").select {
            filter { eq("active", true) }
            order("first_name", Order.ASCENDING)
        }.decodeList()

    suspend fun openShifts(): List<Shift> =
        db.from("shifts").select(withPauses) {
            filter {
                exact("end_at", null)
                exact("hours", null)
            }
        }.decodeList()

    suspend fun shiftsSince(memberId: String, since: Instant): List<Shift> =
        db.from("shifts").select(withPauses) {
            filter {
                eq("member_id", memberId)
                gte("start_at", since.toString())
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
        suspend fun load(): Snapshot {
            val me = Repo.me()
            val settings = Repo.settings()
            val members = Repo.activeMembers()
            val open = Repo.openShifts()
            val today = if (me == null) emptyList() else Repo.shiftsSince(me.id, Calc.startOfToday())
            // una jornada abierta desde ayer también cuenta como "hoy"
            val mine = (today + open.filter { it.memberId == me?.id }).distinctBy { it.id }
            return Snapshot(me, settings, members, mine, open)
        }
    }
}
