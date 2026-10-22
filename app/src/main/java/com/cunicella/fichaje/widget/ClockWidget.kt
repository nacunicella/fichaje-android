package com.cunicella.fichaje.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.DayStats
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Snapshot
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.ui.MainActivity
import kotlinx.datetime.Clock

/** Lo que muestra el widget, calculado al momento de actualizarlo. */
private data class WidgetState(
    val status: String,
    val big: String,
    val sub: String,
    val open: Boolean = false,
    val paused: Boolean = false,
    val canAct: Boolean = false,
)

class ClockWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load()
        provideContent { Content(state) }
    }

    private suspend fun load(): WidgetState = runCatching {
        if (!Supa.awaitSession()) return WidgetState("Fichaje", "Entrá", "Abrí la app para iniciar sesión")
        val snap = Snapshot.load()
        if (snap.me == null) return WidgetState("Fichaje", "—", "Tu email no está en el equipo")
        val now = Clock.System.now()
        val open = snap.myOpen
        val stats = DayStats.of(snap.mineToday, snap.me, snap.settings, now)
        val money = Calc.money(stats.earned, snap.settings.currency)
        when {
            open == null -> WidgetState("Fuera de turno", Calc.dur(stats.worked), "Hoy · $money", canAct = true)
            Calc.isPaused(open) -> WidgetState("En pausa", Calc.dur(stats.worked), "Desde las ${Calc.hm(open.startAt)} · $money", open = true, paused = true, canAct = true)
            else -> WidgetState("En turno", Calc.dur(stats.worked), "Desde las ${Calc.hm(open.startAt)} · $money", open = true, canAct = true)
        }
    }.getOrElse { WidgetState("Fichaje", "Sin conexión", "Tocá para abrir la app") }

    @Composable
    private fun Content(s: WidgetState) {
        val bg = Color(0xFF151C26)
        val accent = when {
            s.paused -> Color(0xFFF2A54E)
            s.open -> Color(0xFF4FCB98)
            else -> Color(0xFF94A1B3)
        }
        Column(
            GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(s.status, style = TextStyle(color = ColorProvider(accent), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Text(s.big, style = TextStyle(color = ColorProvider(Color.White), fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Text(s.sub, style = TextStyle(color = ColorProvider(Color(0xFF94A1B3)), fontSize = 12.sp))
            if (s.canAct) {
                Spacer(GlanceModifier.height(10.dp))
                Row {
                    if (!s.open) {
                        Button("Iniciar", onClick = actionRunCallback<StartAction>())
                    } else {
                        Button(if (s.paused) "Reanudar" else "Pausar", onClick = actionRunCallback<PauseToggleAction>())
                        Spacer(GlanceModifier.width(8.dp))
                        Button("Terminar", onClick = actionRunCallback<StopAction>())
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            runCatching { ClockWidget().updateAll(context) }
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}

private suspend fun widgetAction(context: Context, block: suspend (Snapshot) -> Unit) {
    runCatching {
        if (Supa.awaitSession()) {
            val snap = Snapshot.load()
            if (snap.me != null) block(snap)
        }
    }
    ClockWidget.refreshAll(context)
}

class StartAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        widgetAction(context) { s -> if (s.myOpen == null) Repo.start(s.me!!.id, holiday = false) }
}

class PauseToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        widgetAction(context) { s ->
            val open = s.myOpen ?: return@widgetAction
            if (Calc.isPaused(open)) Repo.resume(open.id) else Repo.pause(open.id)
        }
}

class StopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        widgetAction(context) { s ->
            val open = s.myOpen ?: return@widgetAction
            Repo.stop(open, Calc.currentRate(s.me, s.settings))
        }
}
