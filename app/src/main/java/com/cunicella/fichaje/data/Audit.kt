package com.cunicella.fichaje.data

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

enum class AuditCat(val label: String) {
    Fichaje("Fichajes"), Edicion("Ediciones"), Cobro("Cobros"), Equipo("Equipo"), Config("Configuración"),
}

/** Un movimiento ya traducido a texto, como lo muestra Historial > Movimientos. */
data class AuditItem(
    val id: Long,
    val at: Instant,
    val cat: AuditCat,
    val text: String,
    val changes: List<Triple<String, String, String>>,
    val actorMember: String?,
    val memberId: String?,
)

/** Convierte las filas crudas de la auditoría (old/new en JSON) en frases. */
object Audit {
    fun translate(rows: List<AuditRow>, store: Store): List<AuditItem> = rows.mapNotNull { r ->
        runCatching { one(r, store) }.getOrNull()
    }

    private fun JsonObject?.str(k: String): String? = (this?.get(k) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.num(k: String): Double? = (this?.get(k) as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject?.bool(k: String): Boolean? = (this?.get(k) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject?.time(k: String): Instant? = str(k)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun money(v: Double?, st: Settings) = if (v == null) "—" else Calc.money(v, st.currency)
    private fun rateTxt(v: Double?, st: Settings) = if (v == null) "general" else money(v, st) + "/h"
    private fun whenTxt(i: Instant?) = i?.let { Dates.dateTime(it) } ?: "abierta"

    private fun one(r: AuditRow, store: Store): AuditItem? {
        val st = store.settings
        val o = r.old
        val n = r.new
        val ch = ArrayList<Triple<String, String, String>>()
        fun diff(label: String, a: String?, b: String?) { if (a != b) ch += Triple(label, a ?: "—", b ?: "—") }

        val cat: AuditCat
        val text: String
        when (r.table) {
            "shifts" -> when (r.action) {
                "insert" -> {
                    val manual = n.bool("manual") == true
                    cat = if (manual) AuditCat.Edicion else AuditCat.Fichaje
                    val start = n.time("start_at")
                    text = if (manual) {
                        val hours = n.num("hours")
                        val range = if (hours != null) Calc.dur((hours * 3_600_000).toLong()) + " por horas"
                        else "${start?.let { Calc.hm(it) }} – ${n.time("end_at")?.let { Calc.hm(it) }}"
                        "Cargó una jornada a mano del ${start?.let { Dates.short(it) }}: $range" +
                            (if (n.bool("holiday") == true) " · feriado" else "")
                    } else {
                        "Inició jornada a las ${start?.let { Calc.hm(it) }}" +
                            (if (n.bool("holiday") == true) " · feriado" else "")
                    }
                }
                "update" -> {
                    val trashed = o.str("deleted_at") == null && n.str("deleted_at") != null
                    val restored = o.str("deleted_at") != null && n.str("deleted_at") == null
                    if (trashed || restored) {
                        val t = (if (trashed) "Mandó a la papelera" else "Restauró") + " la jornada del ${n.time("start_at")?.let { Dates.short(it) }}"
                        return AuditItem(r.id, r.at, AuditCat.Edicion, t, ch, r.actorMember, r.memberId)
                    }
                    val closed = o.time("end_at") == null && n.time("end_at") != null && o.str("start_at") == n.str("start_at")
                    val onlyHoliday = o.bool("holiday") != n.bool("holiday") &&
                        o.str("start_at") == n.str("start_at") && o.str("end_at") == n.str("end_at") && o.str("payment_id") == n.str("payment_id")
                    val paidChange = o.str("payment_id") != n.str("payment_id") &&
                        o.str("start_at") == n.str("start_at") && o.str("end_at") == n.str("end_at")
                    when {
                        paidChange -> return null // lo cuenta el cobro en sí
                        closed && o.bool("manual") != true -> {
                            cat = AuditCat.Fichaje
                            text = "Terminó jornada a las ${n.time("end_at")?.let { Calc.hm(it) }}"
                        }
                        onlyHoliday -> {
                            cat = AuditCat.Edicion
                            text = "Marcó la jornada del ${n.time("start_at")?.let { Dates.short(it) }} como " +
                                if (n.bool("holiday") == true) "feriado" else "día normal"
                        }
                        else -> {
                            cat = AuditCat.Edicion
                            text = "Editó la jornada del ${n.time("start_at")?.let { Dates.short(it) }}"
                            diff("Entrada", whenTxt(o.time("start_at")), whenTxt(n.time("start_at")))
                            diff("Salida", whenTxt(o.time("end_at")), whenTxt(n.time("end_at")))
                            if (o.num("hours") != n.num("hours")) diff("Tipo", if (o.num("hours") != null) "por horas" else "por horario", if (n.num("hours") != null) "por horas" else "por horario")
                            if (o.bool("holiday") != n.bool("holiday")) diff("Feriado", if (o.bool("holiday") == true) "sí" else "no", if (n.bool("holiday") == true) "sí" else "no")
                            if (o.str("note") != n.str("note")) diff("Nota", o.str("note"), n.str("note"))
                        }
                    }
                }
                else -> {
                    cat = AuditCat.Edicion
                    val start = o.time("start_at")
                    text = "Eliminó la jornada del ${start?.let { Dates.short(it) }} (${start?.let { Calc.hm(it) }} – ${o.time("end_at")?.let { Calc.hm(it) } ?: "abierta"})"
                }
            }
            "pauses" -> {
                cat = AuditCat.Fichaje
                text = when (r.action) {
                    "insert" -> if (n.time("end_at") == null) "Inició una pausa a las ${n.time("start_at")?.let { Calc.hm(it) }}"
                    else "Agregó una pausa de ${n.time("start_at")?.let { Calc.hm(it) }} a ${n.time("end_at")?.let { Calc.hm(it) }}"
                    "update" -> if (o.time("end_at") == null && n.time("end_at") != null && o.str("start_at") == n.str("start_at"))
                        "Terminó la pausa a las ${n.time("end_at")?.let { Calc.hm(it) }}"
                    else {
                        diff("Pausa", "${o.time("start_at")?.let { Calc.hm(it) }} – ${o.time("end_at")?.let { Calc.hm(it) } ?: "ahora"}", "${n.time("start_at")?.let { Calc.hm(it) }} – ${n.time("end_at")?.let { Calc.hm(it) } ?: "ahora"}")
                        "Cambió una pausa"
                    }
                    else -> "Quitó la pausa de ${o.time("start_at")?.let { Calc.hm(it) }} a ${o.time("end_at")?.let { Calc.hm(it) } ?: "ahora"}"
                }
            }
            "payments" -> {
                cat = AuditCat.Cobro
                text = when (r.action) {
                    "insert" -> "Registró un cobro de ${money(n.num("amount"), st)}"
                    "delete" -> "Deshizo el cobro de ${money(o.num("amount"), st)} del ${o.time("paid_at")?.let { Dates.short(it) }}"
                    else -> when {
                        o.str("deleted_at") == null && n.str("deleted_at") != null -> "Deshizo el cobro de ${money(n.num("amount"), st)} del ${n.time("paid_at")?.let { Dates.short(it) }} (papelera)"
                        o.str("deleted_at") != null && n.str("deleted_at") == null -> "Restauró el cobro de ${money(n.num("amount"), st)} del ${n.time("paid_at")?.let { Dates.short(it) }}"
                        else -> "Cambió un cobro"
                    }
                }
            }
            "members" -> {
                cat = AuditCat.Equipo
                fun name(j: JsonObject?) = listOf(j.str("first_name") ?: "", j.str("last_name") ?: "").filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Sin nombre" }
                text = when (r.action) {
                    "insert" -> "Agregó a ${name(n)} al equipo (${rateTxt(n.num("rate"), st)})"
                    "delete" -> "Eliminó a ${name(o)} del equipo"
                    else -> if (o.str("deleted_at") == null && n.str("deleted_at") != null) "Mandó a ${name(n)} a la papelera"
                    else if (o.str("deleted_at") != null && n.str("deleted_at") == null) "Restauró a ${name(n)} en el equipo"
                    else if (o.str("invite_code") != n.str("invite_code") && o.str("user_id") == n.str("user_id")) {
                        if (n.str("invite_code") != null) "Generó un código de invitación para ${name(n)}" else "Canceló la invitación de ${name(n)}"
                    } else {
                        if (o.str("user_id") == null && n.str("user_id") != null && name(o) == name(n)) {
                            "${name(n)} entró por primera vez con su cuenta"
                        } else {
                            diff("Nombre", name(o), name(n))
                            diff("Email", o.str("email"), n.str("email"))
                            diff("Valor hora", rateTxt(o.num("rate"), st), rateTxt(n.num("rate"), st))
                            if (o.bool("active") != n.bool("active")) diff("Activo", if (o.bool("active") == true) "sí" else "no", if (n.bool("active") == true) "sí" else "no")
                            diff("Rol", o.str("role"), n.str("role"))
                            "Editó a ${name(n)}"
                        }
                    }
                }
            }
            "settings" -> {
                cat = AuditCat.Config
                diff("Empresa", o.str("company"), n.str("company"))
                diff("Valor hora general", money(o.num("rate"), st), money(n.num("rate"), st))
                diff("Multiplicador feriado", o.num("holiday_mult")?.let { "×${trim(it)}" }, n.num("holiday_mult")?.let { "×${trim(it)}" })
                diff("Meta diaria", o.num("goal_hours")?.let { "${trim(it)}h" }, n.num("goal_hours")?.let { "${trim(it)}h" })
                diff("Moneda", o.str("currency"), n.str("currency"))
                if (ch.isEmpty()) return null
                text = "Cambió la configuración"
            }
            else -> return null
        }
        return AuditItem(r.id, r.at, cat, text, ch, r.actorMember, r.memberId)
    }

    private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
}
