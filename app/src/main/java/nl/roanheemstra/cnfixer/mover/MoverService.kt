package nl.roanheemstra.cnfixer.mover

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * Watches DCIM/Screenshots while the app is in the background.
 *
 * WorkManager cannot run faster than every 15 minutes, and a file observer on
 * that folder needs storage permission that the shell already has. So this
 * service polls the folder through Shizuku instead, which keeps the fix
 * permission free and still reacts within seconds.
 */
class MoverService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var store: SafStore

    override fun onCreate() {
        super.onCreate()
        store = SafStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground("Watching ${ScreenshotMover.DISPLAY_SOURCE}")
        if (job == null) {
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val enabled = store.moverAuto.first()
            if (!enabled) {
                stopSelf()
                return
            }

            if (!ShellRunner.hasPermission()) {
                notify("Waiting for Shizuku access")
                delay(5_000L)
                continue
            }

            val outcome = ScreenshotMover.run(
                moveSource = store.moveSource.first(),
                skipPending = store.skipPending.first(),
            )
            notify(
                if (outcome.moved > 0) {
                    "Moved ${outcome.moved} screenshot(s)"
                } else if (outcome.failed > 0) {
                    "${outcome.failed} failed, see the app"
                } else {
                    "No new screenshots"
                }
            )

            val interval = store.moverIntervalSeconds.first().coerceIn(10, 300)
            delay(interval * 1000L)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screenshot mover",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps DCIM/Screenshots empty while the mover is on"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startInForeground(text: String) {
        val notification = build(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(text))
    }

    private fun build(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sdcard)
            .setContentTitle("Screenshot mover")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "cnfixer-mover"
        private const val NOTIFICATION_ID = 4202

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MoverService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MoverService::class.java))
        }
    }
}
