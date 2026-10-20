package nl.roanheemstra.cnfixer.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import nl.roanheemstra.cnfixer.R
import androidx.core.content.ContextCompat

/**
 * Local notification test.
 *
 * This proves the notification pipeline on the device: channel, permission,
 * Do Not Disturb and presentation. It is deliberately not an FCM test, because
 * a real push needs a Firebase project with a registration token and a server
 * that is allowed to send.
 */
object NotificationTest {

    private const val CHANNEL_ID = "cnfixer-test"
    private const val NOTIFICATION_ID = 4201

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun send(context: Context): String {
        if (!hasPermission(context)) {
            return "Notification permission is not granted, so nothing was shown."
        }
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return "No notification service on this device."

        val channel = NotificationChannel(
            CHANNEL_ID,
            "CN Fixer tests",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Test notifications from CN Fixer"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("CN Fixer test")
            .setContentText("If you see this, notifications reach this app.")
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        return "Test notification sent."
    }
}
