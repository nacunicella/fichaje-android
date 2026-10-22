package com.cunicella.fichaje.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cunicella.fichaje.data.Supa
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var msg by rememberSaveable { mutableStateOf<String?>(null) }
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    fun submit() {
        if (!email.contains("@") || password.length < 6) {
            msg = "Poné tu email y una contraseña de al menos 6 caracteres."
            return
        }
        busy = true
        msg = null
        info = null
        scope.launch {
            runCatching {
                if (creating) {
                    Supa.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    info = "Cuenta creada. Si te llega un mail de confirmación, abrilo y después entrá."
                    creating = false
                } else {
                    Supa.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                }
            }.onFailure {
                msg = if (creating) "No se pudo crear la cuenta. ¿Ya tenés una con ese email?"
                else "Email o contraseña incorrectos."
            }
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cs.surfaceVariant,
        unfocusedContainerColor = cs.surfaceVariant,
        unfocusedBorderColor = Color.Transparent,
        focusedBorderColor = cs.primary,
        focusedLabelColor = cs.primary,
        unfocusedLabelColor = cs.onSurfaceVariant,
        cursorColor = cs.primary,
    )

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ClockMark()
            Spacer(Modifier.height(4.dp))
            Text(if (creating) "Crear cuenta" else "Fichaje", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (creating) "Usá el email con el que te cargaron en el equipo."
                else "Entrá para fichar tus horas.",
                color = cs.onSurfaceVariant, fontSize = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPass = !showPass }) {
                        Text(if (showPass) "Ocultar" else "Ver", fontSize = 13.sp)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            msg?.let { Text(it, color = cs.error, fontSize = 14.sp) }
            info?.let { Text(it, color = LocalStateColors.current.on, fontSize = 14.sp) }
            Button(
                onClick = { submit() }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = cs.onPrimary, strokeWidth = 2.dp)
                else Text(if (creating) "Crear cuenta" else "Entrar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (creating) "¿Ya tenés cuenta?" else "¿Es tu primera vez?", color = cs.onSurfaceVariant, fontSize = 14.sp)
                TextButton(onClick = { creating = !creating; msg = null; info = null }) {
                    Text(if (creating) "Entrar" else "Crear cuenta", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Marca de la app: un reloj simple sobre un cuadrado azul, igual al ícono. */
@Composable
private fun ClockMark() {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            val w = size.minDimension
            val stroke = w * 0.11f
            val c = Offset(w / 2, w * 0.55f)
            val r = w * 0.38f
            drawCircle(accent, radius = r, center = c, style = Stroke(stroke))
            drawLine(accent, c, Offset(c.x, c.y - r * 0.6f), stroke, StrokeCap.Round)
            drawLine(accent, c, Offset(c.x + r * 0.45f, c.y + r * 0.3f), stroke, StrokeCap.Round)
            drawLine(accent, Offset(w * 0.38f, w * 0.06f), Offset(w * 0.62f, w * 0.06f), stroke, StrokeCap.Round)
        }
    }
}
