package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Member
import com.cunicella.fichaje.data.PauseDraft
import com.cunicella.fichaje.data.Settings
import com.cunicella.fichaje.data.Shift
import com.cunicella.fichaje.data.ShiftDraft
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private sealed interface Target {
    data object Start : Target
    data object End : Target
    data class PauseStart(val i: Int) : Target
    data class PauseEnd(val i: Int) : Target
}

/**
 * Pone la hora elegida en el día de [anchor]. Si queda más de una hora antes de [notBefore],
 * se pasa al día siguiente (jornadas que cruzan la medianoche).
 */
private fun at(anchor: Instant, h: Int, m: Int, notBefore: Instant? = null): Instant {
    val tz = TimeZone.currentSystemDefault()
    val day = anchor.toLocalDateTime(tz).date
    val t = LocalDateTime(day, LocalTime(h, m)).toInstant(tz)
    return if (notBefore != null && t < notBefore - 1.hours) LocalDateTime(day.plus(1, DateTimeUnit.DAY), LocalTime(h, m)).toInstant(tz) else t
}

/** Editor único de la jornada: entrada, pausas y salida, una línea cada una. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditorSheet(
    shift: Shift,
    member: Member?,
    settings: Settings,
    onSave: (ShiftDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember(shift.id) { mutableStateOf(ShiftDraft.of(shift)) }
    var picking by remember { mutableStateOf<Target?>(null) }
    val open = Calc.isOpen(shift)
    val now = Clock.System.now()
    val problem = d.problem(open, now)
    val sc = LocalStateColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Editar jornada", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Line(sc.on, "Entrada") { TimeChip(d.start) { picking = Target.Start } }
            d.pauses.forEachIndexed { i, p ->
                Line(sc.warn, "Pausa ${i + 1}") {
                    TimeChip(p.start) { picking = Target.PauseStart(i) }
                    Text("–", color = sc.muted)
                    TimeChip(p.end) { picking = Target.PauseEnd(i) }
                    IconButton(onClick = { d = d.copy(pauses = d.pauses.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar pausa ${i + 1}", tint = sc.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Line(Color.Transparent, "") {
                TextButton(onClick = {
                    val lim = d.end ?: now
                    val from = d.pauses.maxOfOrNull { it.end ?: it.start } ?: (d.start + (lim - d.start) / 2)
                    var st = minOf(maxOf(from, d.start), lim)
                    if (lim - st < 1.minutes) st = maxOf(d.start, lim - 15.minutes)
                    d = d.copy(pauses = d.pauses + PauseDraft(null, st, minOf(st + 30.minutes, lim)))
                }) { Text("+ Agregar pausa") }
            }
            Line(MaterialTheme.colorScheme.onSurface, "Salida") {
                TimeChip(d.end, empty = if (open) "abierta" else "--:--") { picking = Target.End }
                if (open && d.end != null) {
                    TextButton(onClick = { d = d.copy(end = null) }) { Text("Dejar abierta", fontSize = 13.sp) }
                }
            }
            val preview = d.asShift(shift)
            Text(
                problem ?: "Quedan ${Calc.dur(Calc.workMs(preview, now))} pagas (${Calc.dur(Calc.pauseMs(preview, now))} de pausa) · " +
                    Calc.money(Calc.earned(preview, member, settings, now), settings.currency),
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(d) }, enabled = problem == null,
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("Guardar", maxLines = 1, fontWeight = FontWeight.SemiBold) }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Cancelar", maxLines = 1)
                }
            }
        }
    }

    picking?.let { target ->
        val initial = when (target) {
            Target.Start -> d.start
            Target.End -> d.end ?: now
            is Target.PauseStart -> d.pauses[target.i].start
            is Target.PauseEnd -> d.pauses[target.i].end ?: now
        }
        TimeDialog(initial, onDismiss = { picking = null }) { h, m ->
            d = when (target) {
                Target.Start -> d.copy(start = at(d.start, h, m))
                Target.End -> d.copy(end = at(d.start, h, m, notBefore = d.start))
                is Target.PauseStart -> d.copy(pauses = d.pauses.mapIndexed { j, p -> if (j == target.i) p.copy(start = at(d.start, h, m, d.start)) else p })
                is Target.PauseEnd -> d.copy(pauses = d.pauses.mapIndexed { j, p -> if (j == target.i) p.copy(end = at(d.start, h, m, d.start)) else p })
            }
            picking = null
        }
    }
}

@Composable
private fun Line(dot: Color, label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.width(72.dp), color = LocalStateColors.current.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun TimeChip(t: Instant?, empty: String = "--:--", onClick: () -> Unit) {
    Text(
        t?.let { Calc.hm(it) } ?: empty,
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: Instant, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val t = initial.toLocalDateTime(TimeZone.currentSystemDefault())
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(clockDialColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
    )
}
