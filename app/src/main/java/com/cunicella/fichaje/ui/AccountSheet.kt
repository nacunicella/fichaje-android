package com.cunicella.fichaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.Member
import com.cunicella.fichaje.data.Settings
import com.cunicella.fichaje.update.Updates
import kotlinx.coroutines.launch

/** Círculo con las iniciales, para la barra de arriba. */
@Composable
fun Avatar(initials: String, size: Int = 34) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (size * 0.38f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    me: Member?,
    email: String?,
    settings: Settings?,
    onUpdateFound: (Updates.Available) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: suspend () -> String?,   // null = listo; si no, el error a mostrar
    onDismiss: () -> Unit,
    onEditProfile: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me?.initials ?: "?", size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(me?.name ?: "Mi cuenta", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(email ?: "", color = muted, fontSize = 14.sp)
                }
                if (me?.isAdmin == true) {
                    Text(
                        "Admin",
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (me != null && settings != null) {
                InfoRow("Valor de tu hora", Calc.money(Calc.currentRate(me, settings), settings.currency) + if (me.rate == null) " (general)" else "")
                InfoRow("En feriado", Calc.money(Calc.currentRate(me, settings) * settings.holidayMult, settings.currency))
                InfoRow("Empresa", settings.company)
            }
            InfoRow("Versión de la app", Updates.currentName)
            if (onEditProfile != null || onSettings != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEditProfile != null) OutlinedButton(onClick = onEditProfile, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) { Text("Mi perfil", maxLines = 1) }
                    if (onSettings != null) OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) { Text("Configuración", maxLines = 1) }
                }
            }
            updateMsg?.let { Text(it, color = muted, fontSize = 13.sp) }
            OutlinedButton(
                onClick = {
                    checking = true
                    updateMsg = null
                    scope.launch {
                        val a = Updates.check()
                        checking = false
                        if (a != null) onUpdateFound(a) else updateMsg = "Tenés la última versión."
                    }
                },
                enabled = !checking, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
            ) { Text(if (checking) "Buscando…" else "Buscar actualizaciones") }
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Cerrar sesión") }
            deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            TextButton(
                onClick = { confirmDelete = true },
                enabled = !deleting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = muted),
            ) { Text(if (deleting) "Cerrando la cuenta…" else "Cerrar mi cuenta") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Cerrar tu cuenta?") },
            text = { Text("Se borra tu usuario y no vas a poder entrar más con este email. Tus jornadas quedan guardadas para el equipo. Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleting = true
                        deleteError = null
                        scope.launch {
                            val err = onDeleteAccount()
                            deleting = false
                            if (err != null) deleteError = err
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Sí, cerrar mi cuenta") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("No") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
