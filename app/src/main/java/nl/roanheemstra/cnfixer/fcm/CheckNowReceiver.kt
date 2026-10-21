package nl.roanheemstra.cnfixer.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The "Check now" action on the persistent notification. */
class CheckNowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-applying is what fixes a reset, and the service re-reads the
                // state right after, so the notification shows the result.
                if (nl.roanheemstra.cnfixer.shizuku.ShellRunner.hasPermission()) {
                    FcmEnforcer.runCorePass()
                }
                FcmStatusService.start(context)
            } catch (_: Throwable) {
                // Nothing to report here: the notification shows the state.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "nl.roanheemstra.cnfixer.CHECK_NOW"
    }
}
