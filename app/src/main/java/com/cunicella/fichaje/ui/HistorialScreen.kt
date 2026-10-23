package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.AuditCat
import com.cunicella.fichaje.data.AuditItem
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Period
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Shift
import com.cunicella.fichaje.data.Store
import kotlinx.datetime.Instant

private enum class HiView(val label: String) { Jornadas("Jornadas"), Movimientos("Movimientos") }

@Composable
fun HistorialScreen(vm: DataViewModel) = WithStore(vm) { ui, store ->
    val sc = LocalStateColors.current
    val now = rememberNow()
    var view by rememberSaveable { mutableStateOf(HiView.Jornadas) }
    var person by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(Period.Month) }
    var cat by rememberSaveable { mutableStateOf<AuditCat?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val cur = store.settings.currency
    if (person != null && store.member(person) == null) person = null

    LaunchedEffect(view) { if (view == HiView.Movimientos && ui.audit.isEmpty()) vm.loadAudit(reset = true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(HiView.entries, view, { it.label }, Modifier.weight(1f)) { view = it }
            AddButton("Agregar jornada") { adding = true }
        }
        StatusLine(ui.error, null)
        val pickable = store.members.sortedWith(compareByDescending<com.cunicella.fichaje.data.Member> { it.active }.thenBy { it.firstName })
        PersonPicker(pickable, person, store.me?.id, allLabel = "Todo el equipo") { person = it }

        if (view == HiView.Jornadas) {
            Segmented(listOf(Period.Month, Period.LastMonth, Period.All), period, { it.label }) { period = it }
            val list = store.shifts.filter { store.member(it.memberId) != null && (person == null || it.memberId == person) && Dates.inPeriod(it.startAt, period) }
            TotalCard(
                "Total del filtro",
                Calc.money(list.sumOf { Calc.earned(it, store.member(it.memberId), store.settings, now) }, cur),
                "${Calc.dur(list.sumOf { Calc.workMs(it, now) })} · ${list.size} jornada${if (list.size == 1) "" else "s"}",
            )
            if (list.isEmpty()) EmptyCard("No hay jornadas en este período.")
            list.groupBy { Dates.dateOf(it.startAt) }.toSortedMap(compareByDescending { it }).forEach { (day, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayHeader(Dates.dayLabel(day), Calc.dur(items.sumOf { Calc.workMs(it, now) }))
                    ListCard(items.size) { i -> ShiftRow(items[i], store, now, showPerson = person == null) { detail = items[i].id } }
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Todo", cat == null) { cat = null }
                AuditCat.entries.forEach { c -> Chip(c.label, cat == c) { cat = c } }
            }
            val list = ui.audit.filter { (cat == null || it.cat == cat) && (person == null || it.memberId == person || it.actorMember == person) }
            Text(
                "${list.size} movimiento${if (list.size == 1) "" else "s"}. Quedan registrados aunque se borre la jornada o la persona.",
                color = sc.muted, fontSize = 12.5.sp,
            )
            if (list.isEmpty() && !ui.auditLoading) EmptyCard("No hay movimientos con este filtro.")
            list.groupBy { Dates.dateOf(it.at) }.toSortedMap(compareByDescending { it }).forEach { (day, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayHeader(Dates.dayLabel(day), items.size.toString())
                    ListCard(items.size) { i -> AuditRow(items[i], store) }
                }
            }
            if (!ui.auditDone || ui.auditLoading) {
                OutlinedButton(
                    onClick = { vm.loadAudit() }, enabled = !ui.auditLoading,
                    modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp),
                ) { Text(if (ui.auditLoading) "Cargando…" else "Cargar más") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    detail?.let { id ->
        val s = store.shifts.firstOrNull { it.id == id }
        if (s == null) detail = null
        else ShiftDetailSheet(s, store, vm, onDismiss = { detail = null })
    }
    if (adding) AddShiftSheet(store, vm, defaultPerson = person ?: store.me?.id, onDismiss = { adding = false })
}

@Composable
internal fun ShiftRow(s: Shift, store: Store, now: Instant, showPerson: Boolean, onTap: () -> Unit) {
    val sc = LocalStateColors.current
    val open = Calc.isOpen(s)
    val m = store.member(s.memberId)
    val range = if (s.hours != null) "${Calc.dur((s.hours * 3_600_000).toLong())} cargadas" else "${Calc.hm(s.startAt)} – ${if (open) "ahora" else Calc.hm(s.endAt!!)}"
    Row(Modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(range, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showPerson) Text(m?.name ?: "", color = sc.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (open) Tag("Abierta", sc.on, sc.onBg)
                if (s.manual) Tag("Manual", sc.muted, MaterialTheme.colorScheme.surfaceVariant)
                if (s.holiday) Tag("Feriado", sc.warn, sc.warnBg)
                if (s.paymentId != null) Tag("Cobrada", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Calc.money(Calc.earned(s, m, store.settings, now), store.settings.currency), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(Calc.dur(Calc.workMs(s, now)), fontFamily = Mono, color = sc.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AuditRow(a: AuditItem, store: Store) {
    val sc = LocalStateColors.current
    val dot = when (a.cat) {
        AuditCat.Fichaje -> sc.on
        AuditCat.Edicion -> MaterialTheme.colorScheme.primary
        AuditCat.Cobro -> sc.warn
        AuditCat.Equipo -> Color(0xFFC792EA)
        AuditCat.Config -> sc.muted
    }
    val by = if (a.actorMember == store.me?.id) "vos" else store.nameOf(a.actorMember)
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.text, fontSize = 14.sp)
            a.changes.forEach { (k, o, n) ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$k:", color = sc.muted, fontSize = 12.5.sp)
                    Text(o, color = sc.muted, fontSize = 12.5.sp, textDecoration = TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ $n", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "${Calc.hm(a.at)} · por $by" + if (a.memberId != null && a.memberId != a.actorMember) " · sobre ${store.nameOf(a.memberId)}" else "",
                color = sc.muted, fontSize = 12.sp, fontFamily = Mono,
            )
        }
    }
}

/** Detalle visual de una jornada, con la barra de la línea de tiempo y las pausas. */
@Composable
internal fun ShiftDetailSheet(s: Shift, store: Store, vm: DataViewModel, onDismiss: () -> Unit) {
    val sc = LocalStateColors.current
    val now = rememberNow(1000)
    val m = store.member(s.memberId)
    val open = Calc.isOpen(s)
    val pay = store.payment(s.paymentId)
    val canEdit = store.isAdmin || s.memberId == store.me?.id
    var editing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val st = store.settings

    if (editing) {
        ShiftEditorSheet(s, m, st, onSave = { d ->
            editing = false
            vm.run("Jornada actualizada") { Repo.saveEdit(s, d, Calc.currentRate(m, st)) }
        }, onDismiss = { editing = false })
        return
    }

    Sheet("Jornada", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(Dates.dayLabel(s.startAt), color = sc.muted, fontSize = 13.5.sp)
            }
            when {
                open -> Pill("Abierta", sc.on, sc.onBg)
                pay != null -> Pill("Cobrada", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                else -> Pill("Pendiente", sc.warn, sc.warnBg)
            }
        }
        Column {
            Text(Calc.money(Calc.earned(s, m, st, now), st.currency), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text(
                "${Calc.dur(Calc.workMs(s, now))} pagas × ${Calc.money(Calc.rate(s, m, st), st.currency)}/h" + if (s.holiday) " × ${trimNum(st.holidayMult)}" else "",
                color = sc.muted, fontSize = 13.sp, fontFamily = Mono,
            )
        }
        if (s.hours != null) {
            Text("Cargada por horas, sin horario de entrada y salida.", color = sc.muted, fontSize = 13.sp)
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Entrada", color = sc.muted, fontSize = 11.5.sp)
                    Text(Calc.hm(s.startAt), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Salida", color = sc.muted, fontSize = 11.5.sp)
                    Text(if (open) "ahora" else Calc.hm(s.endAt!!), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            TimelineBar(s, now, if (open) sc.on else MaterialTheme.colorScheme.primary, sc.warn)
            Text(
                if (s.pauses.isEmpty()) "Sin pausas"
                else "Pausas: " + s.pauses.sortedBy { it.startAt }.joinToString(", ") { "${Calc.hm(it.startAt)}–${it.endAt?.let { e -> Calc.hm(e) } ?: "ahora"}" } +
                    " · ${Calc.dur(Calc.pauseMs(s, now))} descontadas",
                color = sc.muted, fontSize = 12.5.sp, fontFamily = Mono,
            )
        }
        Text(
            (if (s.manual) "Cargada a mano" else "Fichada en la app") +
                (pay?.let { " · cobrada el ${Dates.short(it.paidAt)}" } ?: "") +
                (if (open) " · se valúa a la tarifa de hoy" else "") +
                (s.note?.takeIf { it.isNotBlank() }?.let { " · “$it”" } ?: ""),
            color = sc.muted, fontSize = 12.5.sp,
        )
        if (canEdit) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
                Seg("Día normal", !s.holiday, Modifier.weight(1f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface) {
                    if (s.holiday) vm.run { Repo.setHoliday(s.id, false) }
                }
                Seg("Feriado ×${trimNum(st.holidayMult)}", s.holiday, Modifier.weight(1f), sc.warnBg, sc.warn) {
                    if (!s.holiday) vm.run { Repo.setHoliday(s.id, true) }
                }
            }
            if (pay != null) Text("Ya está cobrada: si la cambiás, el cobro registrado no se modifica.", color = sc.muted, fontSize = 12.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = true }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Editar jornada", maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { confirm = true }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar", maxLines = 1) }
            }
        }
    }
    if (confirm) {
        Confirm(
            "Eliminar jornada",
            "Vas a eliminar la jornada de ${m?.name ?: ""} del ${Dates.dayLabel(s.startAt).lowercase()} (${Calc.dur(Calc.workMs(s, now))}, ${Calc.money(Calc.earned(s, m, st, now), st.currency)}). No se puede deshacer.",
            "Sí, eliminar",
            onConfirm = { confirm = false; vm.run("Jornada eliminada") { Repo.deleteShift(s.id) }; onDismiss() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun TimelineBar(s: Shift, now: Instant, color: Color, pauseColor: Color) {
    val end = s.endAt ?: now
    val span = (end - s.startAt).inWholeMilliseconds.coerceAtLeast(1)
    fun pct(t: Instant) = ((t - s.startAt).inWholeMilliseconds.toFloat() / span).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(color)) {
        val w = maxWidth
        s.pauses.forEach { p ->
            val a = pct(p.startAt)
            val b = pct(p.endAt ?: now)
            Box(Modifier.offset(x = w * a).width((w * (b - a)).coerceAtLeast(3.dp)).height(10.dp).background(pauseColor))
        }
    }
}
