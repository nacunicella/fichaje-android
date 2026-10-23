package com.cunicella.fichaje.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Payment
import com.cunicella.fichaje.data.Period
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store
import kotlin.math.roundToLong

@Composable
fun CobrarScreen(vm: DataViewModel) = WithStore(vm) { ui, store ->
    val sc = LocalStateColors.current
    val now = rememberNow()
    val st = store.settings
    var selected by rememberSaveable { mutableStateOf(store.me?.id) }
    var confirmPay by remember { mutableStateOf(false) }
    var paymentOpen by remember { mutableStateOf<String?>(null) }
    if (!store.isAdmin) selected = store.me?.id
    if (store.member(selected) == null) selected = store.me?.id ?: store.active.firstOrNull()?.id
    val m = store.member(selected)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cobrar", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        StatusLine(ui.error, ui.toast)
        if (m == null) {
            EmptyCard("No hay integrantes.")
            return@Column
        }
        val isMe = m.id == store.me?.id
        if (store.isAdmin) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                store.members.filter { it.active || it.id == m.id }.forEach { x ->
                    Chip(x.shortName + if (x.id == store.me?.id) " · vos" else "", x.id == m.id) { selected = x.id }
                }
            }
        }
        val mine = store.shifts.filter { it.memberId == m.id }
        val owed = mine.filter { !Calc.isOpen(it) && it.paymentId == null }
        val open = store.openOf(m.id)
        val month = mine.filter { Dates.inPeriod(it.startAt, Period.Month) }
        val pays = store.payments.filter { it.memberId == m.id }.sortedByDescending { it.paidAt }
        val owedAmt = owed.sumOf { Calc.earned(it, m, st, now) }
        val owedMs = owed.sumOf { Calc.workMs(it, now) }

        TotalCard(
            if (isMe) "La empresa te debe" else "La empresa le debe a ${m.shortName}",
            Calc.money(owedAmt, st.currency),
            "${Calc.dur(owedMs)} sin cobrar en ${owed.size} jornada${if (owed.size == 1) "" else "s"} · tarifa ${Calc.money(Calc.currentRate(m, st), st.currency)}/h",
            extra = open?.let { "Más la jornada abierta, que se suma cuando termine (${Calc.dur(Calc.workMs(it, now))} hasta ahora)." },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Este mes", Calc.money(month.sumOf { Calc.earned(it, m, st, now) }, st.currency), Calc.dur(month.sumOf { Calc.workMs(it, now) }), Modifier.weight(1f))
            Stat("Generado", Calc.money(mine.sumOf { Calc.earned(it, m, st, now) }, st.currency), Calc.dur(mine.sumOf { Calc.workMs(it, now) }), Modifier.weight(1f))
            Stat("Cobrado", Calc.money(pays.sumOf { it.amount }, st.currency), "${pays.size} cobro${if (pays.size == 1) "" else "s"}", Modifier.weight(1f))
        }
        BigButton("Cobrar las horas pendientes", enabled = owed.isNotEmpty() && !ui.busy) { confirmPay = true }
        SectionTitle("Historial de cobros")
        ListCard(pays.size, empty = "Todavía no hay cobros.") { i ->
            val p = pays[i]
            Row(Modifier.fillMaxWidth().clickable { paymentOpen = p.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.short(p.paidAt), fontWeight = FontWeight.SemiBold)
                    Text("${Calc.dur(p.workedSeconds * 1000)} · ${p.shiftCount} jornada${if (p.shiftCount == 1) "" else "s"}", color = sc.muted, fontSize = 12.5.sp, fontFamily = Mono)
                }
                Text(Calc.money(p.amount, st.currency), fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (confirmPay) {
            Confirm(
                "Registrar cobro",
                "Se registra un cobro de ${Calc.money(owedAmt, st.currency)} para ${m.name} por ${Calc.dur(owedMs)} en ${owed.size} jornada${if (owed.size == 1) "" else "s"}. Esas jornadas quedan marcadas como cobradas.",
                "Registrar cobro", danger = false,
                onConfirm = {
                    confirmPay = false
                    val amount = owedAmt.roundToLong().toDouble()
                    vm.run("Cobro registrado: ${Calc.money(amount, st.currency)}") { Repo.pay(m.id, owed, amount, owedMs) }
                },
                onDismiss = { confirmPay = false },
            )
        }
    }

    paymentOpen?.let { id ->
        val p = store.payment(id)
        if (p == null) paymentOpen = null else PaymentSheet(p, store, vm, onDismiss = { paymentOpen = null })
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = LocalStateColors.current.muted, fontSize = 11.5.sp)
            Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(sub, color = LocalStateColors.current.muted, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1)
        }
    }
}

@Composable
private fun PaymentSheet(p: Payment, store: Store, vm: DataViewModel, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val st = store.settings
    Sheet("Cobro", onDismiss) {
        KV("Persona", store.nameOf(p.memberId))
        KV("Fecha", Dates.dateTime(p.paidAt))
        KV("Horas", Calc.dur(p.workedSeconds * 1000))
        KV("Jornadas", p.shiftCount.toString())
        KV("Importe", Calc.money(p.amount, st.currency))
        OutlinedButton(
            onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Deshacer este cobro") }
        Text("Las jornadas vuelven a quedar pendientes.", color = LocalStateColors.current.muted, fontSize = 12.5.sp)
    }
    if (confirm) {
        Confirm("Deshacer cobro", "Se borra el cobro de ${Calc.money(p.amount, st.currency)} del ${Dates.short(p.paidAt)} y sus jornadas vuelven a quedar pendientes.", "Sí, deshacer", onConfirm = {
            confirm = false
            vm.run("Cobro deshecho") { Repo.undoPay(p.id) }
            onDismiss()
        }, onDismiss = { confirm = false })
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(k, Modifier.weight(1f), color = LocalStateColors.current.muted, fontSize = 14.sp)
        Text(v, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
