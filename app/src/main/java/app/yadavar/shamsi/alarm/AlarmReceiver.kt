package app.yadavar.shamsi.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import app.yadavar.shamsi.CHANNEL_ID
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.R
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.formatTime
import app.yadavar.shamsi.model.rowMeta
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_SNOOZE -> Actions.snooze(id, intent.getIntExtra("minutes", 15))
                    AlarmScheduler.ACTION_DONE -> Actions.complete(id)
                    AlarmScheduler.ACTION_DISMISS -> Actions.dismiss(id)
                    else -> show(context.applicationContext, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun show(context: Context, id: String) {
        val reminder = Graph.repo.get(id) ?: return
        val open = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("id", id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = broadcast(context, id, AlarmScheduler.ACTION_SNOOZE, "snooze")
        val done = broadcast(context, id, AlarmScheduler.ACTION_DONE, "done")
        val time = if (reminder.hour != null && reminder.minute != null) formatTime(reminder.hour, reminder.minute) else ""
        val text = listOf(time, rowMeta(reminder, null, null)).filter { it.isNotBlank() }.joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(reminder.title.ifBlank { "یادآور" })
            .setContentText(text.ifBlank { "وقت یادآور است" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "انجام شد", done)
            .addAction(0, "۱۵ دقیقه", snooze)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        vibrate(context)
        runCatching {
            context.startActivity(
                Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("id", id)
                },
            )
        }
    }

    private fun broadcast(context: Context, id: String, action: String, salt: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra("id", id)
            if (action == AlarmScheduler.ACTION_SNOOZE) putExtra("minutes", 15)
        }
        return PendingIntent.getBroadcast(
            context,
            "$salt:$id".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                AlarmScheduler.reschedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
