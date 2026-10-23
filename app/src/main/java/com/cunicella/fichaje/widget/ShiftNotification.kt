package com.cunicella.fichaje.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cunicella.fichaje.R
import com.cunicella.fichaje.data.Calc
import com.cunicella.fichaje.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notificación fija mientras hay una jornada abierta: tiempo corriendo y botones
 * para pausar/reanudar y terminar. Sale del mismo estado que el widget.
 */
internal object ShiftNotification {
    private const val CHANNEL = "jornada"
    private const val ID = 1
    const val REPOST = "Repost"

    /** Último estado mostrado, para volver a ponerla si la deslizan. */
    @Volatile private var last: WidgetState? = null

    fun update(context: Context, s: WidgetState) {
        val nm = NotificationManagerCompat.from(context)
        if (!s.open || s.message != null) {
            last = null
            nm.cancel(ID)
            return
        }
        last = s
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val elapsed = s.workedMs + (System.currentTimeMillis() - s.atMs).coerceAtLeast(0)
        val money = s.sub.substringAfterLast("· ")
        val since = s.sub.substringBefore(" ·")
        // Diseño propio para que los botones se vean sin expandir la notificación.
        val views = RemoteViews(context.packageName, R.layout.notif_shift).apply {
            setTextViewText(R.id.title, if (s.paused) "En pausa" else "En turno")
            setTextViewText(R.id.sub, "$since · $money")
            if (s.paused) {
                setViewVisibility(R.id.chrono, View.GONE)
                setViewVisibility(R.id.fixed, View.VISIBLE)
                setTextViewText(R.id.fixed, Calc.dur(elapsed))
                setImageViewResource(R.id.btnPause, R.drawable.w_play)
                setInt(R.id.btnPause, "setBackgroundResource", R.drawable.bg_circle_green)
                setContentDescription(R.id.btnPause, "Reanudar")
            } else {
                setViewVisibility(R.id.chrono, View.VISIBLE)
                setViewVisibility(R.id.fixed, View.GONE)
                setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
                setImageViewResource(R.id.btnPause, R.drawable.w_pause)
                setInt(R.id.btnPause, "setBackgroundResource", R.drawable.bg_circle_amber)
                setContentDescription(R.id.btnPause, "Pausar")
            }
            setInt(R.id.btnPause, "setColorFilter", 0xFF0D1219.toInt())
            setInt(R.id.btnStop, "setColorFilter", 0xFFF07A7A.toInt())
            setOnClickPendingIntent(R.id.btnPause, action(context, ShiftAction.PauseToggle))
            setOnClickPendingIntent(R.id.btnStop, action(context, ShiftAction.Stop))
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(if (s.paused) 0xFFF2A54E.toInt() else 0xFF4FCB98.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(context))
            // Android 14+ deja deslizar las notificaciones fijas: si la sacan, vuelve.
            .setDeleteIntent(broadcast(context, REPOST, 9))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            // por si algún fabricante ignora la vista propia: título y acciones clásicas
            .setContentTitle(if (s.paused) "En pausa · ${Calc.dur(elapsed)}" else "En turno")
            .setContentText("$since · $money")
            .setShowWhen(false)
            .addAction(0, if (s.paused) "Reanudar" else "Pausar", action(context, ShiftAction.PauseToggle))
            .addAction(0, "Terminar", action(context, ShiftAction.Stop))
        runCatching { nm.notify(ID, b.build()) }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Jornada en curso", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Muestra el tiempo de la jornada abierta con botones para pausar y terminar"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun open(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, a: ShiftAction) = broadcast(context, a.name, a.ordinal + 1)

    private fun broadcast(context: Context, name: String, code: Int) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, ShiftActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** La deslizaron estando en turno: se vuelve a mostrar (con datos frescos si no hay estado en memoria). */
    suspend fun repost(context: Context) {
        val s = last
        if (s != null) update(context, s) else ClockWidget.refreshAll(context)
    }
}

/** Recibe los botones de la notificación. */
class ShiftActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.action
        val a = ShiftAction.entries.firstOrNull { it.name == name }
        if (a == null && name != ShiftNotification.REPOST) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (a != null) runShiftAction(app, a) else ShiftNotification.repost(app)
            } finally { pending.finish() }
        }
    }
}
