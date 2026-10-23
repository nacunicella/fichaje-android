package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.ManualShift
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private enum class AddMode(val label: String) { Range("Por horario"), Hours("Por horas") }

/** Cargar una jornada a mano: por horario (entrada, salida, pausa) o por cantidad de horas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddShiftSheet(store: Store, vm: DataViewModel, defaultPerson: String?, onDismiss: () -> Unit) {
    val st = store.settings
    val sc = LocalStateColors.current
    val tz = TimeZone.currentSystemDefault()
    var mode by rememberSaveable { mutableStateOf(AddMode.Range) }
    var person by rememberSaveable { mutableStateOf(if (store.isAdmin) defaultPerson ?: store.me?.id else store.me?.id) }
    var day by remember { mutableStateOf(Dates.today()) }
    var startT by remember { mutableStateOf(LocalTime(9, 0)) }
    var endT by remember { mutableStateOf(LocalTime(17, 0)) }
    var pauseMin by rememberSaveable { mutableStateOf("0") }
    var hoursTxt by rememberSaveable { mutableStateOf("8") }
    var minsTxt by rememberSaveable { mutableStateOf("0") }
    var holiday by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val m = store.member(person)
    val rate = Calc.currentRate(m, st)
    val now = Clock.System.now()
    val start: Instant = LocalDateTime(day, startT).toInstant(tz)
    var end: Instant = LocalDateTime(day, endT).toInstant(tz)
    if (end <= start) end += 24.hours // cruza la medianoche
    val pz = (pauseMin.toIntOrNull() ?: 0).coerceAtLeast(0)
    val h = (hoursTxt.toDoubleOrNull() ?: 0.0) + (minsTxt.toDoubleOrNull() ?: 0.0) / 60

    val problem: String? = when {
        m == null -> "Elegí una persona."
        mode == AddMode.Range && pz.minutes >= end - start -> "La pausa no puede ser más larga que la jornada."
        mode == AddMode.Range && end > now -> "No se puede cargar un horario en el futuro."
        mode == AddMode.Hours && (h <= 0 || h > 24) -> "Las horas tienen que estar entre 0 y 24."
        mode == AddMode.Hours && Dates.startOf(day) > now -> "No se puede cargar un día en el futuro."
        else -> null
    }
    val workMs = if (mode == AddMode.Range) (end - start).inWholeMilliseconds - pz * 60_000L else (h * 3_600_000).toLong()
    val money = workMs / 3_600_000.0 * rate * (if (holiday) st.holidayMult else 1.0)

    Sheet("Agregar jornada", onDismiss) {
        Segmented(AddMode.entries, mode, { it.label }) { mode = it }
        FieldLabel("Persona")
        PersonPicker(store.active, person, store.me?.id, enabled = store.isAdmin) { person = it }
        FieldLabel("Fecha")
        ValueChip(Dates.dayLabel(day)) { pickDate = true }
        if (mode == AddMode.Range) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Entrada")
                    TimeChip(start) { pickStart = true }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Salida")
                    TimeChip(end) { pickEnd = true }
                }
            }
            OutlinedTextField(
                pauseMin, { pauseMin = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), label = { Text("Pausa (minutos)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    hoursTxt, { hoursTxt = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), label = { Text("Horas") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    minsTxt, { minsTxt = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), label = { Text("Minutos") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        SwitchRow("Fue feriado", holiday, sub = "×${trimNum(st.holidayMult)}") { holiday = it }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") }, placeholder = { Text("Por ejemplo: me olvidé de fichar") }, singleLine = true)
        Text(
            problem ?: "Se cargan ${Calc.dur(workMs)} pagas por ${Calc.money(money, st.currency)} a ${Calc.money(rate, st.currency)}/h${if (holiday) " ×${trimNum(st.holidayMult)}" else ""}.",
            color = if (problem != null) MaterialTheme.colorScheme.error else sc.muted, fontSize = 13.5.sp,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        BigButton("Guardar jornada", enabled = problem == null) {
            val mm = m ?: return@BigButton
            val shift = if (mode == AddMode.Range) {
                ManualShift(mm.id, start, end, null, holiday, true, note.trim().ifBlank { null }, rate)
            } else {
                val s0 = LocalDateTime(day, LocalTime(9, 0)).toInstant(tz)
                ManualShift(mm.id, s0, s0 + (h * 60).toInt().minutes, h, holiday, true, note.trim().ifBlank { null }, rate)
            }
            vm.run("Jornada agregada") { Repo.addManual(shift, if (mode == AddMode.Range) pz else 0) }
            onDismiss()
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.startOf(day).toEpochMilliseconds() + 12 * 3_600_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        day = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                    }
                    pickDate = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
    if (pickStart) TimeDialog(start, onDismiss = { pickStart = false }) { hh, mi -> startT = LocalTime(hh, mi); pickStart = false }
    if (pickEnd) TimeDialog(end, onDismiss = { pickEnd = false }) { hh, mi -> endT = LocalTime(hh, mi); pickEnd = false }
}

@Composable
private fun ValueChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp,
    )
}
