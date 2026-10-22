package com.cunicella.fichaje.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cunicella.fichaje.R
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.data.DayStats
import com.cunicella.fichaje.data.Repo
import com.cunicella.fichaje.data.Snapshot
import com.cunicella.fichaje.data.Supa
import com.cunicella.fichaje.ui.MainActivity
import kotlinx.datetime.Clock

/**
 * Lo que muestra el widget. Se guarda en el estado del widget (no en la composición)
 * para que cada update muestre lo último: la app y los botones lo pisan con datos frescos.
 */
private data class WidgetState(
    val status: String,
    val sub: String,
    val workedMs: Long = 0,
    val atMs: Long = 0,
    val open: Boolean = false,
    val paused: Boolean = false,
    val canAct: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val running get() = open && !paused

    fun write(p: MutablePreferences) {
        p[K.status] = status; p[K.sub] = sub; p[K.worked] = workedMs; p[K.at] = atMs
        p[K.open] = open; p[K.paused] = paused; p[K.canAct] = canAct; p[K.busy] = busy
        if (message != null) p[K.message] = message else p.remove(K.message)
    }

    companion object {
        fun read(p: Preferences): WidgetState? = p[K.status]?.let {
            WidgetState(
                status = it, sub = p[K.sub] ?: "", workedMs = p[K.worked] ?: 0, atMs = p[K.at] ?: 0,
                open = p[K.open] ?: false, paused = p[K.paused] ?: false, canAct = p[K.canAct] ?: false,
                busy = p[K.busy] ?: false, message = p[K.message],
            )
        }

        fun of(snap: Snapshot): WidgetState {
            val me = snap.me ?: return WidgetState("Fichaje", "Tu email no está en el equipo", message = "—")
            val now = Clock.System.now()
            val open = snap.myOpen
            val stats = DayStats.of(snap.mineToday, me, snap.settings, now)
            val money = Calc.money(stats.earned, snap.settings.currency)
            val base = WidgetState("", "", workedMs = stats.worked, atMs = now.toEpochMilliseconds(), canAct = true)
            return when {
                open == null -> base.copy(status = "Fuera de turno", sub = "Hoy · $money")
                Calc.isPaused(open) -> base.copy(status = "En pausa", sub = "Desde las ${Calc.hm(open.startAt)} · $money", open = true, paused = true)
                else -> base.copy(status = "En turno", sub = "Desde las ${Calc.hm(open.startAt)} · $money", open = true)
            }
        }

        suspend fun load(): WidgetState = runCatching {
            if (!Supa.awaitSession()) WidgetState("Fichaje", "Abrí la app para iniciar sesión", message = "Entrá")
            else of(Snapshot.load())
        }.getOrElse { WidgetState("Fichaje", "Tocá para abrir la app", message = "Sin conexión") }
    }
}

private object K {
    val status = stringPreferencesKey("status")
    val sub = stringPreferencesKey("sub")
    val message = stringPreferencesKey("message")
    val worked = longPreferencesKey("worked")
    val at = longPreferencesKey("at")
    val open = booleanPreferencesKey("open")
    val paused = booleanPreferencesKey("paused")
    val canAct = booleanPreferencesKey("canAct")
    val busy = booleanPreferencesKey("busy")
}

private val Bg = Color(0xFF151C26)
private val Raised = Color(0xFF232C38)
private val Muted = Color(0xFF94A1B3)
private val Green = Color(0xFF4FCB98)
private val Amber = Color(0xFFF2A54E)
private val Red = Color(0xFFF07A7A)
private val Ink = Color(0xFF0D1219)

class ClockWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Android pide un update (al agregarlo o cada 30 min): traemos datos frescos.
        val fresh = WidgetState.load()
        updateAppWidgetState(context, id) { fresh.write(it) }
        provideContent {
            val s = WidgetState.read(currentState<Preferences>()) ?: fresh
            Content(context, s)
        }
    }

    @Composable
    private fun Content(context: Context, s: WidgetState) {
        val compact = LocalSize.current.height < 96.dp
        val accent = when {
            s.busy -> Muted
            s.paused -> Amber
            s.open -> Green
            else -> Muted
        }
        Row(
            GlanceModifier.fillMaxSize().background(Bg).cornerRadius(22.dp)
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(7.dp).background(accent).cornerRadius(4.dp)) {}
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        if (s.busy) "Guardando…" else s.status,
                        style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (compact) {
                        Text("  ${s.sub.substringAfterLast("· ")}", style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                Time(context, s, if (compact) 24f else 30f)
                if (!compact) {
                    Spacer(GlanceModifier.height(2.dp))
                    Text(s.sub, style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp), maxLines = 1)
                }
            }
            if (s.canAct && !s.busy) {
                Spacer(GlanceModifier.width(10.dp))
                val btn = if (compact) 44.dp else 52.dp
                if (!s.open) {
                    RoundButton(R.drawable.w_play, Green, Ink, btn, actionRunCallback<StartAction>())
                } else {
                    if (s.paused) RoundButton(R.drawable.w_play, Green, Ink, btn, actionRunCallback<PauseToggleAction>())
                    else RoundButton(R.drawable.w_pause, Amber, Ink, btn, actionRunCallback<PauseToggleAction>())
                    Spacer(GlanceModifier.width(8.dp))
                    RoundButton(R.drawable.w_stop, Raised, Red, btn, actionRunCallback<StopAction>())
                }
            }
        }
    }

    /** En turno corre en vivo (Chronometer del sistema); en pausa o fuera de turno queda fijo. */
    @Composable
    private fun Time(context: Context, s: WidgetState, sizeSp: Float) {
        if (s.message != null || !s.running) {
            Text(
                s.message ?: Calc.dur(s.workedMs),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            return
        }
        val elapsed = s.workedMs + (System.currentTimeMillis() - s.atMs).coerceAtLeast(0)
        val views = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
            setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
        AndroidRemoteViews(views)
    }

    @Composable
    private fun RoundButton(icon: Int, bg: Color, tint: Color, size: androidx.compose.ui.unit.Dp, onClick: Action) {
        Box(
            GlanceModifier.size(size).background(bg).cornerRadius(size / 2).clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(ColorProvider(tint)), modifier = GlanceModifier.size(22.dp))
        }
    }

    companion object {
        /** La app ya tiene el snapshot: lo mostramos sin volver a pedirlo. */
        suspend fun show(context: Context, snap: Snapshot) = push(context, WidgetState.of(snap))

        /** Recarga desde el servidor (p. ej. al cerrar sesión). */
        suspend fun refreshAll(context: Context) = push(context, WidgetState.load())

        private suspend fun push(context: Context, s: WidgetState) {
            runCatching {
                val ids = GlanceAppWidgetManager(context).getGlanceIds(ClockWidget::class.java)
                ids.forEach { id ->
                    updateAppWidgetState(context, id) { s.write(it) }
                    ClockWidget().update(context, id)
                }
            }
        }

        suspend fun markBusy(context: Context, id: GlanceId) {
            runCatching {
                updateAppWidgetState(context, id) { it[K.busy] = true }
                ClockWidget().update(context, id)
            }
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()
}

private suspend fun widgetAction(context: Context, id: GlanceId, block: suspend (Snapshot) -> Unit) {
    ClockWidget.markBusy(context, id)
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
        widgetAction(context, glanceId) { s -> if (s.myOpen == null) Repo.start(s.me!!.id, holiday = false) }
}

class PauseToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        widgetAction(context, glanceId) { s ->
            val open = s.myOpen ?: return@widgetAction
            if (Calc.isPaused(open)) Repo.resume(open.id) else Repo.pause(open.id)
        }
}

class StopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        widgetAction(context, glanceId) { s ->
            val open = s.myOpen ?: return@widgetAction
            Repo.stop(open, Calc.currentRate(s.me, s.settings))
        }
}
