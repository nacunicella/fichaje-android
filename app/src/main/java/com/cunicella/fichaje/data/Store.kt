package com.cunicella.fichaje.data

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** Todo lo que necesitan Equipo, Historial y Cobrar: se carga de una y se filtra en el teléfono. */
data class Store(
    val me: Member?,
    val settings: Settings,
    val members: List<Member>,
    val shifts: List<Shift>,
    val payments: List<Payment>,
) {
    val active: List<Member> get() = members.filter { it.active }
    val isAdmin: Boolean get() = me?.isAdmin == true

    fun member(id: String?): Member? = members.firstOrNull { it.id == id }
    fun nameOf(id: String?): String = member(id)?.name ?: "alguien que ya no está"
    fun openOf(memberId: String): Shift? = shifts.firstOrNull { it.memberId == memberId && Calc.isOpen(it) }
    fun payment(id: String?): Payment? = payments.firstOrNull { it.id == id }

    companion object {
        suspend fun load(): Store = coroutineScope {
            if (!Supa.ensureSession()) throw Supa.NoSession()
            val meQ = async { Repo.me() }
            val settingsQ = async { Repo.settings() }
            val membersQ = async { Repo.allMembers() }
            val shiftsQ = async { Repo.allShifts() }
            val paymentsQ = async { Repo.payments() }
            Store(meQ.await(), settingsQ.await(), membersQ.await(), shiftsQ.await(), paymentsQ.await())
        }
    }
}

enum class Period(val label: String) { Today("Hoy"), Month("Este mes"), LastMonth("Mes pasado"), All("Todo"), Custom("Otro") }

/** Fechas y períodos, siempre en la zona horaria del teléfono. */
object Dates {
    val tz: TimeZone get() = TimeZone.currentSystemDefault()
    private val es = Locale.forLanguageTag("es-AR")
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", es)
    private val shortYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", es)

    fun today(): LocalDate = Clock.System.todayIn(tz)
    fun dateOf(i: Instant): LocalDate = i.toLocalDateTime(tz).date
    fun startOf(d: LocalDate): Instant = d.atStartOfDayIn(tz)
    fun startOfMonth(d: LocalDate = today()): Instant = startOf(LocalDate(d.year, d.month, 1))
    fun startOfLastMonth(): Instant = startOfMonth(LocalDate(today().year, today().month, 1).minus(1, DateTimeUnit.MONTH))

    fun inPeriod(i: Instant, p: Period): Boolean = when (p) {
        Period.Today -> i >= startOf(today())
        Period.Month -> i >= startOfMonth()
        Period.LastMonth -> i >= startOfLastMonth() && i < startOfMonth()
        Period.All -> true
        Period.Custom -> true // el rango lo aplica la pantalla con inRange
    }

    /** Fechas inclusive, de principio a fin de día. */
    fun inRange(i: Instant, from: LocalDate, to: LocalDate): Boolean = i >= startOf(from) && i < startOf(to.plus(1, DateTimeUnit.DAY))

    fun shortDay(d: LocalDate): String = d.toJavaLocalDate().format(if (d.year == today().year) shortFmt else shortYearFmt).replace(".", "")

    /** "Hoy", "Ayer" o "lunes 20 de octubre". */
    fun dayLabel(d: LocalDate): String {
        val t = today()
        return when (d) {
            t -> "Hoy"
            t.minus(1, DateTimeUnit.DAY) -> "Ayer"
            else -> d.toJavaLocalDate().format(dayFmt).replaceFirstChar { it.uppercase() }
        }
    }

    fun dayLabel(i: Instant): String = dayLabel(dateOf(i))

    /** "20 oct" o, si es de otro año, "20 oct 2025". */
    fun short(i: Instant): String {
        val d = dateOf(i)
        return d.toJavaLocalDate().format(if (d.year == today().year) shortFmt else shortYearFmt).replace(".", "")
    }

    fun dateTime(i: Instant): String = "${short(i)} ${Calc.hm(i)}"
}
