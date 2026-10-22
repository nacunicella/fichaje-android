package com.cunicella.fichaje.ui

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Snapshot
import com.cunicella.fichaje.widget.ClockWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class HoyUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val snap: Snapshot? = null,
    val error: String? = null,
    val toast: String? = null,
    val holidayNext: Boolean = false,
)

class HoyViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(HoyUi())
    val ui: StateFlow<HoyUi> = _ui

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { Snapshot.load() }
            .onSuccess { s -> _ui.update { it.copy(loading = false, snap = s, error = null) } }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = "No se pudo cargar: ${e.message ?: "sin conexión"}") } }
    }

    private fun act(done: String? = null, block: suspend (Snapshot) -> String?) {
        val snap = _ui.value.snap ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, toast = null) }
        viewModelScope.launch {
            val result = runCatching { block(snap) }
            val fresh = runCatching { Snapshot.load() }.getOrNull()
            _ui.update {
                it.copy(
                    busy = false,
                    snap = fresh ?: it.snap,
                    error = result.exceptionOrNull()?.let { e -> "No se pudo guardar: ${e.message ?: "sin conexión"}" },
                    toast = result.getOrNull() ?: done,
                )
            }
            ClockWidget.refreshAll(getApplication())
        }
    }

    fun start() = act { s ->
        Repo.start(s.me!!.id, _ui.value.holidayNext)
        _ui.update { it.copy(holidayNext = false) }
        "Jornada iniciada a las ${Calc.hm(Clock.System.now())}"
    }

    fun pause() = act("Pausa iniciada") { s -> Repo.pause(s.myOpen!!.id); null }
    fun resume() = act("Seguís en turno") { s -> Repo.resume(s.myOpen!!.id); null }

    fun stop() = act { s ->
        val open = s.myOpen!!
        val saved = Repo.stop(open, Calc.currentRate(s.me, s.settings))
        if (saved) "Jornada terminada" else "Jornada de menos de un minuto: no se guardó"
    }

    fun setHoliday(v: Boolean) {
        val open = _ui.value.snap?.myOpen
        if (open == null) _ui.update { it.copy(holidayNext = v) } else act { Repo.setHoliday(open.id, v); null }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.toast?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        NowCard(snap, now)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Generado", Calc.money(stats.earned, snap.settings.currency), Modifier.weight(1f))
                Fact("Entrada", stats.firstStart?.let { Calc.hm(it) } ?: "--:--", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Salida", stats.lastEnd?.let { Calc.hm(it) } ?: "--:--", Modifier.weight(1f))
                Fact("Pausa", Calc.dur(stats.paused), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hoy es feriado", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("paga ×${trimNum(snap.settings.holidayMult)}  ", color = sc.muted, fontSize = 12.sp)
                Switch(checked = open?.holiday ?: ui.holidayNext, onCheckedChange = { vm.setHoliday(it) }, enabled = !ui.busy)
            }
            if (open == null) {
                Button(
                    onClick = { vm.start() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = sc.on, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Iniciar jornada", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (paused) vm.resume() else vm.pause() }, enabled = !ui.busy,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text(if (paused) "Reanudar" else "Pausar", fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { vm.stop() }, enabled = !ui.busy,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text("Terminar jornada", fontWeight = FontWeight.SemiBold) }
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
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.5.sp, color = LocalStateColors.current.muted)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
