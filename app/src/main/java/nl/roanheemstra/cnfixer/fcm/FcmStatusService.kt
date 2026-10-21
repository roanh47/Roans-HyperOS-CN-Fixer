package nl.roanheemstra.cnfixer.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
import nl.roanheemstra.cnfixer.MainActivity
import nl.roanheemstra.cnfixer.R
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShellRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent notification with the FCM state, so the status is visible without
 * opening the app.
 *
 * It is ongoing and comes from a foreground service, which is what makes it
 * non-dismissable. The check is read-only: it reads the values back and never
 * changes anything.
 */
class FcmStatusService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastText: String = "Checking Google Play services..."
    private var lastOk: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createStatusChannel()
    }

    private fun createStatusChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Live status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the Google Play services state without opening the app"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Comes back after a swipe: put the current state straight back.
        if (intent?.action == ACTION_RESTORE) {
            startInForeground(build(lastText, lastOk))
            return START_STICKY
        }
        startInForeground(build("Checking Google Play services...", false))
        if (job == null) {
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val enabled = runCatching { SafStore(this).statusNotification.first() }.getOrDefault(false)
            if (!enabled) {
                stopSelf()
                return
            }

            if (!ShellRunner.hasPermission()) {
                update(build("Shizuku access missing, cannot read the state", false))
                // Wake the device at the normal cadence, not every 30 seconds,
                // because nothing changes until the user starts Shizuku.
                delay(5 * 60_000L)
                continue
            }

            var checks = runCatching { FcmVerifier.run() }.getOrDefault(emptyList())
            var repaired = false

            // Do not report a state that can be fixed: repair first, then read again.
            if (FcmVerifier.isVerified(checks).not()) {
                runCatching { FcmEnforcer.runCorePass() }
                checks = runCatching { FcmVerifier.run() }.getOrDefault(checks)
                repaired = true
            }

            val ok = FcmVerifier.isVerified(checks)
            val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            val detail = when {
                ok && repaired -> "Repaired and verified at $stamp"
                ok -> "Protected. Last check $stamp"
                else -> {
                    val firstFailure = FcmVerifier.firstFailure(checks)
                    firstFailure?.let { "${it.label}: ${it.detail}" }
                        ?: "State unknown. Last check $stamp"
                }
            }
            lastText = detail
            lastOk = ok
            update(build(detail, ok))

            delay(5 * 60_000L)
        }
    }

    private fun build(text: String, ok: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val checkNow = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, CheckNowReceiver::class.java).setAction(CheckNowReceiver.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                when {
                    ok -> "Google Play services: protected"
                    text.startsWith("Shizuku") -> "CN Fixer: Shizuku is not connected"
                    else -> "Google Play services: check failed"
                }
            )
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Check now", checkNow)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this,
                    2,
                    Intent(this, RestoreStatusReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun update(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_STATUS = "cnfixer-status"
        const val ACTION_RESTORE = "nl.roanheemstra.cnfixer.RESTORE_STATUS"
        private const val NOTIFICATION_ID = 4301

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FcmStatusService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FcmStatusService::class.java)) }
        }
    }
}
