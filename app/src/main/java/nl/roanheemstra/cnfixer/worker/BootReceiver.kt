package nl.roanheemstra.cnfixer.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.roanheemstra.cnfixer.assist.AssistantOverride
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * HyperOS resets several of the values this app sets on reboot,
 * so the recovery work is re-queued and the assistant override reapplied.
 * Without root Shizuku is not running yet at boot; the periodic worker
 * picks both up as soon as Shizuku is available again.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        if (!ShellRunner.hasPermission()) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-queue periodic work with the user's current preferences.
                Scheduler.syncFromPrefs(appContext)
                if (SafStore(appContext).assistantOverride.first()) {
                    AssistantOverride.apply()
                }
            } catch (_: Throwable) {
                // Best effort: the periodic worker retries.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
