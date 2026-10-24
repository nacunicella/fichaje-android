package com.cunicella.fichaje.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.cunicella.fichaje.data.Invites
import com.cunicella.fichaje.data.Supa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Dates
import com.cunicella.fichaje.data.Member
import com.cunicella.fichaje.data.NewMember
import com.cunicella.fichaje.data.Period
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Store

/** Muestra el contenido cuando los datos cargaron; mientras, el spinner o el error con reintento. */
@Composable
internal fun WithStore(vm: DataViewModel, content: @Composable (DataUi, Store) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val store = ui.store
    if (store == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (ui.loading) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text("Reintentar") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        if (ui.offline) {
            Text(
                "Sin conexión: se muestra lo guardado en el teléfono. Para cambiar algo hay que esperar la señal.",
                Modifier.fillMaxWidth().background(LocalStateColors.current.warn.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 8.dp),
                color = LocalStateColors.current.warn, fontSize = 13.sp,
            )
        }
        Box(Modifier.weight(1f)) { content(ui, store) }
    }
}

@Composable
fun EquipoScreen(vm: DataViewModel) = WithStore(vm) { ui, store ->
    val sc = LocalStateColors.current
    val now = rememberNow()
    var period by rememberSaveable { mutableStateOf(Period.Month) }
    var editing by remember { mutableStateOf<String?>(null) } // id del integrante, "" = nuevo
    val periods = listOf(Period.Today, Period.Month, Period.All)
    val inP = store.shifts.filter { Dates.inPeriod(it.startAt, period) && store.member(it.memberId) != null }
    val onNow = store.active.count { store.openOf(it.id) != null }
    val cur = store.settings.currency

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(periods, period, { it.label }, Modifier.weight(1f)) { period = it }
            if (store.isAdmin) AddButton("Agregar integrante") { editing = "" }
        }
        StatusLine(ui.error, null)
        TotalCard(
            "Costo del equipo · ${period.label.lowercase()}",
            Calc.money(inP.sumOf { Calc.earned(it, store.member(it.memberId), store.settings, now) }, cur),
            "${Calc.dur(inP.sumOf { Calc.workMs(it, now) })} trabajadas · ${inP.size} jornada${if (inP.size == 1) "" else "s"} · $onNow fichado${if (onNow == 1) "" else "s"} ahora",
        )
        SectionTitle("Integrantes", store.active.size.toString())
        MemberList(store.active, store, inP, now) { editing = it }
        val inactive = store.members.filter { !it.active }
        if (inactive.isNotEmpty()) {
            Text("Inactivos", color = sc.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            MemberList(inactive, store, inP, now) { editing = it }
        }
        Spacer(Modifier.height(8.dp))
    }

    editing?.let { id ->
        MemberSheet(store.member(id), store, vm, onDismiss = { editing = null })
    }
}

@Composable
private fun MemberList(list: List<Member>, store: Store, inP: List<com.cunicella.fichaje.data.Shift>, now: kotlinx.datetime.Instant, onTap: (String) -> Unit) {
    val sc = LocalStateColors.current
    ListCard(list.size, empty = "Todavía no hay nadie.") { i ->
        val m = list[i]
        val open = store.openOf(m.id)
        val mine = inP.filter { it.memberId == m.id }
        val last = store.shifts.filter { it.memberId == m.id }.maxByOrNull { it.startAt }
        Row(Modifier.fillMaxWidth().clickable { onTap(m.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(m, on = open != null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.id == store.me?.id) Text("  vos", color = sc.muted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        open != null -> Text(if (Calc.isPaused(open)) "En pausa" else "En turno", color = sc.on, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        !m.active -> Text("Inactivo", color = sc.muted, fontSize = 12.5.sp)
                        else -> Text("Fuera", color = sc.muted, fontSize = 12.5.sp)
                    }
                    Text(
                        (last?.let { "Última: ${Dates.short(it.startAt)}" } ?: "Sin jornadas") +
                            " · ${Calc.money(Calc.currentRate(m, store.settings), store.settings.currency)}/h" + if (m.rate == null) " (general)" else "",
                        color = sc.muted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Calc.money(mine.sumOf { Calc.earned(it, m, store.settings, now) }, store.settings.currency), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(Calc.dur(mine.sumOf { Calc.workMs(it, now) }), fontFamily = Mono, color = sc.muted, fontSize = 12.sp)
            }
        }
    }
}

/** Alta y edición de un integrante. Solo el admin puede guardar; los demás lo ven de lectura. */
@Composable
internal fun MemberSheet(member: Member?, store: Store, vm: DataViewModel, onDismiss: () -> Unit) {
    val st = store.settings
    val canEdit = store.isAdmin
    val isMe = member != null && member.id == store.me?.id
    var first by rememberSaveable { mutableStateOf(member?.firstName ?: "") }
    var last by rememberSaveable { mutableStateOf(member?.lastName ?: "") }
    var email by rememberSaveable { mutableStateOf(member?.email ?: "") }
    var ownRate by rememberSaveable { mutableStateOf(member?.rate != null) }
    var rate by rememberSaveable { mutableStateOf(trimNum(member?.rate ?: st.rate)) }
    var active by rememberSaveable { mutableStateOf(member?.active ?: true) }
    var admin by rememberSaveable { mutableStateOf(member?.isAdmin ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shiftCount = member?.let { m -> store.shifts.count { it.memberId == m.id } } ?: 0
    val rateNum = rate.replace(",", ".").toDoubleOrNull()

    Sheet(member?.name ?: "Nuevo integrante", onDismiss) {
        if (!canEdit) Text("Solo el admin puede editar el equipo.", color = LocalStateColors.current.muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(first, { first = it }, Modifier.weight(1f), label = { Text("Nombre") }, singleLine = true, enabled = canEdit)
            OutlinedTextField(last, { last = it }, Modifier.weight(1f), label = { Text("Apellido") }, singleLine = true, enabled = canEdit)
        }
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = canEdit,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = { Text("Con este email entra a la app y queda vinculado a esta persona.", fontSize = 12.sp) },
        )
        SwitchRow("Valor hora propio", ownRate, canEdit) { ownRate = it; if (!it) rate = trimNum(st.rate) }
        OutlinedTextField(
            rate, { rate = it }, Modifier.fillMaxWidth(), label = { Text("Valor de la hora (${st.currency})") }, singleLine = true,
            enabled = canEdit && ownRate, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supportingText = {
                Text(
                    (if (ownRate) "" else "Usa el valor general. ") +
                        "En feriado paga ${Calc.money((rateNum ?: 0.0) * st.holidayMult, st.currency)}/h. Cambiar la tarifa afecta solo a las jornadas nuevas.",
                    fontSize = 12.sp,
                )
            },
        )
        SwitchRow("Activo en el equipo", active, canEdit && !isMe) { active = it }
        if (store.isAdmin) SwitchRow("Es admin", admin, canEdit && !isMe) { admin = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        if (canEdit) {
            BigButton("Guardar", enabled = !vm.ui.value.busy) {
                when {
                    first.isBlank() -> error = "Poné al menos el nombre."
                    ownRate && (rateNum == null || rateNum <= 0) -> error = "El valor de la hora tiene que ser mayor a cero."
                    else -> {
                        val nm = NewMember(
                            first.trim(), last.trim(), email.trim().lowercase().ifBlank { null },
                            if (ownRate) rateNum else null, active, if (admin) "admin" else "member",
                        )
                        vm.run("Guardado") { if (member == null) Repo.addMember(nm) else Repo.updateMember(member.id, nm) }
                        onDismiss()
                    }
                }
            }
            if (member != null && member.userId == null) InviteBlock(member, store, vm)
            if (member != null && !isMe) {
                OutlinedButton(
                    onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar del equipo") }
                Text(
                    (if (shiftCount > 0) "Tiene $shiftCount jornada${if (shiftCount == 1) "" else "s"} registrada${if (shiftCount == 1) "" else "s"}. " else "") +
                        "Va a la papelera: se puede restaurar durante 30 días desde Historial.",
                    color = LocalStateColors.current.muted, fontSize = 12.5.sp,
                )
            }
        }
    }
    if (confirmDelete && member != null) {
        Confirm("Eliminar a ${member.name}", "Se quita del equipo y va a la papelera. Se puede restaurar durante 30 días.", "Sí, eliminar", onConfirm = {
            confirmDelete = false
            vm.run("Integrante eliminado") { Repo.deleteMember(member.id) }
            onDismiss()
        }, onDismiss = { confirmDelete = false })
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.5.sp)
            sub?.let { Text(it, color = LocalStateColors.current.muted, fontSize = 12.sp) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Invitación por código para un integrante que todavía no entró con su cuenta. */
@Composable
private fun InviteBlock(member: com.cunicella.fichaje.data.Member, store: Store, vm: DataViewModel) {
    val sc = LocalStateColors.current
    val context = LocalContext.current
    var code by remember(member.id) { mutableStateOf(member.inviteCode) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun share(c: String) {
        val text = Invites.shareText(store.settings.company, member.shortName, c)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Invitar a ${member.shortName}"))
    }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Todavía no entró con su cuenta", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            val c = code
            if (c != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c, fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 3.sp)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { share(c) }, shape = RoundedCornerShape(10.dp)) { Text("Compartir") }
                }
                Text("Con este código se registra con su email y contraseña y queda vinculado.", color = sc.muted, fontSize = 12.sp)
            } else if (store.isAdmin) {
                Text("Generá un código y mandáselo: se registra solo, sin que cargues su email.", color = sc.muted, fontSize = 12.5.sp)
                OutlinedButton(
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            runCatching { Repo.newInvite(member.id) }
                                .onSuccess { code = it; vm.refresh(quiet = true) }
                                .onFailure { error = Supa.friendly(it) }
                            busy = false
                        }
                    },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp),
                ) { Text(if (busy) "Generando…" else "Generar código de invitación") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp) }
            }
        }
    }
}
