package com.cunicella.fichaje.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** Reglas de negocio: horas pagas, plata y formatos. Las mismas que el prototipo. */
object Calc {
    const val HOUR_MS = 3_600_000.0
    private val tz get() = TimeZone.currentSystemDefault()

    fun isOpen(s: Shift) = s.endAt == null && s.hours == null
    fun isPaused(s: Shift) = isOpen(s) && s.pauses.any { it.endAt == null }

    fun pauseMs(s: Shift, now: Instant): Long =
        s.pauses.sumOf { ((it.endAt ?: now) - it.startAt).inWholeMilliseconds.coerceAtLeast(0) }

    /** Horas pagas: de entrada a salida menos las pausas, o las horas cargadas a mano. */
    fun workMs(s: Shift, now: Instant): Long =
        s.hours?.let { (it * HOUR_MS).toLong() }
            ?: (((s.endAt ?: now) - s.startAt).inWholeMilliseconds - pauseMs(s, now)).coerceAtLeast(0)

    /** La tarifa se congela al cerrar la jornada; mientras está abierta vale la de hoy. */
    fun rate(s: Shift, m: Member?, st: Settings): Double = s.rate ?: m?.rate ?: st.rate

    fun currentRate(m: Member?, st: Settings): Double = m?.rate ?: st.rate

    fun earned(s: Shift, m: Member?, st: Settings, now: Instant): Double =
        workMs(s, now) / HOUR_MS * rate(s, m, st) * (if (s.holiday) st.holidayMult else 1.0)

    fun startOfToday(): Instant = Clock.System.todayIn(tz).atStartOfDayIn(tz)

    fun dur(ms: Long): String {
        val m = ms.coerceAtLeast(0) / 60_000
        return "%dh %02dm".format(m / 60, m % 60)
    }

    fun clock(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }

    fun hm(i: Instant): String {
        val t = i.toLocalDateTime(tz)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    fun money(amount: Double, currency: String): String {
        if (Privacy.hidden) return "$ ••••"
        val f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"))
        runCatching { f.currency = Currency.getInstance(currency) }
        f.maximumFractionDigits = 0
        return f.format(amount)
    }
}

/** Resumen de un día de una persona, para la pantalla Hoy y el widget. */
data class DayStats(
    val worked: Long,
    val earned: Double,
    val paused: Long,
    val firstStart: Instant?,
    val lastEnd: Instant?,
) {
    companion object {
        fun of(shifts: List<Shift>, m: Member?, st: Settings, now: Instant): DayStats {
            val open = shifts.any { Calc.isOpen(it) }
            return DayStats(
                worked = shifts.sumOf { Calc.workMs(it, now) },
                earned = shifts.sumOf { Calc.earned(it, m, st, now) },
                paused = shifts.sumOf { Calc.pauseMs(it, now) },
                firstStart = shifts.minOfOrNull { it.startAt },
                lastEnd = if (open) null else shifts.mapNotNull { it.endAt }.maxOrNull(),
            )
        }
    }
}
