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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Member
import com.cunicella.fichaje.data.PayMethod
import com.cunicella.fichaje.data.Payment
import com.cunicella.fichaje.data.Period
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Cobros por saldo: lo generado por las jornadas cerradas menos lo cobrado.
 * Se cobra un monto cualquiera y lo que falta sigue como saldo pendiente.
 */
@Composable
fun CobrarScreen(vm: DataViewModel) = WithStore(vm) { ui, store ->
    val sc = LocalStateColors.current
    val now = rememberNow()
    val st = store.settings
    var selected by rememberSaveable { mutableStateOf(store.me?.id) }
    var paying by remember { mutableStateOf(false) }
    var paymentOpen by remember { mutableStateOf<String?>(null) }
    if (!store.isAdmin) selected = store.me?.id
    if (store.member(selected) == null) selected = store.me?.id ?: store.active.firstOrNull()?.id
    val m = store.member(selected)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusLine(ui.error, null)
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
        val closed = mine.filter { !Calc.isOpen(it) }
        val open = store.openOf(m.id)
        val month = mine.filter { Dates.inPeriod(it.startAt, Period.Month) }
        val pays = store.payments.filter { it.memberId == m.id }.sortedByDescending { it.paidAt }
        val generated = closed.sumOf { Calc.earned(it, m, st, now) }
        val paid = pays.sumOf { it.amount }
        val balance = generated - paid
        val rate = Calc.currentRate(m, st)
        val balanceHours = if (rate > 0) (abs(balance) / rate * Calc.HOUR_MS).toLong() else 0L

        TotalCard(
            when {
                balance < -0.5 -> if (isMe) "Cobraste por adelantado" else "${m.shortName} cobró por adelantado"
                isMe -> "La empresa te debe"
                else -> "La empresa le debe a ${m.shortName}"
            },
            Calc.money(abs(balance), st.currency),
            "Generado ${Calc.money(generated, st.currency)} · cobrado ${Calc.money(paid, st.currency)} · tarifa ${Calc.money(rate, st.currency)}/h",
            extra = listOfNotNull(
                if (balance > 0.5) "Equivale a ${Calc.dur(balanceHours)} a la tarifa de hoy." else null,
                open?.let { "Más la jornada abierta, que se suma cuando termine (${Calc.dur(Calc.workMs(it, now))} hasta ahora)." },
            ).joinToString(" ").ifBlank { null },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Este mes", Calc.money(month.sumOf { Calc.earned(it, m, st, now) }, st.currency), Calc.dur(month.sumOf { Calc.workMs(it, now) }), Modifier.weight(1f))
            Stat("Generado", Calc.money(mine.sumOf { Calc.earned(it, m, st, now) }, st.currency), Calc.dur(mine.sumOf { Calc.workMs(it, now) }), Modifier.weight(1f))
            Stat("Cobrado", Calc.money(paid, st.currency), "${pays.size} cobro${if (pays.size == 1) "" else "s"}", Modifier.weight(1f))
        }
        BigButton("Registrar cobro", enabled = !ui.busy) { paying = true }
        SectionTitle("Historial de cobros")
        ListCard(pays.size, empty = "Todavía no hay cobros.") { i ->
            val p = pays[i]
            Row(Modifier.fillMaxWidth().clickable { paymentOpen = p.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.short(p.paidAt), fontWeight = FontWeight.SemiBold)
                    Text((p.method?.let { PayMethod.label(it) + " · " } ?: "") + "≈ ${Calc.dur(p.workedSeconds * 1000)}", color = sc.muted, fontSize = 12.5.sp, fontFamily = Mono)
                }
                Text(Calc.money(p.amount, st.currency), fontFamily = Mono, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (paying) {
            PaySheet(m, balance, rate, st.currency, onDismiss = { paying = false }) { amount, method ->
                paying = false
                vm.run("Cobro registrado: ${Calc.money(amount, st.currency)}") { Repo.pay(m.id, amount, rate, method) }
            }
        }
    }

    paymentOpen?.let { id ->
        val p = store.payment(id)
        if (p == null) paymentOpen = null else PaymentSheet(p, store, vm, onDismiss = { paymentOpen = null })
    }
}

@Composable
private fun PaySheet(m: Member, balance: Double, rate: Double, currency: String, onDismiss: () -> Unit, onPay: (Double, PayMethod?) -> Unit) {
    val sc = LocalStateColors.current
    var method by rememberSaveable { mutableStateOf<PayMethod?>(null) }
    val pending = balance.coerceAtLeast(0.0).roundToLong()
    var text by rememberSaveable { mutableStateOf(if (pending > 0) pending.toString() else "") }
    val amount = text.replace(".", "").replace(",", ".").toDoubleOrNull()?.takeIf { it > 0 }
    val rest = balance - (amount ?: 0.0)
    Sheet("Registrar cobro", onDismiss) {
        Text("Para ${m.name}. Saldo pendiente: ${Calc.money(balance, currency)}.", color = sc.muted, fontSize = 13.5.sp)
        OutlinedTextField(
            text, { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.fillMaxWidth(),
            label = { Text("Monto cobrado") }, singleLine = true, prefix = { Text("$ ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pending > 0) Chip("Todo el saldo", amount != null && amount.roundToLong() == pending) { text = pending.toString() }
            if (pending > 0) Chip("La mitad", amount != null && amount.roundToLong() == pending / 2) { text = (pending / 2).toString() }
            if (rate > 0) Chip("8 horas", amount != null && amount.roundToLong() == (8 * rate).roundToLong()) { text = (8 * rate).roundToLong().toString() }
        }
        Text(
            when {
                amount == null -> "Escribí el monto que se cobra."
                rest > 0.5 -> "Queda un saldo pendiente de ${Calc.money(rest, currency)}" + (if (rate > 0) " (≈ ${Calc.dur((rest / rate * Calc.HOUR_MS).toLong())})." else ".")
                rest < -0.5 -> "Es ${Calc.money(-rest, currency)} más que el saldo: queda cobrado por adelantado."
                else -> "Queda el saldo en cero."
            },
            color = if (rest < -0.5) sc.warn else sc.muted, fontSize = 13.5.sp,
        )
        FieldLabel("Medio de pago")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayMethod.entries.forEach { pm -> Chip(pm.label, method == pm) { method = if (method == pm) null else pm } }
        }
        BigButton(amount?.let { "Registrar cobro de ${Calc.money(it, currency)}" } ?: "Registrar cobro", enabled = amount != null) { onPay(amount!!, method) }
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
        KV("Importe", Calc.money(p.amount, st.currency))
        KV("Medio de pago", PayMethod.label(p.method))
        KV("Equivale a", "≈ ${Calc.dur(p.workedSeconds * 1000)}")
        OutlinedButton(
            onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Deshacer este cobro") }
        Text("El monto vuelve al saldo pendiente.", color = LocalStateColors.current.muted, fontSize = 12.5.sp)
    }
    if (confirm) {
        Confirm("Deshacer cobro", "Se borra el cobro de ${Calc.money(p.amount, st.currency)} del ${Dates.short(p.paidAt)} y el monto vuelve al saldo pendiente.", "Sí, deshacer", onConfirm = {
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
