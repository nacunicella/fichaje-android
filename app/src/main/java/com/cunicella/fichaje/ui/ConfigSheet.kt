package com.cunicella.fichaje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Settings

/** Empresa y tarifas. Solo el admin puede guardar. */
@Composable
internal fun ConfigSheet(settings: Settings, vm: DataViewModel, onDismiss: () -> Unit) {
    var company by rememberSaveable { mutableStateOf(settings.company) }
    var rate by rememberSaveable { mutableStateOf(trimNum(settings.rate)) }
    var mult by rememberSaveable { mutableStateOf(trimNum(settings.holidayMult)) }
    var goal by rememberSaveable { mutableStateOf(trimNum(settings.goalHours)) }
    var currency by rememberSaveable { mutableStateOf(settings.currency) }
    var error by remember { mutableStateOf<String?>(null) }
    val muted = LocalStateColors.current.muted

    Sheet("Configuración", onDismiss) {
        FieldLabel("Empresa")
        OutlinedTextField(company, { company = it }, Modifier.fillMaxWidth(), label = { Text("Nombre de la empresa") }, singleLine = true)
        FieldLabel("Tarifas")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                rate, { rate = it }, Modifier.weight(1f), label = { Text("Valor hora general") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("Para quien no tiene tarifa propia.", fontSize = 11.5.sp) },
            )
            OutlinedTextField(
                mult, { mult = it }, Modifier.weight(1f), label = { Text("Feriado ×") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("Multiplicador.", fontSize = 11.5.sp) },
            )
        }
        FieldLabel("Moneda")
        Segmented(listOf("ARS", "USD", "EUR"), currency, { it }) { currency = it }
        OutlinedTextField(
            goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text("Meta diaria (horas)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text("Cambiar la tarifa afecta solo a las jornadas nuevas: las cerradas conservan la tarifa con la que se trabajaron.", color = muted, fontSize = 12.5.sp)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        BigButton("Guardar cambios") {
            val r = rate.replace(",", ".").toDoubleOrNull()
            val mu = mult.replace(",", ".").toDoubleOrNull()
            val g = goal.replace(",", ".").toDoubleOrNull()
            when {
                r == null || r <= 0 -> error = "El valor hora general tiene que ser mayor a cero."
                mu == null || mu < 1 -> error = "El multiplicador de feriado tiene que ser 1 o más."
                g == null || g <= 0 || g > 24 -> error = "La meta diaria tiene que estar entre 1 y 24 horas."
                else -> {
                    vm.run("Configuración guardada") {
                        Repo.updateSettings(Settings(company.trim().ifBlank { "Fichaje" }, currency, r, mu, g))
                    }
                    onDismiss()
                }
            }
        }
    }
}
