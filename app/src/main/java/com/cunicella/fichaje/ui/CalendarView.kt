package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Shift
import com.cunicella.fichaje.data.Store
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-AR"))

/** Calendario mensual: cada día se pinta según las horas trabajadas; al tocar uno se listan sus jornadas. */
@Composable
internal fun CalendarView(shifts: List<Shift>, store: Store, now: Instant, showPerson: Boolean, onOpen: (String) -> Unit) {
    val sc = LocalStateColors.current
    val st = store.settings
    val today = Dates.today()
    var monthKey by rememberSaveable { mutableStateOf(LocalDate(today.year, today.month, 1).toString()) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val first = LocalDate.parse(monthKey)
    val next = first.plus(1, DateTimeUnit.MONTH)
    val days = next.minus(1, DateTimeUnit.DAY).dayOfMonth
    val selected = selectedKey?.let { LocalDate.parse(it) }

    val inMonth = shifts.filter { val d = Dates.dateOf(it.startAt); d >= first && d < next }
    val byDay = inMonth.groupBy { Dates.dateOf(it.startAt) }
    val msByDay = byDay.mapValues { (_, items) -> items.sumOf { Calc.workMs(it, now) } }
    val maxMs = (msByDay.values.maxOrNull() ?: 0L).coerceAtLeast(8 * 3_600_000L)
    val goal = (st.goalHours * 3_600_000).toLong()

    TotalCard(
        "Total de ${first.toJavaLocalDate().format(monthFmt)}",
        Calc.money(inMonth.sumOf { Calc.earned(it, store.member(it.memberId), st, now) }, st.currency),
        "${Calc.dur(inMonth.sumOf { Calc.workMs(it, now) })} · ${byDay.size} día${if (byDay.size == 1) "" else "s"} trabajado${if (byDay.size == 1) "" else "s"} · ${inMonth.size} jornada${if (inMonth.size == 1) "" else "s"}",
    )

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthKey = first.minus(1, DateTimeUnit.MONTH).toString(); selectedKey = null }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                Text(
                    first.toJavaLocalDate().format(monthFmt).replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                IconButton(
                    onClick = { monthKey = next.toString(); selectedKey = null }, modifier = Modifier.size(36.dp),
                    enabled = next <= today,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = sc.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val lead = first.dayOfWeek.isoDayNumber - 1
            val cells = lead + days
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val n = r * 7 + c - lead + 1
                        if (n < 1 || n > days) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                            continue
                        }
                        val day = LocalDate(first.year, first.month, n)
                        val ms = msByDay[day] ?: 0L
                        val worked = ms > 0
                        val frac = (ms.toFloat() / maxMs).coerceIn(0.18f, 1f)
                        val bg = when {
                            day == selected -> MaterialTheme.colorScheme.primary
                            worked -> sc.on.copy(alpha = 0.15f + 0.6f * frac)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                        val fg = when {
                            day == selected -> MaterialTheme.colorScheme.onPrimary
                            worked -> MaterialTheme.colorScheme.onSurface
                            else -> sc.muted
                        }
                        Column(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(bg)
                                .clickable { selectedKey = if (day == selected) null else day.toString() }
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                n.toString(), color = fg, fontSize = 13.sp,
                                fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Medium,
                            )
                            if (worked) Text(shortDur(ms), color = fg, fontSize = 9.5.sp, fontFamily = Mono, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(sc.on.copy(alpha = 0.3f)))
                Text("Poco", color = sc.muted, fontSize = 11.sp)
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(sc.on.copy(alpha = 0.75f)))
                Text("Mucho", color = sc.muted, fontSize = 11.sp)
                if (goal > 0) Text("· día completo: ${Calc.dur(goal)}", color = sc.muted, fontSize = 11.sp)
            }
        }
    }

    if (selected != null) {
        val items = (byDay[selected] ?: emptyList()).sortedByDescending { it.startAt }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayHeader(Dates.dayLabel(selected), Calc.dur(items.sumOf { Calc.workMs(it, now) }))
            ListCard(items.size, empty = "Sin jornadas ese día.") { i -> ShiftRow(items[i], store, now, showPerson = showPerson) { onOpen(items[i].id) } }
        }
    } else if (byDay.isEmpty()) {
        EmptyCard("No hay jornadas en este mes.")
    } else {
        Text("Tocá un día para ver sus jornadas.", color = sc.muted, fontSize = 12.5.sp)
    }
}

private fun shortDur(ms: Long): String {
    val m = ms / 60_000
    val h = m / 60
    val mm = m % 60
    return if (h > 0 && mm > 0) "${h}h${mm}" else if (h > 0) "${h}h" else "${mm}m"
}
