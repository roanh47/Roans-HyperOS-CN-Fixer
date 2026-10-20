package nl.roanheemstra.cnfixer.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import nl.roanheemstra.cnfixer.MainActivity
import nl.roanheemstra.cnfixer.R
import nl.roanheemstra.cnfixer.mover.SafStore

/**
 * Two channels, each with its own switch in Settings.
 *
 * The switch gates posting here, so turning one off stops the notification
 * without touching the system settings for the other.
 */
object Notify {

    const val CHANNEL_SCREENSHOTS = "cnfixer-screenshots"
    const val CHANNEL_GMS = "cnfixer-gms"

    private const val ID_SCREENSHOTS = 5101
    private const val ID_GMS = 5102

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCREENSHOTS,
                "Screenshots",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Reports what the screenshot mover did" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GMS,
                "Google Play services",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Warns when the notification protection is broken" }
        )
    }

    suspend fun screenshots(context: Context, title: String, text: String) {
        if (!SafStore(context).notifyScreenshots.first()) return
        post(context, CHANNEL_SCREENSHOTS, ID_SCREENSHOTS, title, text)
    }

    suspend fun gms(context: Context, title: String, text: String) {
        if (!SafStore(context).notifyGms.first()) return
        post(context, CHANNEL_GMS, ID_GMS, title, text)
    }

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        ensureChannels(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
