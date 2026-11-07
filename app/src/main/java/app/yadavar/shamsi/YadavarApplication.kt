package app.yadavar.shamsi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.data.AppDatabase
import app.yadavar.shamsi.data.ReminderRepository
import app.yadavar.shamsi.data.SettingsStore
import app.yadavar.shamsi.widget.RescheduleWorker
import app.yadavar.shamsi.widget.YadavarWidget
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Graph {
    lateinit var repo: ReminderRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var appContext: Context
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(app: Application) {
        if (::repo.isInitialized) return
        appContext = app
        repo = ReminderRepository(AppDatabase.open(app).dao())
        settings = SettingsStore(app)
        appScope.launch {
            AlarmScheduler.reschedule(app)
            YadavarWidget().updateAll(app)
        }
    }

    suspend fun refreshWidget() {
        YadavarWidget().updateAll(appContext)
    }

    suspend fun refresh() {
        AlarmScheduler.reschedule(appContext)
        YadavarWidget().updateAll(appContext)
    }
}

class YadavarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        Graph.init(this)
        val request = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "yadavar-reschedule",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

const val CHANNEL_ID = "reminders"

private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
}
