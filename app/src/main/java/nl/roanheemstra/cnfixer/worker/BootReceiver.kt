package nl.roanheemstra.cnfixer.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.roanheemstra.cnfixer.mover.MoverService
import nl.roanheemstra.cnfixer.mover.SafStore
import nl.roanheemstra.cnfixer.shizuku.ShellRunner

/**
 * HyperOS resets several of the values this app sets on reboot, so the periodic
 * work is re-queued here with the user's current preferences.
 *
 * Without root Shizuku is not running yet at boot, so the worker itself does the
 * repair as soon as the service answers.
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
                Scheduler.syncFromPrefs(appContext)
                // Restart the watcher if the user had it on before the reboot.
                if (SafStore(appContext).moverAuto.first()) {
                    MoverService.start(appContext)
                }
            } catch (_: Throwable) {
                // Best effort: the periodic worker retries.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
