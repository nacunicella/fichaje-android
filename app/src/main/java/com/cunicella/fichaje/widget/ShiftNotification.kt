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

    fun update(context: Context, s: WidgetState) {
        val nm = NotificationManagerCompat.from(context)
        if (!s.open || s.message != null) {
            nm.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val elapsed = s.workedMs + (System.currentTimeMillis() - s.atMs).coerceAtLeast(0)
        val money = s.sub.substringAfterLast("· ")
        val since = s.sub.substringBefore(" ·")
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setColor(if (s.paused) 0xFFF2A54E.toInt() else 0xFF4FCB98.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(context))
        if (s.paused) {
            b.setContentTitle("En pausa · ${Calc.dur(elapsed)}")
                .setContentText("$since · $money")
                .setShowWhen(false)
                .addAction(0, "Reanudar", action(context, ShiftAction.PauseToggle))
        } else {
            b.setContentTitle("En turno")
                .setContentText("$since · $money")
                .setWhen(System.currentTimeMillis() - elapsed)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .addAction(0, "Pausar", action(context, ShiftAction.PauseToggle))
        }
        b.addAction(0, "Terminar", action(context, ShiftAction.Stop))
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

    private fun action(context: Context, a: ShiftAction) = PendingIntent.getBroadcast(
        context, a.ordinal + 1,
        Intent(context, ShiftActionReceiver::class.java).setAction(a.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Recibe los botones de la notificación. */
class ShiftActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = ShiftAction.entries.firstOrNull { it.name == intent.action } ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { runShiftAction(app, a) } finally { pending.finish() }
        }
    }
}
