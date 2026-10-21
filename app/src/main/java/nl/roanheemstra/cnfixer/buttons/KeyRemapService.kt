package nl.roanheemstra.cnfixer.buttons

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.roanheemstra.cnfixer.MainActivity
import nl.roanheemstra.cnfixer.R
import nl.roanheemstra.cnfixer.mover.SafStore

/**
 * Holds the key grabber open and executes the configured actions.
 *
 * The notification is the safety line: from here the takeover can be stopped
 * without opening the app, which releases the input device immediately.
 */
class KeyRemapService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val downAt = mutableMapOf<Int, Long>()

    @Volatile private var shortAction: RemapAction = RemapAction.Nothing
    @Volatile private var longAction: RemapAction = RemapAction.Nothing
    @Volatile private var aiAction: RemapAction = RemapAction.Nothing
    @Volatile private var aiCode: Int = -1

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(build("Starting..."))
        if (job == null) {
            job = scope.launch { run() }
        }
        return START_STICKY
    }

    private suspend fun run() {
        val store = SafStore(applicationContext)

        // Keep the actions current without restarting the helper.
        val configJob = scope.launch {
            combine(
                store.remapShort,
                store.remapLong,
                store.remapAi,
                store.remapAiCode,
                store.remapEnabled,
            ) { short, long, ai, code, enabled ->
                Triple(short, long, ai) to (code to enabled)
            }.collect { (actions, rest) ->
                shortAction = RemapAction.decode(actions.first)
                longAction = RemapAction.decode(actions.second)
                aiAction = RemapAction.decode(actions.third)
                aiCode = rest.first
                if (!rest.second) {
                    stopSelf()
                } else {
                    update(build(describe()))
                }
            }
        }

        if (!store.remapEnabled.first()) {
            configJob.cancel()
            stopSelf()
            return
        }

        val intercepted = buildSet {
            add(KEY_POWER_CODE)
            if (aiCode >= 0) add(aiCode)
        }

        val started = KeyGrabber.start(applicationContext, intercepted) { code, value, whenMs ->
            when (value) {
                1 -> downAt[code] = whenMs
                0 -> {
                    val start = downAt.remove(code) ?: whenMs
                    val action = when {
                        code == aiCode -> aiAction
                        KeyGrabber.isLongPress(start, whenMs) -> longAction
                        else -> shortAction
                    }
                    KeyGrabber.perform(applicationContext, action)
                }
            }
        }

        if (!started) {
            update(build("Could not take over the key. Check Shizuku access."))
        } else {
            update(build(describe()))
        }

        configJob.join()
    }

    private fun describe(): String =
        "short: ${shortAction.encode().let { RemapAction.describe(it) }}  |  " +
            "long: ${RemapAction.describe(longAction.encode())}"

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Key remap", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows which actions the power key runs"
                setShowBadge(false)
            }
        )
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, StopRemapReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Power key taken over")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(0, "Stop and give the key back", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
    }

    private fun update(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(ID, notification)
    }

    override fun onDestroy() {
        KeyGrabber.stop()
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "cnfixer-remap"
        private const val ID = 4401

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KeyRemapService::class.java),
            )
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeyRemapService::class.java)) }
        }
    }
}

/** The notification action that gives the key back. */
class StopRemapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = SafStore(context)
        kotlinx.coroutines.runBlocking {
            runCatching { store.setRemapEnabled(false) }
        }
        KeyRemapService.stop(context)
    }
}
