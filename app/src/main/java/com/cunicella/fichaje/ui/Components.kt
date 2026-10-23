package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Member

/** Piezas que comparten las pantallas: pastillas, segmentos, tarjetas, listas, selector de persona. */

internal fun trimNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

@Composable
internal fun Pill(text: String, fg: Color, bg: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Etiqueta chica sin punto: Manual, Feriado, Cobrada. */
@Composable
internal fun Tag(text: String, fg: Color, bg: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

@Composable
internal fun Seg(text: String, selected: Boolean, modifier: Modifier, selBg: Color, selFg: Color, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp)).background(if (selected) selBg else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) selFg else LocalStateColors.current.muted, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Control segmentado simple con las opciones dadas. */
@Composable
internal fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
        options.forEach { o ->
            Seg(label(o), o == selected, Modifier.weight(1f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface) { onSelect(o) }
        }
    }
}

/** Fila de chips: la seleccionada va en color primario. */
@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
    )
}

/** Tarjeta grande de total: etiqueta, importe y una línea de detalle. */
@Composable
internal fun TotalCard(label: String, amount: String, meta: String, extra: String? = null) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = LocalStateColors.current.muted, fontSize = 13.sp)
            Text(amount, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp, maxLines = 1)
            Text(meta, color = LocalStateColors.current.muted, fontSize = 13.sp)
            extra?.let { Text(it, color = LocalStateColors.current.muted, fontSize = 12.5.sp) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, count: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, color = LocalStateColors.current.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** Encabezado de un día en una lista agrupada. */
@Composable
internal fun DayHeader(label: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = LocalStateColors.current.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text(right, color = LocalStateColors.current.muted, fontSize = 12.5.sp, fontFamily = Mono)
    }
}

/** Tarjeta que contiene filas separadas por una línea. */
@Composable
internal fun ListCard(count: Int, empty: String = "Nada por acá.", row: @Composable (Int) -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            if (count == 0) Text(empty, Modifier.padding(vertical = 14.dp), color = LocalStateColors.current.muted, fontSize = 13.5.sp)
            for (i in 0 until count) {
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                row(i)
            }
        }
    }
}

@Composable
internal fun EmptyCard(text: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(text, Modifier.fillMaxWidth().padding(18.dp), color = LocalStateColors.current.muted, fontSize = 13.5.sp)
    }
}

@Composable
internal fun MemberAvatar(m: Member?, on: Boolean, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text(m?.initials ?: "★", fontWeight = FontWeight.Bold, fontSize = (size * 0.36f).sp, color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) }
}

/** Marco común de las hojas que suben desde abajo: título y contenido con scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

/** Campo que abre una lista de personas (nuestro reemplazo del select nativo). */
@Composable
internal fun PersonPicker(
    members: List<Member>,
    selected: String?,
    meId: String?,
    allLabel: String? = null,
    enabled: Boolean = true,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val cur = members.firstOrNull { it.id == selected }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enabled) Modifier.clickable { open = true } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(cur, on = cur != null, size = 30)
        Spacer(Modifier.width(10.dp))
        Text(
            cur?.name ?: allLabel ?: "Elegí una persona", Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (cur != null && cur.id == meId) Text("vos", color = LocalStateColors.current.muted, fontSize = 12.sp)
        if (enabled) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = LocalStateColors.current.muted)
    }
    if (open) {
        Sheet("Persona", onDismiss = { open = false }) {
            val opts: List<Member?> = (if (allLabel != null) listOf<Member?>(null) else emptyList()) + members
            ListCard(opts.size) { i ->
                val m = opts[i]
                val sel = m?.id == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(m?.id); open = false }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberAvatar(m, on = m != null, size = 34)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m?.name ?: allLabel ?: "", fontWeight = FontWeight.SemiBold)
                        val sub = when {
                            m == null -> null
                            m.id == meId -> "vos"
                            !m.active -> "inactivo"
                            else -> null
                        }
                        if (sub != null) Text(sub, color = LocalStateColors.current.muted, fontSize = 12.5.sp)
                    }
                    if (sel) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
internal fun Confirm(title: String, text: String, confirmLabel: String, danger: Boolean = true, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm, shape = RoundedCornerShape(10.dp),
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Botón principal ancho, una sola línea. */
@Composable
internal fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Etiqueta chica arriba de un campo. */
@Composable
internal fun FieldLabel(text: String) {
    Text(text, color = LocalStateColors.current.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
}

/** Hora actual que se renueva cada [periodMs], para que lo abierto siga sumando sin recargar. */
@Composable
internal fun rememberNow(periodMs: Long = 30_000): kotlinx.datetime.Instant {
    var now by remember { mutableStateOf(kotlinx.datetime.Clock.System.now()) }
    androidx.compose.runtime.LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now = kotlinx.datetime.Clock.System.now()
        }
    }
    return now
}

/** Línea de estado debajo del encabezado: error en rojo o aviso corto en gris. */
@Composable
internal fun StatusLine(error: String?, toast: String?) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    toast?.let { Text(it, color = LocalStateColors.current.muted, fontSize = 13.sp) }
}
