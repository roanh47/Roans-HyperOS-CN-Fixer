package nl.roanheemstra.cnfixer.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the user swipes the ongoing notification away. Android allows that
 * on some versions even for a foreground service, so the notification is put
 * back immediately instead of waiting for the next five minute tick.
 */
class RestoreStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            context.startService(
                Intent(context, FcmStatusService::class.java)
                    .setAction(FcmStatusService.ACTION_RESTORE)
            )
        }
    }
}
