package com.cunicella.fichaje.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.DayStats
import com.cunicella.fichaje.data.Pause
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Shift
import com.cunicella.fichaje.data.ShiftDraft
import com.cunicella.fichaje.data.Snapshot
import com.cunicella.fichaje.data.Offline
import com.cunicella.fichaje.data.Op
import com.cunicella.fichaje.data.apply
import com.cunicella.fichaje.data.closePauses
import com.cunicella.fichaje.data.mapShift
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.widget.ClockWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class HoyUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val snap: Snapshot? = null,
    val error: String? = null,
    val toast: String? = null,
    val holidayNext: Boolean = false,
    val editing: String? = null,
    /** Jornada recién terminada a la que se le puede dejar una nota. */
    val noteFor: String? = null,
    /** La foto que se ve es la guardada en el teléfono (no hubo señal). */
    val offline: Boolean = false,
    /** Fichadas esperando señal para mandarse. */
    val pending: Int = 0,
)

class HoyViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(HoyUi())
    val ui: StateFlow<HoyUi> = _ui

    init {
        Offline.init(app)
        refresh()
        viewModelScope.launch { Sync.changes.collect { if (it !== this@HoyViewModel) refresh() } }
        viewModelScope.launch { Offline.pendingCount.collect { n -> _ui.update { it.copy(pending = n) } } }
        viewModelScope.launch { Offline.offline.collect { off -> _ui.update { it.copy(offline = off) } } }
    }

    fun refresh() = viewModelScope.launch {
        runCatching { Snapshot.loadOrLocal() }
            .onSuccess { (s, local) ->
                _ui.update { it.copy(loading = false, snap = s, error = null, offline = local) }
                ClockWidget.show(getApplication(), s)
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = "No se pudo cargar. ${Supa.friendly(e)}") } }
    }

    /**
     * Una fichada: se aplica a la foto al instante y se manda. Sin señal queda en cola
     * y se sincroniza sola después; la pantalla sigue andando con la hora del teléfono.
     */
    private fun fichar(op: Op, done: String?, after: ((Snapshot) -> Unit)? = null) {
        val snap = _ui.value.snap ?: return
        if (_ui.value.busy) return
        val optimistic = snap.apply(op)
        _ui.update { it.copy(busy = true, toast = done, error = null, snap = optimistic) }
        viewModelScope.launch {
            val result = runCatching { Offline.submit(op) }
            val queued = result.getOrDefault(false)
            val (fresh, local) = runCatching { Snapshot.loadOrLocal() }.getOrNull() ?: (optimistic to true)
            _ui.update {
                it.copy(
                    busy = false,
                    snap = if (result.isFailure) snap else fresh,
                    offline = local,
                    error = result.exceptionOrNull()?.let { e -> "No se pudo guardar. ${Supa.friendly(e)}" },
                    toast = if (result.isFailure) null else if (queued) "${done ?: "Guardado"} · sin señal, se sincroniza solo" else done,
                )
            }
            ClockWidget.show(getApplication(), _ui.value.snap ?: fresh)
            if (result.isSuccess) { after?.invoke(_ui.value.snap ?: fresh); Sync.changed(this@HoyViewModel) }
        }
    }

    /**
     * Primero muestra el resultado esperado (optimistic), después lo guarda y recarga.
     * Si falla, vuelve a como estaba y muestra el error.
     */
    private fun act(done: String? = null, optimistic: (Snapshot) -> Snapshot = { it }, block: suspend (Snapshot) -> String?) {
        val snap = _ui.value.snap ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, toast = done, error = null, snap = optimistic(snap)) }
        viewModelScope.launch {
            val result = runCatching {
                if (!Supa.ensureSession()) throw Supa.NoSession()
                block(snap)
            }
            val fresh = runCatching { Snapshot.loadOrLocal().first }.getOrNull()
            _ui.update {
                it.copy(
                    busy = false,
                    snap = fresh ?: if (result.isFailure) snap else it.snap,
                    error = result.exceptionOrNull()?.let { e -> "No se pudo guardar. ${Supa.friendly(e)}" },
                    toast = result.getOrNull() ?: it.toast,
                )
            }
            if (fresh != null) ClockWidget.show(getApplication(), fresh)
            if (result.isSuccess) Sync.changed(this@HoyViewModel)
        }
    }

    fun start() {
        val s = _ui.value.snap ?: return
        val me = s.me ?: return
        if (s.myOpen != null) return
        val now = Clock.System.now()
        val hol = _ui.value.holidayNext
        _ui.update { it.copy(holidayNext = false) }
        fichar(Op(kind = Op.START, at = now, memberId = me.id, shiftId = Op.localId(), holiday = hol), "Jornada iniciada a las ${Calc.hm(now)}")
    }

    fun pause() {
        val s = _ui.value.snap ?: return
        val open = s.myOpen ?: return
        fichar(Op(kind = Op.PAUSE, at = Clock.System.now(), memberId = open.memberId, shiftId = open.id), "Pausa iniciada")
    }

    fun resume() {
        val s = _ui.value.snap ?: return
        val open = s.myOpen ?: return
        fichar(Op(kind = Op.RESUME, at = Clock.System.now(), memberId = open.memberId, shiftId = open.id), "Seguís en turno")
    }

    fun stop() {
        val s = _ui.value.snap ?: return
        val open = s.myOpen ?: return
        val now = Clock.System.now()
        val tooShort = Calc.workMs(open.closePauses(now).copy(endAt = now), now) < 60_000
        fichar(
            Op(kind = Op.STOP, at = now, memberId = open.memberId, shiftId = open.id, rate = Calc.currentRate(s.me, s.settings)),
            if (tooShort) "Jornada de menos de un minuto: no se guarda" else "Jornada terminada",
        ) { if (!tooShort) _ui.update { it.copy(noteFor = open.id) } }
    }

    fun dismissNote() = _ui.update { it.copy(noteFor = null) }

    fun saveNote(id: String, note: String) {
        _ui.update { it.copy(noteFor = null) }
        if (note.isBlank()) return
        val s = _ui.value.snap ?: return
        val shift = s.mineToday.firstOrNull { it.id == id } ?: return
        fichar(Op(kind = Op.NOTE, at = Clock.System.now(), memberId = shift.memberId, shiftId = id, note = note.trim()), "Nota guardada")
    }

    fun setHoliday(v: Boolean) {
        val open = _ui.value.snap?.myOpen
        if (open == null) _ui.update { it.copy(holidayNext = v) }
        else act(optimistic = { s -> s.mapShift(open.id) { it.copy(holiday = v) } }) { Repo.setHoliday(open.id, v); null }
    }

    fun edit(id: String?) = _ui.update { it.copy(editing = id) }

    fun saveEdit(shift: Shift, d: ShiftDraft) {
        _ui.update { it.copy(editing = null) }
        act("Jornada actualizada", optimistic = { s -> s.mapShift(shift.id) { d.asShift(it) } }) { s ->
            Repo.saveEdit(shift, d, Calc.currentRate(s.me, s.settings))
            null
        }
    }
}

@Composable
fun HoyScreen(vm: HoyViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now()
            delay(1000)
        }
    }
    val snap = ui.snap
    if (snap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.loading) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text("Reintentar") }
            }
        }
        return
    }
    val me = snap.me
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (me == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    "Tu email todavía no está en el equipo. Pedile al admin que te agregue y volvé a entrar.",
                    Modifier.padding(16.dp),
                )
            }
            return@Column
        }
        ClockCard(ui, snap, now, vm)
        ui.editing?.let { id ->
            snap.mineToday.firstOrNull { it.id == id }?.let { sh ->
                ShiftEditorSheet(sh, snap.me, snap.settings, onSave = { vm.saveEdit(sh, it) }, onDismiss = { vm.edit(null) })
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.toast?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        if (ui.offline || ui.pending > 0) {
            Text(
                (if (ui.offline) "Sin conexión: se muestra lo guardado en el teléfono" else "Sincronizando") +
                    (if (ui.pending > 0) " · ${ui.pending} fichada${if (ui.pending == 1) "" else "s"} por mandar" else ""),
                color = LocalStateColors.current.warn, fontSize = 13.sp,
            )
        }
        NowCard(snap, now)
    }
    ui.noteFor?.let { id -> NoteSheet(onSkip = { vm.dismissNote() }) { vm.saveNote(id, it) } }
}

/** Al terminar el turno: una nota opcional, sin pasar por el editor. */
@Composable
private fun NoteSheet(onSkip: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Sheet("Jornada terminada", onSkip) {
        Text("¿Querés dejar una nota? Es opcional.", color = LocalStateColors.current.muted, fontSize = 13.5.sp)
        OutlinedTextField(
            text, { text = it.take(200) }, Modifier.fillMaxWidth(),
            label = { Text("Nota") }, placeholder = { Text("Qué se hizo, dónde, con quién…") },
            minLines = 2, maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp)) { Text("Omitir") }
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp)) { Text("Guardar nota") }
        }
    }
}

@Composable
private fun ClockCard(ui: HoyUi, snap: Snapshot, now: kotlinx.datetime.Instant, vm: HoyViewModel) {
    val sc = LocalStateColors.current
    val open = snap.myOpen
    val paused = open != null && Calc.isPaused(open)
    val stats = DayStats.of(snap.mineToday, snap.me, snap.settings, now)
    val goalMs = (snap.settings.goalHours * 3_600_000).toLong()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    when { open == null -> "Fuera de turno"; paused -> "En pausa"; else -> "En turno" },
                    when { open == null -> sc.muted; paused -> sc.warn; else -> sc.on },
                    when { open == null -> MaterialTheme.colorScheme.surfaceVariant; paused -> sc.warnBg; else -> sc.onBg },
                )
                Spacer(Modifier.weight(1f))
                Text(snap.me?.shortName ?: "", color = sc.muted, fontSize = 13.sp)
            }
            Text(Calc.clock(stats.worked), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 52.sp, letterSpacing = (-1).sp)
            LinearProgressIndicator(
                progress = { (stats.worked.toFloat() / goalMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (stats.worked >= goalMs) sc.on else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Text(
                if (stats.worked >= goalMs) "Meta de ${snap.settings.goalHours.toInt()}h cumplida"
                else "${Calc.dur(stats.worked)} de ${snap.settings.goalHours.toInt()}h · faltan ${Calc.dur(goalMs - stats.worked)}",
                color = sc.muted, fontSize = 13.sp,
            )
            // la jornada que editan los lápices: la abierta o la última de hoy
            val editable = (open ?: snap.mineToday.filter { it.hours == null }.maxByOrNull { it.startAt })?.takeIf { !it.id.startsWith("local") }
            val onEdit: (() -> Unit)? = editable?.let { e -> { vm.edit(e.id) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Generado", Calc.money(stats.earned, snap.settings.currency), Modifier.weight(1f))
                Fact("Entrada", stats.firstStart?.let { Calc.hm(it) } ?: "--:--", Modifier.weight(1f), onEdit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Salida", stats.lastEnd?.let { Calc.hm(it) } ?: "--:--", Modifier.weight(1f), onEdit)
                Fact("Pausa", Calc.dur(stats.paused), Modifier.weight(1f), onEdit)
            }
            val hol = open?.holiday ?: ui.holidayNext
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
            ) {
                Seg("Día normal", !hol, Modifier.weight(1f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface) { vm.setHoliday(false) }
                Seg("Feriado ×${trimNum(snap.settings.holidayMult)}", hol, Modifier.weight(1f), sc.warnBg, sc.warn) { vm.setHoliday(true) }
            }
            if (open == null) {
                Button(
                    onClick = { vm.start() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = sc.on, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Iniciar jornada", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (paused) vm.resume() else vm.pause() },
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text(if (paused) "Reanudar" else "Pausar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    SlideToConfirm("Terminar ››", Modifier.weight(1.4f), enabled = !ui.busy) { vm.stop() }
                }
            }
        }
    }
}

@Composable
private fun NowCard(snap: Snapshot, now: kotlinx.datetime.Instant) {
    val sc = LocalStateColors.current
    val onCount = snap.members.count { m -> snap.openAll.any { it.memberId == m.id } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Ahora mismo", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.width(8.dp))
            Text("$onCount/${snap.members.size} fichados", color = sc.muted, fontSize = 13.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                snap.members.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    val o = snap.openAll.firstOrNull { it.memberId == m.id }
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(if (o != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Text(m.initials, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    o == null -> "Fuera de turno"
                                    Calc.isPaused(o) -> "En pausa · desde las ${Calc.hm(o.startAt)}"
                                    else -> "En turno · desde las ${Calc.hm(o.startAt)}"
                                },
                                color = if (o != null) sc.on else sc.muted, fontSize = 13.sp,
                            )
                        }
                        if (o != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Calc.dur(Calc.workMs(o, now)), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(Calc.money(Calc.earned(o, m, snap.settings, now), snap.settings.currency), fontFamily = Mono, color = sc.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 11.5.sp, color = LocalStateColors.current.muted)
            if (onEdit != null) Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}
