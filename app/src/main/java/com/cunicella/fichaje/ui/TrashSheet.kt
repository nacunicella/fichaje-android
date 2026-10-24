package com.cunicella.fichaje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store
import com.cunicella.fichaje.data.Supa
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/** Papelera: lo borrado en los últimos 30 días, con botón para restaurar. */
@Composable
internal fun TrashSheet(store: Store, vm: DataViewModel, onDismiss: () -> Unit) {
    val sc = LocalStateColors.current
    val now = rememberNow()
    var trash by remember { mutableStateOf<Repo.Trash?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { Repo.trash() }.onSuccess { trash = it }.onFailure { error = Supa.friendly(it) }
    }
    fun restore(table: String, id: String, done: String) {
        vm.run(done) { Repo.restore(table, id) }
        reload++
    }
    fun expires(deletedAt: Instant?): String = deletedAt?.let { "se borra el ${Dates.short(it + 30.days)}" } ?: ""

    Sheet("Papelera", onDismiss) {
        Text("Lo que se borra queda acá 30 días y después desaparece de verdad.", color = sc.muted, fontSize = 13.sp)
        val t = trash
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            t == null -> Text("Cargando…", color = sc.muted, fontSize = 13.sp)
            t.shifts.isEmpty() && t.payments.isEmpty() && t.members.isEmpty() -> EmptyCard("La papelera está vacía.")
            else -> {
                if (t.shifts.isNotEmpty()) {
                    SectionTitle("Jornadas", t.shifts.size.toString())
                    ListCard(t.shifts.size) { i ->
                        val s = t.shifts[i]
                        val m = store.member(s.memberId) ?: t.members.firstOrNull { it.id == s.memberId }
                        TrashRow(
                            "${Dates.short(s.startAt)} · ${Calc.hm(s.startAt)} – ${s.endAt?.let { Calc.hm(it) } ?: "abierta"}",
                            "${m?.name ?: "—"} · ${Calc.dur(Calc.workMs(s, now))} · ${expires(s.deletedAt)}",
                        ) { restore("shifts", s.id, "Jornada restaurada") }
                    }
                }
                if (t.payments.isNotEmpty()) {
                    SectionTitle("Cobros", t.payments.size.toString())
                    ListCard(t.payments.size) { i ->
                        val p = t.payments[i]
                        val m = store.member(p.memberId) ?: t.members.firstOrNull { it.id == p.memberId }
                        TrashRow(
                            "${Calc.money(p.amount, store.settings.currency)} · ${Dates.short(p.paidAt)}",
                            "${m?.name ?: "—"} · ${expires(p.deletedAt)}",
                        ) { restore("payments", p.id, "Cobro restaurado") }
                    }
                }
                if (t.members.isNotEmpty()) {
                    SectionTitle("Integrantes", t.members.size.toString())
                    ListCard(t.members.size) { i ->
                        val m = t.members[i]
                        TrashRow(m.name, (m.email ?: "sin email") + " · " + expires(m.deletedAt)) { restore("members", m.id, "Integrante restaurado") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, sub: String, onRestore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = Mono)
            Text(sub, color = LocalStateColors.current.muted, fontSize = 12.sp)
        }
        TextButton(onClick = onRestore) { Text("Restaurar", fontWeight = FontWeight.SemiBold) }
    }
}
