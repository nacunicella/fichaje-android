package com.cunicella.fichaje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cunicella.fichaje.update.Updates
import kotlinx.coroutines.launch

/** Popup de "Hay una versión nueva": baja el APK y abre el instalador de Android. */
@Composable
fun UpdateDialog(available: Updates.Available, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    val downloading = progress != null

    fun update() {
        if (!Updates.canInstall(context)) {
            needsPermission = true
            Updates.openInstallPermission(context)
            return
        }
        needsPermission = false
        error = null
        progress = 0f
        scope.launch {
            runCatching { Updates.download(context, available) { progress = it } }
                .onSuccess { Updates.install(context, it) }
                .onFailure { error = "No se pudo descargar. Revisá la conexión y probá de nuevo." }
            progress = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Hay una versión nueva") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Versión ${available.versionName}. Tenés la ${Updates.currentName}.")
                if (needsPermission) {
                    Text(
                        "Activá \"Permitir de esta fuente\" para Fichaje, volvé y tocá Actualizar otra vez. Se pide una sola vez.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        drawStopIndicator = {},
                    )
                    Text("Descargando… ${(it * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { update() }, enabled = !downloading, shape = RoundedCornerShape(10.dp)) { Text("Actualizar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("Ahora no") }
        },
    )
}
